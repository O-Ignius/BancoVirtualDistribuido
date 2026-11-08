package br.com.bancoVirtualDistribuido.nodes;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.util.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.UUID;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.grpc.data.*;
import br.com.bancoVirtualDistribuido.grpc.service.*;

public class ControlNode implements Receiver {

  private JChannel channel;
  private RpcDispatcher dispatcher;
  private LockService lockService;
  private String nodeName;

  private DataNodeClient dataClient;
  private io.grpc.Server grpcServer;

  // Cliente online
  private final Set<String> loggedClients = new HashSet<>();

  public ControlNode(String nodeName) {
    this.nodeName = nodeName;
  }

  public void start() throws Exception {
    channel = new JChannel("jgroups-config.xml");
    channel.setName(nodeName);

    dispatcher = new RpcDispatcher(channel, this);
    dispatcher.setReceiver(this);

    lockService = new LockService(channel);

    channel.connect("ControlChannel");

    // Pega estado do coordenador
    channel.getState(null, 10000);

    // Conecta ao servidor de dados
    dataClient = new DataNodeClient();

    System.out.println("[" + nodeName + "] Conectado ao canal de Controle.");

    // Abre conexão para Gateway
    grpcServer = io.grpc.ServerBuilder.forPort(Constants.CONTROL_NODE_PORT)
        .addService(new ControlNodeGrpcServiceImpl())
        .build()
        .start();

    System.out.println("[" + nodeName + "] Servidor gRPC escutando o Gateway na porta "
        + Constants.CONTROL_NODE_PORT);
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

  // ---------- RPC ------------

  public boolean loginService(String idCliente, String password) {
    if (!dataClient.verifyCredentialsData(idCliente, password)) {
      return false;
    }

    synchronized (loggedClients) {
      loggedClients.add(idCliente);
    }

    return true;
  }

  public boolean processTransfer(String idSource, String idDestiny, double amount) {
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
    }
  }

  public void stop() {
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

      boolean success = ControlNode.this.loginService(
          request.getId(), request.getPassword());

      LoginServiceResponse response = LoginServiceResponse.newBuilder()
          .setSuccess(success)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void createAccountService(CreateAccountServiceRequest request,
        io.grpc.stub.StreamObserver<CreateAccountServiceResponse> responseObserver) {

      System.out.println("[" + nodeName
          + "] Processando criação de conta para: " + request.getUser());

      // O Controle solicita ao DataNode o salvamento da conta
      boolean success = dataClient.saveAccountData(
          request.getCpf(),
          request.getUser(),
          request.getPassword(),
          Constants.inititalBalance);

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

      // Processa transferência adquirindo os LOCKS do JGroups
      boolean success = processTransfer(source, destiny, amount);

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
        System.out.println("[ControlNode] Buscando dados da conta: " + cpf);

        // 2. Pede pro datanode passando a String DIRETO (resolve o Erro 1)
        ReadAccountDataResponse dataResponse = dataClient.readAccountData(cpf);

        // 3. Monta a resposta
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
        System.out.println("[ControlNode] Buscando extrato de transferências para: " + cpf);

        GetTransferDataResponse dataResponse = dataClient.readTransferData(cpf);

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
  }

  public static void main(String[] args) throws Exception {
    String name = args.length > 0
        ? args[0]
        : "ControlNode{" + UUID.randomUUID().toString() + "}";

    ControlNode node = new ControlNode(name);
    node.start();

    Runtime.getRuntime().addShutdownHook(new Thread(node::stop));

    Thread.currentThread().join();
  }
}
