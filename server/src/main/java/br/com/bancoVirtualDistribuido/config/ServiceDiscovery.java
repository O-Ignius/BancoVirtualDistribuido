package br.com.bancoVirtualDistribuido.config;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class ServiceDiscovery {

  public static String[] discover(String targetService) throws Exception {
    try (MulticastSocket socket = new MulticastSocket(Constants.DISCOVERY_MULTICAST_PORT)) {
      socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      socket.setSoTimeout(10000);

      InetAddress group = InetAddress.getByName(Constants.DISCOVERY_MULTICAST_GROUP);
      InetSocketAddress groupAddr = new InetSocketAddress(group, Constants.DISCOVERY_MULTICAST_PORT);

      boolean joinedAny = false;
      for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!nif.isUp() || !nif.supportsMulticast()) {
          continue;
        }
        boolean hasIpv4 = Collections.list(nif.getInetAddresses()).stream()
            .anyMatch(addr -> addr instanceof Inet4Address);
        if (!hasIpv4) {
          continue;
        }
        try {
          socket.joinGroup(groupAddr, nif);
          joinedAny = true;
        } catch (Exception ignored) {
        }
      }

      if (!joinedAny) {
        socket.joinGroup(groupAddr, null);
      }

      byte[] buffer = new byte[256];
      while (true) {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        String[] parts = msg.split(";");

        if (parts.length == 3 && targetService.equals(parts[0])) {
          String realIp = packet.getAddress().getHostAddress();
          String port = parts[2];
          return new String[] { realIp, port };
        }
      }
    }
  }
}
