package br.com.bancoVirtualDistribuido.config;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class ServiceDiscovery {

  public static String[] discover(String targetService) throws Exception {
    try (MulticastSocket socket = new MulticastSocket(Constants.DISCOVERY_MULTICAST_PORT)) {
      InetAddress group = InetAddress.getByName(Constants.DISCOVERY_MULTICAST_GROUP);
      socket.joinGroup(group);
      socket.setSoTimeout(Constants.timeOut);

      byte[] buffer = new byte[256];
      while (true) {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        String[] parts = msg.split(";");

        // Ignora pacotes de outras camadas e aceita apenas o targetService pedido
        if (parts.length == 3 && targetService.equals(parts[0])) {
          String realIp = packet.getAddress().getHostAddress();
          String port = parts[2];
          socket.leaveGroup(group);
          return new String[] { realIp, port };
        }
      }
    }
  }
}
