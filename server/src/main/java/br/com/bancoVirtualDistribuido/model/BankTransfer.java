package br.com.bancoVirtualDistribuido.model;

import java.time.Instant;

public class BankTransfer {
  private String uuid;
  private String source;
  private String destiny;
  private double amount;
  private Instant timestamp;

  // Construtor vazio
  public BankTransfer() {
  }

  // Construtor com todos os atributos
  public BankTransfer(String uuid, String source, String destiny, double amount,
      Instant timestamp) {
    this.uuid = uuid;
    this.source = source;
    this.destiny = destiny;
    this.amount = amount;
    this.timestamp = timestamp;
  }

  // Getters
  public String getUUId() {
    return uuid;
  }

  public String getSource() {
    return source;
  }

  public String getDestiny() {
    return destiny;
  }

  public double getAmount() {
    return amount;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
