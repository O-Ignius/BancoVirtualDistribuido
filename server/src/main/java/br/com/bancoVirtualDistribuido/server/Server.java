package br.com.bancoVirtualDistribuido.server;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;

import java.util.Scanner;

public class Server {

  public static void main(String[] args) throws Exception {
    String nome = args.length > 0 ? args[0] : "Servidor";

    try (JChannel channel = new JChannel("/config.xml")) {

      channel.setName(nome);

      channel.setReceiver(new Receiver() {
        @Override
        public void viewAccepted(View view) {
          System.out.println("Membros do cluster: " + view);
        }

        @Override
        public void receive(Message msg) {
          System.out.printf(
              "Mensagem recebida de %s: %s%n",
              msg.getSrc(),
              msg.getObject());
        }
      });

      channel.connect("MeuCluster");

      System.out.println(nome + " conectado ao cluster.");
      System.out.println("Digite uma mensagem ou 'sair'.");

      try (Scanner scanner = new Scanner(System.in)) {
        while (scanner.hasNextLine()) {
          String texto = scanner.nextLine();

          if ("sair".equalsIgnoreCase(texto)) {
            break;
          }

          channel.send(null, "[" + nome + "] " + texto);
        }
      }
    }
  }
}
