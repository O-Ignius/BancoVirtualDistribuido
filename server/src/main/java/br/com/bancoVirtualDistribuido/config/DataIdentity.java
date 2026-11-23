package br.com.bancoVirtualDistribuido.config;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class DataIdentity {

  // trava os locks para o SO não libera-los se estiver sendo executado
  private static FileChannel lockChannel;
  private static FileLock fileLock;

  public static String resolveNodeName(String[] args, String prefix, String dataFolder) {
    // se nome do nó for passado, tenta pegar da database
    if (args != null && args.length > 0 && !args[0].isBlank()) {
      return args[0].trim();
    }

    String host = getHostNamePC();
    File dirLocks = new File(dataFolder, ".locks");
    dirLocks.mkdirs();

    // usa lock exclusivo do SO e procura sempre o menor disponivel
    int indice = 1;
    while (true) {
      String possibly = host + "_" + prefix + "_" + indice;
      File arquivoLock = new File(dirLocks, possibly + ".lock");

      try {
        RandomAccessFile raf = new RandomAccessFile(arquivoLock, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = channel.tryLock(); // returna null se lock já travado

        if (lock != null) {
          lockChannel = channel;
          fileLock = lock;
          return possibly;
        }
        channel.close();
        raf.close();
      } catch (Exception ignored) {
        // tenta outro indice se atual está trancado
      }
      indice++;
    }
  }

  private static String getHostNamePC() {
    try {
      String host = InetAddress.getLocalHost().getHostName();
      return host.split("\\.")[0].replaceAll("[^a-zA-Z0-9_-]", "");
    } catch (Exception e) {
      return "local";
    }
  }
}
