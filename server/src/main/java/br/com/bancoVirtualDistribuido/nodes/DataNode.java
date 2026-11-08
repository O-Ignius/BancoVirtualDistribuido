package br.com.bancoVirtualDistribuido.nodes;

import br.com.bancoVirtualDistribuido.model.*;
import br.com.bancoVirtualDistribuido.service.*;
import br.com.bancoVirtualDistribuido.config.Constants;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import java.time.Instant;

//grpc
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
      System.out.println("[" + nodeName + "] Dados contas carregados do arquivo local em disco.");
    }

    BankTransfer[] localTransfers = transferService.readAll();
    if (localTransfers != null) {
      for (BankTransfer transfers : localTransfers) {
        transfersMap.put(transfers.getUUId(), transfers);
      }
      System.out.println("[" + nodeName + "] Dados de transferencias carregados do arquivo local em disco.");
    }

    // jgroups
    channel = new JChannel("jgroups-config.xml");
    channel.setName(nodeName);

    // rpc
    dispatcher = new RpcDispatcher(channel, this);
    dispatcher.setReceiver(this);

    // conecta no canal de dados
    channel.connect(Constants.dataChannelName);

    // pede sincronização pro coordenador
    channel.getState(null, Constants.timeOut);

    System.out.println("[" + nodeName + "] Nó de dados pronto e conectado ao canal de dados.");

    // inicia grpc para comunicar com server controle
    grpcServer = ServerBuilder.forPort(Constants.DATA_NODE_PORT)
        .addService(new DataNodeGrpcServiceImpl()) // Registra a classe tradutora (ver passo 3)
        .build()
        .start();
    System.out.println("[" + nodeName + "] Servidor gRPC escutando o Controle na porta " + Constants.DATA_NODE_PORT);

    showAmount();
  }

  // --- Métodos de Sincronização de Estado do JGroups (Obrigatórios) ---

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
    CompleteDataNode sincronizedState = (CompleteDataNode) Util.objectFromStream(
        new DataInputStream(input));

    // Atualiza contas e transferências em memória
    synchronized (accountsMap) {
      synchronized (transfersMap) {

        accountsMap.clear();
        accountsMap.putAll(sincronizedState.getAccounts());

        transfersMap.clear();
        transfersMap.putAll(sincronizedState.getTransfers());
      }
    }

    // Atualiza os arquivos persistentes em disco
    accountService.saveAll(accountsMap.values());
    transferService.saveAll(transfersMap.values());

    System.out.println(
        "[" + nodeName + "] Contas e transferências sincronizadas com sucesso.");

    showAmount();
  }

  @Override
  public void viewAccepted(View view) {
    System.out.println("[" + nodeName + "] Nova visão do canal de Dados: " + view);
  }

  // ----------------------------- RPC --------------------------------------

  public boolean verifyCredentials(String id, String password) {
    synchronized (accountsMap) {
      for (BankAccount account : accountsMap.values()) {
        boolean matchesId = id.equals(account.getCpf()) || id.equals(account.getUser());
        if (!account.getIsDeleted() && matchesId && password.equals(account.getPassword())) {
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

      // cria conta no arquivo
      if (!accountService.create(user, cpf, password)) {
        return false;
      }

      // pega conta
      BankAccount account = accountService.readAccount(cpf);
      // Atualiza o HashMap
      accountsMap.put(cpf, account);

      return true;
    }
  }

  public boolean modifyAccount(String newUser, String cpf, String newPassword) {
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

  public boolean debitAndCredit(String idSource, String idDestiny, double amount) {
    // mantem consistencia durante escrita
    synchronized (accountsMap) {
      synchronized (transfersMap) {
        BankAccount source = accountsMap.get(idSource);
        BankAccount destiny = accountsMap.get(idDestiny);

        if (source == null || destiny == null || source.getBalance() < amount) {
          return false;
        }

        source.setBalance(source.getBalance() - amount);
        destiny.setBalance(destiny.getBalance() + amount);

        // 2. Cria e armazena o histórico da transferência
        String transferId = java.util.UUID.randomUUID().toString();
        BankTransfer newTransfer = new BankTransfer(transferId, idSource, idDestiny, amount, Instant.now());
        transfersMap.put(transferId, newTransfer);

        // 3. Persiste imediatamente as modificações nos dois arquivos JSON do nó
        try {
          accountService.saveAll(accountsMap.values());
          transferService.saveAll(transfersMap.values());
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }

        System.out.printf("[%s] Transferência efetuada: R$%.2f de %s para %s%n",
            nodeName, amount, idSource, idDestiny);

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

  // --- Auditoria / Debug ---

  private void showAmount() {
    synchronized (accountsMap) {
      double total = accountsMap.values().stream()
          .mapToDouble(BankAccount::getBalance)
          .sum();
      System.out.printf("[%s] AUDITORIA - Montante total no banco: R$%.2f%n", nodeName, total);
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

  // ------------------Tradutores ------------------------------

  private class DataNodeGrpcServiceImpl extends BankDataServiceGrpc.BankDataServiceImplBase {

    @Override
    public void verifyCredentials(VerifyCredentialsRequest request,
        StreamObserver<VerifyCredentialsResponse> responseObserver) {
      VerifyCredentialsResponse response = VerifyCredentialsResponse.newBuilder()
          .setSuccess(DataNode.this.verifyCredentials(request.getId(), request.getPassword()))
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void readAccount(ReadAccountRequest request, StreamObserver<ReadAccountResponse> responseObserver) {
      // converte int64 pra string
      String accountId = String.valueOf(request.getCpf());

      BankAccount account;
      synchronized (accountsMap) {
        account = accountsMap.get(accountId);
      }

      ReadAccountDataResponse.Builder responseBuilder = ReadAccountDataResponse.newBuilder();

      if (account != null) {
        responseBuilder.setFound(true)
            .setCpf(request.getCpf())
            .setOwner(account.getUser())
            // converte pra centavos para melhor acuracia
            .setBalanceCents((long) (account.getBalance() * 100));
      } else {
        responseBuilder.setFound(false);
      }

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void saveAccount(SaveAccountRequest request, StreamObserver<SaveAccountResponse> responseObserver) {
      String accountId = String.valueOf(request.getCpf());
      String owner = String.valueOf(request.getOwner());
      String password = String.valueOf(request.getPassword());
      double balance = request.getBalanceCents() / 100.0;

      boolean isSuccess = true;
      synchronized (accountsMap) {
        try {
          BankAccount account = accountsMap.get(accountId);

          if (account == null) {
            // Se não existir, cria e define o saldo
            account = new BankAccount();
            account.setCpf(accountId);
            account.setPassword(password);
            account.setUser(owner);
            account.setBalance(balance);
          } else {
            // Se já existir, não altera o saldo
            account.setUser(owner);
            if (!password.isEmpty()) {
              account.setPassword(password);
            }
          }

          accountsMap.put(accountId, account);

          // salva localmente
          accountService.saveAll(accountsMap.values());
        } catch (Exception e) {
          e.printStackTrace();
          isSuccess = false;
        }
      }

      SaveAccountDataResponse response = SaveAccountDataResponse.newBuilder()
          .setSuccess(isSuccess)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /*
    @Override
    public void readTransferData(ReadTransferDataRequest request,
        StreamObserver<ReadTransferDataResponse> responseObserver) {
      String transferId = request.getTransferId();

      BankTransfer transfer;
      synchronized (transfersMap) {
        transfer = transfersMap.get(transferId);
      }

      ReadTransferDataResponse.Builder responseBuilder = ReadTransferDataResponse.newBuilder();

      if (transfer != null) {
        responseBuilder.setFound(true)
            .setTransferId(transfer.getUUId())
            .setSourceAccountId(Long.parseLong(transfer.getSource()))
            .setDestinationAccountId(Long.parseLong(transfer.getDestiny()))
            .setAmountCents((long) (transfer.getAmount() * 100))
            .setStatus("COMPLETED"); // Or whatever status is in your class
      } else {
        responseBuilder.setFound(false);
      }

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }
    */

    @Override
    public void saveTransferData(SaveTransferDataRequest request,
        StreamObserver<SaveTransferDataResponse> responseObserver) {
      String transferId = request.getTransferId();
      double amount = request.getAmountCents() / 100.0;

      boolean isSuccess = true;
      synchronized (transfersMap) {
        try {
          BankTransfer newTransfer = new BankTransfer(
              transferId,
              String.valueOf(request.getSourceCpf()),
              String.valueOf(request.getDestinationCpf()),
              amount,
              Instant.now());

          transfersMap.put(transferId, newTransfer);
          transferService.saveAll(transfersMap.values());
        } catch (Exception e) {
          e.printStackTrace();
          isSuccess = false;
        }
      }

      SaveTransferDataResponse response = SaveTransferDataResponse.newBuilder()
          .setSuccess(isSuccess)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }

  public static void main(String[] args) throws Exception {
    // Passa o nome do nó e o caminho dos dois arquivos de persistência locais
    String name = args.length > 0 ? args[0] : "DataNode{" + UUID.randomUUID().toString() + "}";
    String accountsPath = args.length > 1 ? args[1] : Constants.accountsDatabaseFolder + "accounts-" + name + ".json";
    String transfersPath = args.length > 2 ? args[2]
        : Constants.transfersDatabaseFolder + "transfers-" + name + ".json";

    DataNode node = new DataNode(name, accountsPath, transfersPath);
    node.start();

    Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
    Thread.currentThread().join();
  }
}
