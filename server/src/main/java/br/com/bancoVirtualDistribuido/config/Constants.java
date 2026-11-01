package br.com.bancoVirtualDistribuido.config;

public final class Constants {
  private Constants() {
  }

  public static final int timeOut = 10000;
  public static final String dataChannelName = "DataChannel";
  public static final String controlChannelName = "ControlChannel";
  public static final String accountsDatabaseFolder = "database/";
  public static final String transfersDatabaseFolder = "database/";
  public static final double inititalBalance = 1000;

  // portas grpc
  public static final int GATEWAY_PORT = 50050;
  public static final int CONTROL_NODE_PORT = 50051;
  public static final int DATA_NODE_PORT = 50052;

  // servers ip
  public static final String dataServerIp = "localhost";
  public static final String controlServerIp = "localhost";
}
