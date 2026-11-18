package br.com.bancoVirtualDistribuido.config;

import org.jgroups.JChannel;

public final class Constants {
  private Constants() {
  }

  public static final int timeOut = 10000; // ms
  public static final int timeOutRetries = 8000; // ms
  public static final int timeOutRollBack = 3000; // ms
  public static final String dataChannelName = "DataChannel";
  public static final String controlChannelName = "ControlChannel";
  public static final String accountsDatabaseFolder = "database/";
  public static final String transfersDatabaseFolder = "database/";
  public static final double inititalBalance = 1000;

  // retries
  public static final int maxRetriesSaveAccount = 3;
  public static final int maxRetriesSaveTransfer = 3;

  // portas grpc
  public static final int GATEWAY_PORT = 50050;
  public static final int CONTROL_NODE_PORT = 50051;
  public static final int DATA_NODE_PORT = 50052;

  // ips fixos
  public static final String dataServerIp = "localhost";
  public static final String controlServerIp = "localhost";

  // ips de broadcast para descoberta de gateway e coordenadores
  public static final String DISCOVERY_MULTICAST_GROUP = "229.0.0.1";
  public static final int DISCOVERY_MULTICAST_PORT = 4446;
  public static final int sleepMulticastDiscover = 2000;// ms

  // configs para minimo de servidores e tempo de espera para esses minimos
  public static final int minDataNodesCluster = 1;
  public static final int timeOutWaitClusters = 3000; // ms

  // função para constante jGroups
  public static int getMajority(JChannel channel) {
    // Segurança: Se o canal não estiver conectado ou não tiver visão ainda, a
    // maioria é 1 (ele mesmo)
    if (channel == null || channel.getView() == null) {
      return 1;
    }

    int totalNodes = channel.getView().getMembers().size();
    return (totalNodes / 2) + 1;
  }
}
