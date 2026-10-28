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

import java.time.LocalDateTime;

public class DataNode implements Receiver {

  private JChannel channel;
  private RpcDispatcher dispatcher;
  private String nodeName;
  private String accountDataPath;
  private String transfersDataPath;

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
        BankTransfer newTransfer = new BankTransfer(transferId, idSource, idDestiny, amount, LocalDateTime.now());
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

  public BankAccount buscarConta(String idConta) {
    synchronized (accountsMap) {
      return accountsMap.get(idConta);
    }
  }

  // --- Auditoria / Debug Exigido no Barema ---

  private void showAmount() {
    synchronized (accountsMap) {
      double total = accountsMap.values().stream()
          .mapToDouble(BankAccount::getBalance)
          .sum();
      System.out.printf("[%s] AUDITORIA - Montante total no banco: R$%.2f%n", nodeName, total);
    }
  }

  public void stop() {
    if (channel != null) {
      channel.close();
    }
  }

  public static void main(String[] args) throws Exception {
    // Passa o nome do nó e o caminho dos dois arquivos de persistência locais
    String name = args.length > 0 ? args[0] : "DataNode1";
    String accountsPath = args.length > 1 ? args[1] : "database/node1_accounts.json";
    String transfersPath = args.length > 2 ? args[2] : "database/node1_transfers.json";

    DataNode node = new DataNode(name, accountsPath, transfersPath);
    node.start();

    Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
    Thread.currentThread().join();
  }
}
