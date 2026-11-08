package br.com.bancoVirtualDistribuido.nodes;

import br.com.bancoVirtualDistribuido.model.*;
import br.com.bancoVirtualDistribuido.service.*;
import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.config.MyRpcOptions;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.checkerframework.checker.units.qual.t;
import org.jgroups.Address;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.util.Util;
import org.jgroups.util.RspList;
import org.jgroups.util.Rsp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;

// grpc
import br.com.bancoVirtualDistribuido.grpc.data.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class DataNode implements Receiver {

  private JChannel channel;
  private RpcDispatcher dispatcher;
  private String nodeName;
  private String accountDataPath;
  private String transfersDataPath;

  private Server grpcServer;

  private final Map<String, BankAccount> accountsMap = new HashMap<>();
  private final Map<String, BankTransfer> transfersMap = new HashMap<>();

  private BankAccountService accountService;
  private TransferService transferService;

  public DataNode(String nodeName, String accountDataPath, String transfersDataPath) {
    this.nodeName = nodeName;
    this.accountDataPath = accountDataPath;
    this.transfersDataPath = transfersDataPath;

    this.accountService = new BankAccountService(accountDataPath);
    this.transferService = new TransferService(transfersDataPath);
  }

  public void start() throws Exception {

    // Tenta ler localmente os dados
    BankAccount[] localAccounts = accountService.readAll();

    if (localAccounts != null) {
      for (BankAccount account : localAccounts) {
        accountsMap.put(account.getCpf(), account);
      }

      System.out.println("[" + nodeName
          + "] Dados contas carregados do arquivo local em disco.");
    }

    BankTransfer[] localTransfers = transferService.readAll();

    if (localTransfers != null) {
      for (BankTransfer transfers : localTransfers) {
        transfersMap.put(transfers.getUUId(), transfers);
      }

      System.out.println("[" + nodeName
          + "] Dados de transferencias carregados do arquivo local em disco.");
    }

    // JGroups
    channel = new JChannel("jgroups-config.xml");
    channel.setName(nodeName);

    // RPC
    dispatcher = new RpcDispatcher(channel, this);
    dispatcher.setReceiver(this);

    // Conecta no canal de dados
    channel.connect(Constants.dataChannelName);

    // Pede sincronização para o coordenador
    channel.getState(null, Constants.timeOut);

    System.out.println("[" + nodeName
        + "] Nó de dados pronto e conectado ao canal de dados.");

    // Inicia gRPC para comunicar com servidor de Controle
    grpcServer = ServerBuilder.forPort(Constants.DATA_NODE_PORT)
        .addService(new DataNodeGrpcServiceImpl())
        .build()
        .start();

    System.out.println("[" + nodeName
        + "] Servidor gRPC escutando o Controle na porta "
        + Constants.DATA_NODE_PORT);

    showAmount();
  }

  // --- Métodos de Sincronização de Estado do JGroups ---

  @Override
  public void getState(OutputStream output) throws Exception {
    synchronized (accountsMap) {
      synchronized (transfersMap) {

        CompleteDataNode state = new CompleteDataNode(
            accountsMap,
            transfersMap);

        Util.objectToStream(
            state,
            new DataOutputStream(output));
      }
    }
  }

  @Override
  public void setState(InputStream input) throws Exception {

    // Recebe o estado completo do coordenador
    CompleteDataNode synchronizedState = (CompleteDataNode) Util.objectFromStream(
        new DataInputStream(input));

    // Atualiza contas e transferências em memória
    synchronized (accountsMap) {
      synchronized (transfersMap) {

        accountsMap.clear();
        accountsMap.putAll(synchronizedState.getAccounts());

        transfersMap.clear();
        transfersMap.putAll(synchronizedState.getTransfers());
      }
    }

    // Atualiza arquivos persistentes em disco
    accountService.saveAll(accountsMap.values());
    transferService.saveAll(transfersMap.values());

    System.out.println(
        "[" + nodeName + "] Contas e transferências sincronizadas com sucesso.");

    showAmount();
  }

  @Override
  public void viewAccepted(View view) {
    System.out.println(
        "[" + nodeName + "] Nova visão do canal de Dados: " + view);
  }

  // ----------------------------- MÉTODOS INTERNOS
  // --------------------------------------

  public boolean verifyCredentials(String id, String password) {

    synchronized (accountsMap) {
      for (BankAccount account : accountsMap.values()) {

        boolean matchesId = id.equals(account.getCpf()) || id.equals(account.getUser());

        if (!account.getIsDeleted()
            && matchesId
            && password.equals(account.getPassword())) {
          return true;
        }
      }

      return false;
    }
  }

  public boolean createAccount(String user, String cpf, String password) {

    synchronized (accountsMap) {

      if (accountsMap.containsKey(cpf)) {
        return false;
      }

      // Cria conta no arquivo
      if (!accountService.create(user, cpf, password)) {
        return false;
      }

      // Recupera conta
      BankAccount account = accountService.readAccount(cpf);

      // Atualiza HashMap
      accountsMap.put(cpf, account);

      return true;
    }
  }

  public boolean modifyAccount(
      String newUser,
      String cpf,
      String newPassword) {

    synchronized (accountsMap) {

      BankAccount account = accountsMap.get(cpf);

      if (account == null) {
        return false;
      }

      account.setUser(newUser);
      account.setPassword(newPassword);

      try {
        accountService.saveAll(accountsMap.values());
        return true;

      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
    }
  }

  public boolean debitAndCredit(
      String idSource,
      String idDestiny,
      double amount) {

    // Mantém consistência durante escrita
    synchronized (accountsMap) {
      synchronized (transfersMap) {

        BankAccount source = accountsMap.get(idSource);
        BankAccount destiny = accountsMap.get(idDestiny);

        if (source == null
            || destiny == null
            || source.getBalance() < amount) {
          return false;
        }

        source.setBalance(source.getBalance() - amount);
        destiny.setBalance(destiny.getBalance() + amount);

        // Cria e armazena histórico da transferência
        String transferId = UUID.randomUUID().toString();

        BankTransfer newTransfer = new BankTransfer(
            transferId,
            idSource,
            idDestiny,
            amount,
            Instant.now());

        transfersMap.put(transferId, newTransfer);

        // Persiste modificações nos arquivos JSON
        try {
          accountService.saveAll(accountsMap.values());
          transferService.saveAll(transfersMap.values());

        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }

        System.out.printf(
            "[%s] Transferência efetuada: R$%.2f de %s para %s%n",
            nodeName,
            amount,
            idSource,
            idDestiny);

        showAmount();

        return true;
      }
    }
  }

  public BankAccount getAccount(String idConta) {

    synchronized (accountsMap) {
      return accountsMap.get(idConta);
    }
  }

  // REPLICAÇÃO DE INFORMAÇÃO

  public void replicateSaveAccount(String accountId, String owner, String password, double balance) {
    synchronized (accountsMap) {
      try {
        BankAccount account = accountsMap.get(accountId);
        if (account == null) {
          account = new BankAccount();
          account.setCpf(accountId);
          account.setPassword(password);
          account.setUser(owner);
        } else {
          account.setUser(owner);
          if (password != null && !password.isEmpty()) {
            account.setPassword(password);
          }
        }
        account.setBalance(balance);

        accountsMap.put(accountId, account);
        accountService.saveAll(accountsMap.values()); // Salva no JSON

        System.out.println("[" + nodeName + "] Conta " + accountId + " replicada e salva com sucesso.");
      } catch (Exception e) {
        System.err.println("[" + nodeName + "] Erro ao replicar conta: " + e.getMessage());
      }
    }
  }

  public void replicateSaveTransfer(String transferId, String sourceCpf, String destCpf, double amount) {
    synchronized (transfersMap) {
      try {
        BankTransfer newTransfer = new BankTransfer(
            transferId,
            sourceCpf,
            destCpf,
            amount,
            Instant.now());
        transfersMap.put(transferId, newTransfer);
        transferService.saveAll(transfersMap.values());

        System.out.println("[" + nodeName + "] Transferência " + transferId + " replicada e salva com sucesso.");
      } catch (Exception e) {
        System.err.println("[" + nodeName + "] Erro ao replicar transferência: " + e.getMessage());
      }
    }
  }

  // ROLLBACKS -> DEU RUIIIIMMMM
  public void replicateDeleteAccount(String accountId) {
    synchronized (accountsMap) {
      try {
        // verifica se conta existe no nó
        if (accountsMap.containsKey(accountId)) {

          // tira do hashmap (RAM)
          accountsMap.remove(accountId);

          // re escreve json com a deleção
          accountService.saveAll(accountsMap.values());

          System.out.println(
              "[" + nodeName + "] ROLLBACK EXECUTADO: Conta " + accountId + " excluída da memória e do disco.");
        } else {
          System.out.println("[" + nodeName + "] AVISO ROLLBACK: Ordem de apagar recebida, mas a conta " + accountId
              + " já não existia localmente.");
        }
      } catch (Exception e) {
        System.err.println(
            "[" + nodeName + "] ERRO CRÍTICO AO EXECUTAR ROLLBACK da conta " + accountId + ": " + e.getMessage());
      }
    }
  }

  public void replicateDeleteTransfer(String transferId, String sourceCpf, String destCpf, double amount) {
    synchronized (transfersMap) {
      try {
        // verifica se conta existe no nó
        if (transfersMap.containsKey(transferId)) {

          // tira do hashmap (RAM)
          transfersMap.remove(transferId);

          // re escreve json com a deleção
          transferService.saveAll(transfersMap.values());

          // volta valor para as contas
          BankAccount source = accountsMap.get(sourceCpf);
          BankAccount dest = accountsMap.get(destCpf);

          if (source != null) {
            source.setBalance(source.getBalance() + amount);
          }
          if (dest != null) {
            dest.setBalance(dest.getBalance() - amount);
          }

          // salva valor nas contas
          accountService.saveAll(accountsMap.values());

          System.out.println(
              "[" + nodeName + "] ROLLBACK EXECUTADO: transferencia " + transferId
                  + " excluída da memória e do disco.");
        } else {
          System.out
              .println("[" + nodeName + "] AVISO ROLLBACK: Ordem de apagar recebida, mas a transferencia " + transferId
                  + " já não existia localmente.");
        }
      } catch (Exception e) {
        System.err.println(
            "[" + nodeName + "] ERRO CRÍTICO AO EXECUTAR ROLLBACK da transferencia " + transferId + ": "
                + e.getMessage());
      }
    }
  }

  // --- Auditoria / Debug ---

  private void showAmount() {

    synchronized (accountsMap) {

      double total = accountsMap.values().stream()
          .mapToDouble(BankAccount::getBalance)
          .sum();

      System.out.printf(
          "[%s] AUDITORIA - Montante total no banco: R$%.2f%n",
          nodeName,
          total);
    }
  }

  public void stop() {

    if (grpcServer != null) {
      grpcServer.shutdown();
    }

    if (channel != null) {
      channel.close();
    }
  }

  // ------------------ TRADUTOR gRPC DATA ------------------------------

  private class DataNodeGrpcServiceImpl
      extends BankDataServiceGrpc.BankDataServiceImplBase {

    @Override
    public void verifyCredentialsData(
        VerifyCredentialsDataRequest request,
        StreamObserver<VerifyCredentialsDataResponse> responseObserver) {

      VerifyCredentialsDataResponse response = VerifyCredentialsDataResponse.newBuilder()
          .setSuccess(
              DataNode.this.verifyCredentials(
                  request.getId(),
                  request.getPassword()))
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void readAccountData(
        ReadAccountDataRequest request,
        StreamObserver<ReadAccountDataResponse> responseObserver) {

      String accountId = request.getCpf();

      BankAccount account;

      synchronized (accountsMap) {
        account = accountsMap.get(accountId);
      }

      ReadAccountDataResponse.Builder responseBuilder = ReadAccountDataResponse.newBuilder();

      if (account != null) {

        responseBuilder
            .setFound(true)
            .setCpf(request.getCpf())
            .setOwner(account.getUser())
            .setBalanceCents(
                (long) (account.getBalance() * 100));

      } else {
        responseBuilder.setFound(false);
      }

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void saveAccountData(
        SaveAccountDataRequest request,
        StreamObserver<SaveAccountDataResponse> responseObserver) {

      String accountId = request.getCpf();
      String owner = request.getOwner();
      String password = request.getPassword();
      double balance = request.getBalanceCents() / 100.0;

      boolean isSuccess = false;

      RequestOptions syncOptions = MyRpcOptions.getMajority(channel).setTimeout(Constants.timeOut);

      try {
        // tenta com que os nós executem a replicação de conta
        MethodCall call = new MethodCall(
            "replicateSaveAccount",
            new Object[] { accountId, owner, password, balance },
            new Class[] { String.class, String.class, String.class, double.class });

        // pega as respostas dos nós
        RspList<Object> answers = dispatcher.callRemoteMethods(null, call, syncOptions);

        int countSuccess = 0;

        List<Address> successNodes = new ArrayList<>();
        List<Address> errorNodes = new ArrayList<>();

        // verifica resposta nó a nó
        for (Map.Entry<Address, Rsp<Object>> entry : answers.entrySet()) {
          Address nodeAddress = entry.getKey();
          Rsp<Object> nodeResponse = entry.getValue();

          // se resposta não foi erro, conta sucesso e adiciona no na lista de successo
          if (nodeResponse.wasReceived() && !nodeResponse.hasException()) {
            countSuccess++;
            successNodes.add(nodeAddress);
            // se resposta não recebida ou erro adiciona no na lista de erros
          } else {
            errorNodes.add(nodeAddress);
          }
        }

        // verifica se a maioria deu erro
        int totalNodes = channel.getView().getMembers().size();
        int majority = (totalNodes / 2) + 1;
        int tries = 1;

        // maioria deu errado
        if (countSuccess < majority) {
          System.out.println("Não foi possivel replicar conta na maioria");
          RequestOptions retryOptions = MyRpcOptions.getAll().setTimeout(Constants.timeOutRetries);
          // espera todos pois precisa pegar todos que falharam, se pegar maioria, sempre
          // pega metade+1, depois metade+1 da metade...
          // tenta ficar reenviando até ou dar certo ou estourar o limite de tentativa
          while (tries <= Constants.maxRetriesSaveAccount && !errorNodes.isEmpty()) {
            System.out.println("Tentando replicar conta na maioria {tentativa: " + tries + "}");
            // reenvia pra quem deu erro
            answers = dispatcher.callRemoteMethods(errorNodes, call, retryOptions);

            List<Address> stillFailing = new ArrayList<>();
            // verifica resposta nó a nó
            for (Map.Entry<Address, Rsp<Object>> entry : answers.entrySet()) {
              Address nodeAddress = entry.getKey();
              Rsp<Object> nodeResponse = entry.getValue();

              // se resposta não foi erro, conta sucesso e adiciona no na lista de successo
              if (nodeResponse.wasReceived() && !nodeResponse.hasException()) {
                countSuccess++;
                successNodes.add(nodeAddress);
              } else {
                stillFailing.add(nodeAddress);
              }
            }

            errorNodes = stillFailing;

            if (countSuccess >= majority) {
              System.out.println("Contas replicadas com sucesso");
              isSuccess = true;
              break;
            } else {
              tries++;
            }
          }
        }
        // maioria deu certo
        else {
          System.out.println("Contas replicadas com sucesso");
          isSuccess = true;
        }

        // se no final não conseguir um sucesso, tenta fazer rollBack
        if (!isSuccess) {
          System.out.println("Contas não replicadas após " + tries + "tentativas, fazendo rollBack...");
          // função do rollBack
          if (!successNodes.isEmpty()) {
            MethodCall rollbackCall = new MethodCall(
                "replicateDeleteAccount",
                new Object[] { accountId },
                new Class[] { String.class });

            // Joga na galera que conseguiu pra eles deletarem e espera um pouquin pra ver
            // se responderam
            RequestOptions rollbackOptions = MyRpcOptions.getAll().timeout(Constants.timeOutRollBack)
                .setTimeout(Constants.timeOutRollBack);
            dispatcher.callRemoteMethods(successNodes, rollbackCall, rollbackOptions);

            System.out.println("[DataNode] Rollback enviado para " + successNodes.size() + " nós.");
          }
        }

      } catch (Exception e) {
        e.printStackTrace();
        isSuccess = false;
      }

      SaveAccountDataResponse response = SaveAccountDataResponse.newBuilder()
          .setSuccess(isSuccess)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void editAccountData(
        EditAccountDataRequest request,
        StreamObserver<EditAccountDataResponse> responseObserver) {

      String accountId = request.getCpf();
      String newUser = request.getOwner();
      String newPassword = request.getPassword();

      boolean isSuccess = false;

      // salva estado antigo
      BankAccount oldAccount;

      // busca os dados antigos na memória
      synchronized (accountsMap) {
        oldAccount = accountsMap.get(accountId);
      }

      // se a conta nem existe, não tem o que editar, já retorna erro
      if (oldAccount == null) {
        EditAccountDataResponse response = EditAccountDataResponse.newBuilder()
            .setSuccess(false)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        return;
      }

      // guarda os dados originais antes de tentar mudar
      String oldUser = oldAccount.getUser();
      String oldPassword = oldAccount.getPassword();
      double currentBalance = oldAccount.getBalance(); // saldo não muda na edição

      RequestOptions syncOptions = MyRpcOptions.getMajority(channel).setTimeout(Constants.timeOut);

      try {
        // tenta com que os nós executem a replicação da conta com os NOVOS dados
        MethodCall call = new MethodCall(
            "replicateSaveAccount",
            new Object[] { accountId, newUser, newPassword, currentBalance },
            new Class[] { String.class, String.class, String.class, double.class });

        // pega as respostas dos nós na primeira tentativa (pra todo mundo)
        RspList<Object> answers = dispatcher.callRemoteMethods(null, call, syncOptions);

        int countSuccess = 0;
        List<Address> successNodes = new ArrayList<>();
        List<Address> errorNodes = new ArrayList<>();

        // verifica resposta nó a nó
        for (Map.Entry<Address, Rsp<Object>> entry : answers.entrySet()) {
          Address nodeAddress = entry.getKey();
          Rsp<Object> nodeResponse = entry.getValue();

          // se resposta não foi erro, conta sucesso e adiciona nó na lista de sucesso
          if (nodeResponse.wasReceived() && !nodeResponse.hasException()) {
            countSuccess++;
            successNodes.add(nodeAddress);
            // se resposta não recebida ou erro, adiciona nó na lista de erros
          } else {
            errorNodes.add(nodeAddress);
          }
        }

        // verifica se a maioria deu erro
        int totalNodes = channel.getView().getMembers().size();
        int majority = (totalNodes / 2) + 1;
        int tries = 1;

        // maioria deu errado na primeira chamada
        if (countSuccess < majority) {
          System.out.println("Não foi possivel editar na maioria, tentando novamente");

          RequestOptions retryOptions = MyRpcOptions.getAll().setTimeout(Constants.timeOutRetries);
          // espera todos pois precisa pegar todos que falharam, tentando a maioria de
          // quem sobrou

          // tenta ficar reenviando até dar certo ou estourar o limite de tentativas
          while (tries <= Constants.maxRetriesSaveAccount && !errorNodes.isEmpty()) {
            System.out.println(
                "Tentando salvar edição na maioria {" + tries + " / " + Constants.maxRetriesSaveAccount + "}");

            // reenvia a mudança SÓ pra quem deu erro na rodada anterior
            answers = dispatcher.callRemoteMethods(errorNodes, call, retryOptions);

            // lista auxiliar para quem continuou falhando
            List<Address> stillFailing = new ArrayList<>();

            // verifica resposta nó a nó das retentativas
            for (Map.Entry<Address, Rsp<Object>> entry : answers.entrySet()) {
              Address nodeAddress = entry.getKey();
              Rsp<Object> nodeResponse = entry.getValue();

              // se conseguiu agora, contabiliza
              if (nodeResponse.wasReceived() && !nodeResponse.hasException()) {
                countSuccess++;
                successNodes.add(nodeAddress);
              } else {
                // se erro manteve, bota na lista pra tentar na proxima rodada
                stillFailing.add(nodeAddress);
              }
            }

            // atualiza a lista principal de erros pra proxima rodada
            errorNodes = stillFailing;

            // se JÁ atingiu a maioria, interrompe
            if (countSuccess >= majority) {
              System.out.println("Editado na maioria");
              isSuccess = true;
              break;
            } else {
              tries++;
            }
          }
        }
        // maioria deu certo de primeira
        else {
          System.out.println("Editado na maioria");
          isSuccess = true;
        }

        // se no final das tentativas não conseguir um sucesso da maioria
        if (!isSuccess) {
          System.err.println("[" + nodeName + "] Maioria não atingida na edição. Iniciando ROLLBACK...");

          // se algum nó chegou a salvar os dados errados, manda ele desfazer
          if (!successNodes.isEmpty()) {

            // tenta com que os nós voltem a salvar os DADOS ANTIGOS
            MethodCall rollbackCall = new MethodCall(
                "replicateSaveAccount",
                new Object[] { accountId, oldUser, oldPassword, currentBalance },
                new Class[] { String.class, String.class, String.class, double.class });

            RequestOptions rollbackOptions = MyRpcOptions.getAll().setTimeout(Constants.timeOutRollBack);

            // envia a ordem de voltar no tempo APENAS para os nós que haviam comitado
            RspList<Object> rollbackAnswers = dispatcher.callRemoteMethods(successNodes, rollbackCall, rollbackOptions);

            System.out.println("[DataNode] Rollback enviado para " + successNodes.size() + " nós.");

          }
        }

      } catch (Exception e) {
        e.printStackTrace();
        isSuccess = false; // falhou na chamada geral
      }

      EditAccountDataResponse response = EditAccountDataResponse.newBuilder()
          .setSuccess(isSuccess)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void saveTransferData(
        SaveTransferDataRequest request,
        StreamObserver<SaveTransferDataResponse> responseObserver) {

      String transferId = request.getTransferId();
      double amount = request.getAmountCents() / 100.0;
      String source = request.getSourceCpf();
      String dest = request.getDestinationCpf();

      boolean isSuccess = false;

      RequestOptions syncOptions = MyRpcOptions.getMajority(channel).setTimeout(Constants.timeOut);

      try {
        // tenta com que os nós executem a replicação da transferência
        MethodCall call = new MethodCall(
            "replicateSaveTransfer",
            new Object[] { transferId, source, dest, amount },
            new Class[] { String.class, String.class, String.class, double.class });

        // pega as respostas dos nós
        RspList<Object> answers = dispatcher.callRemoteMethods(null, call, syncOptions);

        int countSuccess = 0;

        List<Address> successNodes = new ArrayList<>();
        List<Address> errorNodes = new ArrayList<>();

        // verifica resposta nó a nó
        for (Map.Entry<Address, Rsp<Object>> entry : answers.entrySet()) {
          Address nodeAddress = entry.getKey();
          Rsp<Object> nodeResponse = entry.getValue();

          // se resposta não foi erro, conta sucesso e adiciona no na lista de successo
          if (nodeResponse.wasReceived() && !nodeResponse.hasException()) {
            countSuccess++;
            successNodes.add(nodeAddress);
          } else {
            errorNodes.add(nodeAddress);
          }
        }

        int totalNodes = channel.getView().getMembers().size();
        int majority = (totalNodes / 2) + 1;
        int tries = 1;

        // maioria deu errado
        if (countSuccess < majority) {
          System.out.println("Não foi possivel replicar transferência na maioria");
          RequestOptions retryOptions = MyRpcOptions.getAll().setTimeout(Constants.timeOutRetries);

          while (tries <= Constants.maxRetriesSaveAccount && !errorNodes.isEmpty()) {
            System.out.println("Tentando replicar transferência na maioria {tentativa: " + tries + "}");

            // reenvia pra quem deu erro
            answers = dispatcher.callRemoteMethods(errorNodes, call, retryOptions);

            List<Address> stillFailing = new ArrayList<>();

            // verifica resposta nó a nó
            for (Map.Entry<Address, Rsp<Object>> entry : answers.entrySet()) {
              Address nodeAddress = entry.getKey();
              Rsp<Object> nodeResponse = entry.getValue();

              if (nodeResponse.wasReceived() && !nodeResponse.hasException()) {
                countSuccess++;
                successNodes.add(nodeAddress);
              } else {
                stillFailing.add(nodeAddress);
              }
            }

            errorNodes = stillFailing;

            if (countSuccess >= majority) {
              System.out.println("Transferência replicada com sucesso após retentativas.");
              isSuccess = true;
              break;
            } else {
              tries++;
            }
          }
        }
        // maioria deu certo de primeira
        else {
          System.out.println("Transferências replicadas com sucesso na primeira tentativa.");
          isSuccess = true;
        }

        // se no final não conseguir um sucesso, faz rollBack compensatório
        if (!isSuccess) {
          System.out
              .println("Transferência não replicada após " + tries + " tentativas, fazendo rollBack de saldos...");

          if (!successNodes.isEmpty()) {
            MethodCall rollbackCall = new MethodCall(
                "replicateRollbackTransfer",
                new Object[] { transferId, source, dest, amount },
                new Class[] { String.class, String.class, String.class, double.class });

            RequestOptions rollbackOptions = MyRpcOptions.getAll().setTimeout(Constants.timeOutRollBack);
            dispatcher.callRemoteMethods(successNodes, rollbackCall, rollbackOptions);

            System.out.println("[DataNode] Rollback de transferência enviado para " + successNodes.size() + " nós.");
          }
        }

      } catch (Exception e) {
        e.printStackTrace();
        isSuccess = false;
      }

      SaveTransferDataResponse response = SaveTransferDataResponse.newBuilder()
          .setSuccess(isSuccess)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }

  public static void main(String[] args) throws Exception {

    // Passa nome do nó e caminhos dos arquivos de persistência
    String name = args.length > 0
        ? args[0]
        : "DataNode{" + UUID.randomUUID().toString() + "}";

    String accountsPath = args.length > 1
        ? args[1]
        : Constants.accountsDatabaseFolder + "accounts-" + name + ".json";

    String transfersPath = args.length > 2
        ? args[2]
        : Constants.transfersDatabaseFolder + "transfers-" + name + ".json";

    DataNode node = new DataNode(
        name,
        accountsPath,
        transfersPath);

    node.start();

    Runtime.getRuntime().addShutdownHook(
        new Thread(node::stop));

    Thread.currentThread().join();
  }
}
