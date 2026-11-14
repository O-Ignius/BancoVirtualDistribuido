package br.com.bancoVirtualDistribuido.gateway;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.grpc.service.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class FrontendConnector {

  private Server gatewayServer;
  private ManagedChannel controlNodeChannel;

  // O "telefone" para ligar para o Controle
  private BankServiceGrpc.BankServiceBlockingStub controlStub;

  public void start() throws Exception {
    controlNodeChannel = ManagedChannelBuilder.forAddress(Constants.controlServerIp, Constants.CONTROL_NODE_PORT)
        .usePlaintext()
        .build();

    controlStub = BankServiceGrpc.newBlockingStub(controlNodeChannel);
    System.out.println("[Gateway] Canal aberto com o ControlNode em localhost:" + Constants.CONTROL_NODE_PORT);

    gatewayServer = ServerBuilder.forPort(Constants.GATEWAY_PORT)
        .addService(new RequestProcessor(controlStub))
        .build()
        .start();

    System.out.println("[Gateway] Online! Aguardando cliente Python na porta " + Constants.GATEWAY_PORT);
  }

  public void stop() {
    if (gatewayServer != null) {
      gatewayServer.shutdown();
    }
    if (controlNodeChannel != null) {
      controlNodeChannel.shutdown();
    }
    System.out.println("[Gateway] Conexões encerradas.");
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
