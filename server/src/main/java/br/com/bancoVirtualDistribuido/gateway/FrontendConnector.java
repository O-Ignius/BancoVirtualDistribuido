package br.com.bancoVirtualDistribuido.gateway;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.config.BroadcastConnector;
import br.com.bancoVirtualDistribuido.config.ServiceDiscovery;
import br.com.bancoVirtualDistribuido.grpc.service.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class FrontendConnector {

  private Server gatewayServer;
  private ManagedChannel controlNodeChannel;
  private final BroadcastConnector broadcaster = new BroadcastConnector();

  // O "telefone" para ligar para o Controle
  private BankServiceGrpc.BankServiceBlockingStub controlStub;

  public void start() throws Exception {
    connectToControl();

    gatewayServer = ServerBuilder.forPort(Constants.GATEWAY_PORT)
        .addService(new RequestProcessor(controlStub))
        .build()
        .start();

    // após build do servidor grpc, começa a anúnciá-lo
    broadcaster.start("GATEWAY", Constants.GATEWAY_PORT);

    System.out.println("[Gateway] Online! Aguardando cliente Python na porta " + Constants.GATEWAY_PORT);
  }

  public void stop() {
    // para anuncio do multicast de gRPC
    broadcaster.stop();
    if (gatewayServer != null) {
      gatewayServer.shutdown();
    }
    if (controlNodeChannel != null) {
      controlNodeChannel.shutdown();
    }
    System.out.println("[Gateway] Conexões encerradas.");
  }

  public synchronized void connectToControl() throws Exception {
    if (controlNodeChannel != null && !controlNodeChannel.isShutdown()) {
      controlNodeChannel.shutdownNow();
    }

    System.out.println("[Gateway] Buscando ControlNode coordenador via Multicast...");
    String[] endpoint = ServiceDiscovery.discover("CONTROL");
    String host = endpoint[0];
    int port = Integer.parseInt(endpoint[1]);

    controlNodeChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();

    controlStub = BankServiceGrpc.newBlockingStub(controlNodeChannel);
    System.out.println("[Gateway] Canal aberto com o ControlNode em " + host + ":" + port);
  }

  public void blockUntilShutdown() throws InterruptedException {
    if (gatewayServer != null) {
      gatewayServer.awaitTermination();
    }
  }

  public static void main(String[] args) throws Exception {
    FrontendConnector gateway = new FrontendConnector();
    gateway.start();

    Runtime.getRuntime().addShutdownHook(new Thread(gateway::stop));

    gateway.blockUntilShutdown();
  }
}
