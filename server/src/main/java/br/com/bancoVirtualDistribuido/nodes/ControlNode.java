package br.com.bancoVirtualDistribuido.nodes;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.Address;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.util.Util;
import org.jgroups.util.RspList;
import org.jgroups.util.Rsp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.config.MyRpcOptions;
import br.com.bancoVirtualDistribuido.config.BroadcastConnector;
import br.com.bancoVirtualDistribuido.grpc.data.*;
import br.com.bancoVirtualDistribuido.grpc.service.*;

public class ControlNode implements Receiver {

  private JChannel channel;
  private RpcDispatcher dispatcher;
  private LockService lockService;
  private String nodeName;
  private int grpcPort;

  private DataNodeClient dataClient;
  private io.grpc.Server grpcServer;

  // Cliente online
  private final Set<String> loggedClients = new HashSet<>();

  private final AtomicInteger currentLoad = new AtomicInteger(0);

  private final BroadcastConnector broadcaster = new BroadcastConnector();

  public ControlNode(String nodeName) {
    this.nodeName = nodeName;
  }

  public void start(int nodePort) throws Exception {
    this.grpcPort = nodePort;
    channel = new JChannel("jgroups-config.xml");
    channel.setName(nodeName);

    dispatcher = new RpcDispatcher(channel, this);
    dispatcher.setReceiver(this);

    lockService = new LockService(channel);

    channel.connect(Constants.controlChannelName);

    // Pega estado do coordenador
    channel.getState(null, Constants.timeOut);

    // Conecta ao servidor de dados
    dataClient = new DataNodeClient();

    System.out.println("[" + nodeName + "] Conectado ao canal de Controle.");
  }

