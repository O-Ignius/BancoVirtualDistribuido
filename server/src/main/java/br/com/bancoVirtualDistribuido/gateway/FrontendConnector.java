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
  private volatile BankServiceGrpc.BankServiceBlockingStub controlStub;

  public void start() throws Exception {
    connectToControl();

    gatewayServer = ServerBuilder.forPort(Constants.GATEWAY_PORT)
        .addService(new RequestProcessor(this))
        .build()
        .start();

    // após build do servidor grpc, começa a anunciá-lo
    broadcaster.start("GATEWAY", Constants.GATEWAY_PORT);

    System.out.println("[Gateway] Online! Aguardando cliente Python na porta " + Constants.GATEWAY_PORT);
  }

  public void stop() {
    // para anúncio do multicast de gRPC
    broadcaster.stop();
    if (gatewayServer != null) {
      gatewayServer.shutdown();
    }
    if (controlNodeChannel != null) {
      controlNodeChannel.shutdown();
    }
    System.out.println("[Gateway] Conexões encerradas.");
  }

  public synchronized void connectToControl() throws InterruptedException {
    if (controlNodeChannel != null && !controlNodeChannel.isShutdown()) {
      controlNodeChannel.shutdownNow();
    }

    int tries = 1;
    while (!Thread.currentThread().isInterrupted()) {
      try {
        System.out.println("[Gateway] Buscando ControlNode coordenador via Multicast (tentativa " + tries + ")...");
        String[] endpoint = ServiceDiscovery.discover("CONTROL");
        String host = endpoint[0];
        int port = Integer.parseInt(endpoint[1]);

        controlNodeChannel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();

        controlStub = BankServiceGrpc.newBlockingStub(controlNodeChannel);
        System.out.println("[Gateway] Canal aberto com o ControlNode em " + host + ":" + port);
        return;

      } catch (Exception e) {
        System.err
            .println("[Gateway] ControlNode não encontrado (" + e.getMessage() + "). Tentando novamente em 2s...");
        tries++;
        Thread.sleep(2000);
      }
    }

    throw new InterruptedException("Busca pelo ControlNode interrompida.");
  }

  public BankServiceGrpc.BankServiceBlockingStub getControlStub() {
    return controlStub;
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
