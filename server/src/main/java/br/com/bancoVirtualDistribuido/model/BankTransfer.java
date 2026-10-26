package br.com.bancoVirtualDistribuido.model;

import java.time.LocalDateTime;

public class BankTransfer {
  private String source;
  private String destiny;
  private double amount;
  private LocalDateTime timestamp;

  // Construtor vazio
  public BankTransfer() {
  }

  // Construtor com todos os atributos
  public BankTransfer(String source, String destiny, double amount,
      LocalDateTime timestamp) {
    this.source = source;
    this.destiny = destiny;
    this.amount = amount;
    this.timestamp = timestamp;
  }

  // Getters
  public String getSource() {
    return source;
  }

  public String getDestiny() {
    return destiny;
  }

  public double getAmount() {
    return amount;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }
}