  @Override
  public void getState(OutputStream output) throws Exception {
    synchronized (loggedClients) {
      Util.objectToStream(loggedClients, new DataOutputStream(output));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setState(InputStream input) throws Exception {
    Set<String> sincronizedState = (Set<String>) Util.objectFromStream(new DataInputStream(input));

    synchronized (loggedClients) {
      loggedClients.clear();
      loggedClients.addAll(sincronizedState);
    }

    System.out.println("[" + nodeName + "] Estado de clientes logados sincronizado.");
  }

  private synchronized void startGrpcServer() {
    if (grpcServer == null) {
      try {
        grpcServer = io.grpc.ServerBuilder.forPort(grpcPort)
            .addService(new ControlNodeGrpcServiceImpl())
            .build()
            .start();

        // após build do servidor grpc, começa a anúnciá-lo
        broadcaster.start("CONTROL", grpcPort);

        System.out.println("[" + nodeName + "] COORDENADOR DE CONTROLE: Escutando Gateway na porta " + grpcPort);
      } catch (Exception e) {
        System.err.println("[" + nodeName + "] Erro ao iniciar servidor gRPC: " + e.getMessage());
      }
    }
  }

  @Override
  public void viewAccepted(View view) {
    System.out.println("[" + nodeName + "] Nova visão do canal de Controle: " + view);

    if (!view.getMembers().isEmpty() && channel.getAddress().equals(view.getMembers().get(0))) {
      startGrpcServer();
    }
  }

  // carga do no
  public int getNodeLoad() {
    return currentLoad.get();
  }

  // seleciona nó mais livre
  private Address getLeastLoadedNode() {
    Address bestNode = channel.getAddress();
    int minLoad = currentLoad.get();

    try {
      // se só 1 nó, retorna ele
      if (channel.getView() == null || channel.getView().size() <= 1) {
        return bestNode;
      }

      MethodCall loadCall = new MethodCall("getNodeLoad", new Object[] {}, new Class[] {});
      RspList<Integer> responses = dispatcher.callRemoteMethods(
          null,
          loadCall,
          MyRpcOptions.getAll().setTimeout(Constants.timeOutWaitClusters));

      // após pegar as respostas, verifica qual nó tem menos carga
      for (Map.Entry<Address, Rsp<Integer>> entry : responses.entrySet()) {
        Rsp<Integer> rsp = entry.getValue();
        if (rsp.wasReceived() && !rsp.hasException() && rsp.getValue() != null) {
          if (rsp.getValue() < minLoad) {
            minLoad = rsp.getValue();
            bestNode = entry.getKey();
          }
        }
      }
    } catch (Exception e) {
      System.err.println("[" + nodeName + "] Erro ao consultar carga dos nós, usando nó local: " + e.getMessage());
    }

    return bestNode;
  }

  public void replicateAddLoggedClient(String idCliente) {
    synchronized (loggedClients) {
      loggedClients.add(idCliente);
    }
  }

  // tratamento de dados recebidos
  public boolean verifyValidityCpf(String cpf) {
    if (cpf.matches("\\d{11}")) {
      return true;
    }
    return false;
  }

  public boolean verifyValidityAmount(double amount) {
    return Double.isFinite(amount) && amount > 0.0;
  }

  public boolean verifyValidityPassword(String password) {
    return password != null && !password.isBlank();
  }

  // ---------- RPC ------------

  public boolean loginService(String idCliente, String password) {
    currentLoad.incrementAndGet();
    // verificadores
    if (!verifyValidityCpf(idCliente) || !verifyValidityPassword(password)) {
      currentLoad.decrementAndGet();
      return false;
    }

    try {
      if (!dataClient.verifyCredentialsData(idCliente, password)) {
        return false;
      }

      try {
        MethodCall call = new MethodCall("replicateAddLoggedClient", new Object[] { idCliente },
            new Class[] { String.class });
        dispatcher.callRemoteMethods(null, call, MyRpcOptions.getAll().setTimeout(Constants.timeOut));
      } catch (Exception e) {
        replicateAddLoggedClient(idCliente);
      }

      return true;

    } finally {
      currentLoad.decrementAndGet();
    }
  }

  public boolean createAccount(String cpf, String user, String password) {
    currentLoad.incrementAndGet();

    // verificadores
    if (!verifyValidityCpf(cpf) || !verifyValidityPassword(password)) {
      currentLoad.decrementAndGet();
      return false;
    }

    try {
      return dataClient.saveAccountData(cpf, user, password, Constants.inititalBalance);
    } finally {
      currentLoad.decrementAndGet();
    }
  }

  public boolean processTransfer(String idSource, String idDestiny, double amount) {
    currentLoad.incrementAndGet();

    // verificadores
    if (!verifyValidityCpf(idSource) || !verifyValidityCpf(idDestiny) || !verifyValidityAmount(amount)) {
      currentLoad.decrementAndGet();
      return false;
    }

    String lock1 = idSource.compareTo(idDestiny) < 0 ? idSource : idDestiny;
    String lock2 = idSource.compareTo(idDestiny) < 0 ? idDestiny : idSource;

    Lock l1 = lockService.getLock(lock1);
    Lock l2 = lockService.getLock(lock2);

    l1.lock();
    l2.lock();

    try {
      System.out.printf("[%s] Locks adquiridos. Processando R$%.2f de %s para %s%n",
          nodeName, amount, idSource, idDestiny);

      // Comunicação com o DataNode
      boolean success = dataClient.executeDebitAndCreditData(
          idSource, idDestiny, amount);

      if (success) {
        // Salva transferência no DataNode
        String transferId = UUID.randomUUID().toString();

        dataClient.saveTransferData(
            transferId, idSource, idDestiny, amount);
      }

      return success;

    } catch (Exception e) {
      e.printStackTrace();
      return false;

    } finally {
      l2.unlock();
      l1.unlock();
      currentLoad.decrementAndGet();
    }
  }

  public long fetchTotalBankMoney() {
    currentLoad.incrementAndGet();
    try {
      return dataClient.readTotalBankMoneyData();
    } finally {
      currentLoad.decrementAndGet();
    }
  }

  public ReadAccountsByNameDataResponse fetchAccountsByNameFromData(String name) {
    currentLoad.incrementAndGet();
    try {
      return dataClient.readAccountsByNameData(name);
    } finally {
      currentLoad.decrementAndGet();
    }
  }

  public ReadAccountDataResponse fetchAccountFromData(String cpf) {
    currentLoad.incrementAndGet();

    // verificadores
    if (!verifyValidityCpf(cpf)) {
      currentLoad.decrementAndGet();
      return ReadAccountDataResponse.newBuilder().setFound(false).build();
    }
    try {
      return dataClient.readAccountData(cpf);
    } finally {
      currentLoad.decrementAndGet();
    }
  }

  public GetTransferDataResponse fetchTransfersFromData(String cpf) {
    currentLoad.incrementAndGet();

    // verificadores
    if (!verifyValidityCpf(cpf)) {
      currentLoad.decrementAndGet();
      return GetTransferDataResponse.newBuilder().build();
    }
    try {
      return dataClient.readTransferData(cpf);
    } finally {
      currentLoad.decrementAndGet();
    }
  }

  public void stop() {
    // para anuncio do multicast de gRPC
    broadcaster.stop();
    if (grpcServer != null) {
      grpcServer.shutdown();
    }

    // Fecha conexão com servidor de dados
    if (dataClient != null) {
      dataClient.shutdown();
    }

    if (channel != null) {
      channel.close();
    }
  }

  // ---------- Tradutor gRPC ----------

  private class ControlNodeGrpcServiceImpl
      extends BankServiceGrpc.BankServiceImplBase {

    @Override
    public void loginService(LoginServiceRequest request,
        io.grpc.stub.StreamObserver<LoginServiceResponse> responseObserver) {

      Address target = getLeastLoadedNode();
      boolean success = false;

      try {
        if (target.equals(channel.getAddress())) {
          success = ControlNode.this.loginService(request.getId(), request.getPassword());
        } else {
          System.out.println("[" + nodeName + "] Delegando login para: " + target);
          MethodCall call = new MethodCall(
              "loginService",
              new Object[] { request.getId(), request.getPassword() },
              new Class[] { String.class, String.class });
          Boolean res = dispatcher.callRemoteMethod(target, call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));
          success = (res != null && res);
        }
      } catch (Exception e) {
        success = ControlNode.this.loginService(request.getId(), request.getPassword());
      }

      LoginServiceResponse response = LoginServiceResponse.newBuilder()
          .setSuccess(success)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void createAccountService(CreateAccountServiceRequest request,
        io.grpc.stub.StreamObserver<CreateAccountServiceResponse> responseObserver) {

      boolean success = false;
      Address target = getLeastLoadedNode();

      try {
        if (target.equals(channel.getAddress())) {
          success = ControlNode.this.createAccount(request.getCpf(), request.getUser(), request.getPassword());
        } else {
          System.out.println("[" + nodeName + "] Delegando criação de conta para: " + target);
          MethodCall call = new MethodCall(
              "createAccount",
              new Object[] { request.getCpf(), request.getUser(), request.getPassword() },
              new Class[] { String.class, String.class, String.class });
          Boolean res = dispatcher.callRemoteMethod(target, call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));
          success = (res != null && res);
        }
      } catch (Exception e) {
        success = ControlNode.this.createAccount(request.getCpf(), request.getUser(), request.getPassword());
      }

      CreateAccountServiceResponse response = CreateAccountServiceResponse.newBuilder()
          .setSuccess(success)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void saveTransferService(SaveTransferServiceRequest request,
        io.grpc.stub.StreamObserver<SaveTransferServiceResponse> responseObserver) {

      String source = request.getSourceCpf();
      String destiny = request.getDestinationCpf();
      double amount = request.getAmountCents() / 100.0;

      boolean success = false;
      Address target = getLeastLoadedNode();

      try {
        if (target.equals(channel.getAddress())) {
          success = ControlNode.this.processTransfer(source, destiny, amount);
        } else {
          System.out.println("[" + nodeName + "] Delegando transferência para: " + target);
          MethodCall call = new MethodCall(
              "processTransfer",
              new Object[] { source, destiny, amount },
              new Class[] { String.class, String.class, double.class });
          Boolean res = dispatcher.callRemoteMethod(target, call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));
          success = (res != null && res);
        }
      } catch (Exception e) {
        success = ControlNode.this.processTransfer(source, destiny, amount);
      }

      SaveTransferServiceResponse response = SaveTransferServiceResponse.newBuilder()
          .setSuccess(success)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    // ---------- Métodos de leitura ----------

    @Override
    public void getAccountService(GetAccountServiceRequest request,
        io.grpc.stub.StreamObserver<GetAccountServiceResponse> responseObserver) {

      try {
        // 1. Pega cpf
        String cpf = request.getCpf();
        Address target = getLeastLoadedNode();
        ReadAccountDataResponse dataResponse;

        if (target.equals(channel.getAddress())) {
          dataResponse = ControlNode.this.fetchAccountFromData(cpf);
        } else {
          System.out.println("[" + nodeName + "] Delegando consulta de conta para: " + target);
          MethodCall call = new MethodCall(
              "fetchAccountFromData",
              new Object[] { cpf },
              new Class[] { String.class });
          dataResponse = dispatcher.callRemoteMethod(target, call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));
        }

        // Monta a resposta
        GetAccountServiceResponse response = GetAccountServiceResponse.newBuilder()
            .setCpf(dataResponse.getCpf())
            .setOwner(dataResponse.getOwner())
            .setBalanceCents(dataResponse.getBalanceCents())
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

      } catch (Exception e) {
        System.err.println("[ControlNode] Erro ao buscar conta no DataNode: " + e.getMessage());

        GetAccountServiceResponse errorResponse = GetAccountServiceResponse.newBuilder()
            .build();

        responseObserver.onNext(errorResponse);
        responseObserver.onCompleted();
      }
    }

    @Override
    public void getTransferService(GetTransferServiceRequest request,
        io.grpc.stub.StreamObserver<GetTransferServiceResponse> responseObserver) {

      try {
        String cpf = request.getSource();
        Address target = getLeastLoadedNode();
        GetTransferDataResponse dataResponse;

        if (target.equals(channel.getAddress())) {
          dataResponse = ControlNode.this.fetchTransfersFromData(cpf);
        } else {
          System.out.println("[" + nodeName + "] Delegando extrato para: " + target);
          MethodCall call = new MethodCall(
              "fetchTransfersFromData",
              new Object[] { cpf },
              new Class[] { String.class });
          dataResponse = dispatcher.callRemoteMethod(target, call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));
        }

        GetTransferServiceResponse.Builder responseBuilder = GetTransferServiceResponse.newBuilder()
            .setFound(true);

        // tradutor pois no rpc temos o transfer usado em data e usado em service
        for (br.com.bancoVirtualDistribuido.grpc.data.Transfer dataTransfer : dataResponse.getTransferList()) {

          br.com.bancoVirtualDistribuido.grpc.service.Transfer serviceTransfer = br.com.bancoVirtualDistribuido.grpc.service.Transfer
              .newBuilder()
              .setTransferId(dataTransfer.getTransferId())
              .setSource(dataTransfer.getSource())
              .setDestiny(dataTransfer.getDestiny())
              .setAmountCents(dataTransfer.getAmountCents())
              .setCreatedAt(dataTransfer.getCreatedAt())
              .build();

          // Adiciona na lista da resposta usando o método singular gerado pelo gRPC
          responseBuilder.addTransfer(serviceTransfer);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();

      } catch (Exception e) {
        System.err.println("[ControlNode] Erro ao buscar transferências no DataNode: " + e.getMessage());

        GetTransferServiceResponse errorResponse = GetTransferServiceResponse.newBuilder()
            .setFound(false)
            .build();

        responseObserver.onNext(errorResponse);
        responseObserver.onCompleted();
      }
    }

    @Override
    public void getTotalBankMoneyService(
        com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<GetTotalBankMoneyServiceResponse> responseObserver) {

      try {
        Address target = getLeastLoadedNode();
        long totalMoney;

        if (target.equals(channel.getAddress())) {
          totalMoney = ControlNode.this.fetchTotalBankMoney();
        } else {
          System.out.println("[" + nodeName + "] Delegando leitura do montante total para: " + target);
          MethodCall call = new MethodCall(
              "fetchTotalBankMoney",
              new Object[] {},
              new Class[] {});

          Long res = dispatcher.callRemoteMethod(
              target,
              call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));

          totalMoney = (res != null) ? res : 0L;
        }

        GetTotalBankMoneyServiceResponse response = GetTotalBankMoneyServiceResponse.newBuilder()
            .setTotalMoney(totalMoney)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

      } catch (Exception e) {
        System.err.println("[ControlNode] Erro ao buscar montante total no DataNode: " + e.getMessage());

        responseObserver.onNext(GetTotalBankMoneyServiceResponse.newBuilder().build());
        responseObserver.onCompleted();
      }
    }

    @Override
    public void searchAccountsByNameService(
        br.com.bancoVirtualDistribuido.grpc.service.SearchAccountsByNameServiceRequest request,
        io.grpc.stub.StreamObserver<br.com.bancoVirtualDistribuido.grpc.service.SearchAccountsByNameServiceResponse> responseObserver) {

      try {
        String nameQuery = request.getName();
        Address target = getLeastLoadedNode();
        br.com.bancoVirtualDistribuido.grpc.data.ReadAccountsByNameDataResponse dataResponse;

        if (target.equals(channel.getAddress())) {
          dataResponse = ControlNode.this.fetchAccountsByNameFromData(nameQuery);
        } else {
          MethodCall call = new MethodCall(
              "fetchAccountsByNameFromData",
              new Object[] { nameQuery },
              new Class[] { String.class });

          dataResponse = dispatcher.callRemoteMethod(
              target,
              call,
              MyRpcOptions.getFirst().setTimeout(Constants.timeOut));
        }

        br.com.bancoVirtualDistribuido.grpc.service.SearchAccountsByNameServiceResponse.Builder responseBuilder = br.com.bancoVirtualDistribuido.grpc.service.SearchAccountsByNameServiceResponse
            .newBuilder();

        for (br.com.bancoVirtualDistribuido.grpc.data.Account dataAcc : dataResponse.getAccountsList()) {
          br.com.bancoVirtualDistribuido.grpc.service.Account serviceAcc = br.com.bancoVirtualDistribuido.grpc.service.Account
              .newBuilder()
              .setUser(dataAcc.getUser())
              .setCpf(dataAcc.getCpf())
              .build();

          responseBuilder.addAccounts(serviceAcc);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();

      } catch (Exception e) {
        System.err.println("[ControlNode] Erro ao pesquisar contas por nome: " + e.getMessage());
        responseObserver.onNext(
            br.com.bancoVirtualDistribuido.grpc.service.SearchAccountsByNameServiceResponse.newBuilder().build());
        responseObserver.onCompleted();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    String name = "ControlNode{" + UUID.randomUUID().toString() + "}";
    int nodePort = Constants.CONTROL_NODE_PORT;

    if (args.length > 0) {
      name = args[0];
      if (args.length > 1 && !args[1].isBlank()) {
        nodePort = Integer.parseInt(args[1]);
      }
    }

    ControlNode node = new ControlNode(name);
    node.start(nodePort);

    Runtime.getRuntime().addShutdownHook(new Thread(node::stop));

    Thread.currentThread().join();
  }
}
