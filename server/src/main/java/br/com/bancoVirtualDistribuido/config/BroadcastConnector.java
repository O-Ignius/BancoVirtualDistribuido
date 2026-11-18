package br.com.bancoVirtualDistribuido.config;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class BroadcastConnector {

  private volatile boolean running = false;
  private Thread broadcasterThread;

  /**
   * @param serviceType "CONTROL" ou "DATA" ou "GATEWAY"
   * @param grpcPort    Porta onde o servidor gRPC do coordenador está escutando
   */
  public synchronized void start(String serviceType, int grpcPort) {
    if (running)
      return;
    running = true;

    broadcasterThread = new Thread(() -> {
      try (DatagramSocket socket = new DatagramSocket()) {
        InetAddress group = InetAddress.getByName(Constants.DISCOVERY_MULTICAST_GROUP);
        String localIp = InetAddress.getLocalHost().getHostAddress();

        // "CONTROL;192.168.1.10;50050"
        String message = serviceType + ";" + localIp + ";" + grpcPort;
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);

        System.out.println("[Multicast] Anunciando coordenador " + message);

        while (running) {
          DatagramPacket packet = new DatagramPacket(
              buffer, buffer.length, group, Constants.DISCOVERY_MULTICAST_PORT);
          socket.send(packet);
          Thread.sleep(Constants.sleepMulticastDiscover);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        System.err.println("[Multicast] Erro ao anunciar coordenador: " + e.getMessage());
      }
    }, "Multicast-Broadcaster-" + serviceType);

    broadcasterThread.setDaemon(true);
    broadcasterThread.start();
  }

  public synchronized void stop() {
    running = false;
    if (broadcasterThread != null) {
      broadcasterThread.interrupt();
    }
  }
}
