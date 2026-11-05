package br.com.bancoVirtualDistribuido.nodes;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
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
import br.com.bancoVirtualDistribuido.nodes.DataNodeClient;
import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.grpc.*;

public class ControlNode implements Receiver {

  private JChannel channel;
  private RpcDispatcher dispatcher;
  private LockService lockService;
  private String nodeName;

  private DataNodeClient dataClient;
  private io.grpc.Server grpcServer;

  // cliente online
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

    // pega estado do coordenador
    channel.getState(null, 10000);

    // conecta ao serviidor de dados
    dataClient = new DataNodeClient();

    System.out.println("[" + nodeName + "] Conectado ao canal de Controle.");

    // abre conexao para Gateway
    dataClient = new DataNodeClient();

    grpcServer = io.grpc.ServerBuilder.forPort(Constants.CONTROL_NODE_PORT)
        .addService(new ControlNodeGrpcServiceImpl())
        .build()
        .start();

    System.out.println("[" + nodeName + "] Servidor gRPC escutando o Gateway na porta " + Constants.CONTROL_NODE_PORT);
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

  // @Override
  // public void viewAccepted(View view) {
  // System.out.println("[" + nodeName + "] Nova visão do grupo: " + view);
  //
  // Address atualCoordinator = view.getMembers().get(0);
  //
  // if (atualCoordinator.equals(channel.getAddress())) {
  // System.out.println("[" + nodeName + "] Eu sou o COORDENador da camada de
  // Controle.");
  // }
  // }

  // ---------- RPC ------------

  public boolean login(String idCliente, String password) {
    if (!dataClient.verifyCredentials(idCliente, password)) {
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

      // abstracao grpc
      boolean success = dataClient.executeDebitAndCredit(idSource, idDestiny, amount);

      if (success) {
        // salva no no de dados
        String transferId = UUID.randomUUID().toString();
        dataClient.saveTransfer(transferId, idSource, idDestiny, amount);
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
      grpcServer.shutdown(); // Desliga o servidor que atende o Gateway
    }
    // fecha conexao com servidor de dadps
    if (dataClient != null) {
      dataClient.shutdown();
    }
    if (channel != null) {
      channel.close();
    }
  }

  // - ---------------------------tradutor grpc
  private class ControlNodeGrpcServiceImpl
      extends BankServiceGrpc.BankServiceImplBase {

    @Override
    public void login(LoginRequest request,
        io.grpc.stub.StreamObserver<LoginResponse> responseObserver) {
      boolean success = ControlNode.this.login(request.getId(), request.getPassword());
      LoginResponse response = LoginResponse.newBuilder()
          .setSuccess(success)
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void createAccount(CreateAccountRequest request,
        io.grpc.stub.StreamObserver<CreateAccountResponse> responseObserver) {

      System.out.println("[" + nodeName + "] Processando criação de conta para: " + request.getUser());

      // O Controle pede para o DataNode salvar a conta (usando o CPF como ID e saldo
      // inicial zero)
      boolean success = dataClient.saveAccount(
          String.valueOf(request.getCpf()), request.getUser(), request.getPassword(), 1000.0);

      CreateAccountResponse response = CreateAccountResponse
          .newBuilder()
          .setSuccess(success)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void saveTransfer(SaveTransferRequest request,
        io.grpc.stub.StreamObserver<SaveTransferResponse> responseObserver) {

      String source = String.valueOf(request.getSourceCpf());
      String destiny = String.valueOf(request.getDestinationCpf());
      double amount = request.getAmountCents() / 100.0;

      // ==========================================================
      // AQUI É A MÁGICA: Chama a SUA função que adquire os LOCKS
      // do JGroups antes de repassar a ordem pro DataNode!
      // ==========================================================
      boolean success = processTransfer(source, destiny, amount);

      SaveTransferResponse response = SaveTransferResponse
          .newBuilder()
          .setSuccess(success)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    // Métodos de Get (Leitura) - Implementação básica para o compilador não
    // reclamar
    @Override
    public void getAccount(GetAccountRequest request,
        io.grpc.stub.StreamObserver<GetAccountResponse> responseObserver) {
      // Por enquanto retorna vazio, se quiser buscar saldo depois, chama o
      // dataClient.readAccount
      GetAccountResponse response = GetAccountResponse
          .newBuilder().build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /*
    @Override
    public void getTransfer(GetTransferRequest request,
        io.grpc.stub.StreamObserver<GetTransferResponse> responseObserver) {
      GetTransferResponse response = GetTransferResponse
          .newBuilder().build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
    */

  }

  public static void main(String[] args) throws Exception {
    String name = args.length > 0 ? args[0] : "ControlNode{" + UUID.randomUUID().toString() + "}";
    ControlNode node = new ControlNode(name);
    node.start();

    Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
    Thread.currentThread().join();
  }
}
