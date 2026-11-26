package br.com.bancoVirtualDistribuido.config;

import org.jgroups.JChannel;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Constants {

  private Constants() {
  }

  // CALA A BOCA JGROUPS
  // Mantém referência forte para o GC não resetar o nível de log do JGroups
  private static final Logger JGROUPS_UDP_LOGGER = Logger.getLogger("org.jgroups.protocols.UDP");
  private static final Logger JGROUPS_TP_LOGGER = Logger.getLogger("org.jgroups.protocols.TP");

  static {
    JGROUPS_UDP_LOGGER.setLevel(Level.SEVERE);
    JGROUPS_TP_LOGGER.setLevel(Level.SEVERE);
  }

  // Métodos auxiliares para leitura de variáveis de ambiente
  private static String getEnv(String key, String defaultValue) {
    return System.getenv().getOrDefault(key, defaultValue);
  }

  private static int getEnvInt(String key, int defaultValue) {
    return Integer.parseInt(getEnv(key, String.valueOf(defaultValue)));
  }

  private static double getEnvDouble(String key, double defaultValue) {
    return Double.parseDouble(getEnv(key, String.valueOf(defaultValue)));
  }

  // Timeouts
  public static final int timeOut = getEnvInt("TIME_OUT", 10000);
  public static final int timeOutRetries = getEnvInt("TIME_OUT_RETRIES", 8000);
  public static final int timeOutRollBack = getEnvInt("TIME_OUT_ROLLBACK", 3000);

  // Canais
  public static final String dataChannelName = getEnv("DATA_CHANNEL_NAME", "DataChannel");
  public static final String controlChannelName = getEnv("CONTROL_CHANNEL_NAME", "ControlChannel");

  // Banco de dados
  public static final String accountsDatabaseFolder = getEnv("ACCOUNTS_DATABASE_FOLDER", "database/");
  public static final String transfersDatabaseFolder = getEnv("TRANSFERS_DATABASE_FOLDER", "database/");
  public static final double inititalBalance = getEnvDouble("INITIAL_BALANCE", 1000);

  // Retries
  public static final int maxRetriesSaveAccount = getEnvInt("MAX_RETRIES_SAVE_ACCOUNT", 3);
  public static final int maxRetriesSaveTransfer = getEnvInt("MAX_RETRIES_SAVE_TRANSFER", 3);

  // Portas gRPC
  public static final int GATEWAY_PORT = getEnvInt("GATEWAY_PORT", 50050);
  public static final int CONTROL_NODE_PORT = getEnvInt("CONTROL_NODE_PORT", 50051);
  public static final int DATA_NODE_PORT = getEnvInt("DATA_NODE_PORT", 50052);

  // Multicast
  public static final String DISCOVERY_MULTICAST_GROUP = getEnv("DISCOVERY_MULTICAST_GROUP", "229.0.0.1");
  public static final int DISCOVERY_MULTICAST_PORT = getEnvInt("DISCOVERY_MULTICAST_PORT", 4446);
  public static final int sleepMulticastDiscover = getEnvInt("SLEEP_MULTICAST_DISCOVER", 2000);

  // Cluster
  public static final int minDataNodesCluster = getEnvInt("MIN_DATA_NODES_CLUSTER", 3);
  public static final int timeOutWaitClusters = getEnvInt("TIME_OUT_WAIT_CLUSTERS", 3000);

  // Maioria JGroups
  public static int getMajority(JChannel channel) {
    if (channel == null || channel.getView() == null) {
      return 1;
    }

    int totalNodes = channel.getView().getMembers().size();
    return (totalNodes / 2) + 1;
  }
}
