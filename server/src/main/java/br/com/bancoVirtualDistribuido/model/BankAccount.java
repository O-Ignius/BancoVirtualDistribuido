package br.com.bancoVirtualDistribuido.model;

public class BankAccount {
  private String user;
  private String password;
  private double balance;
  private String cpf;

  public BankAccount(String user, String password,
      double balance, String cpf) {
    this.user = user;
    this.password = password;
    this.balance = balance;
    this.cpf = cpf;
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }

  public double getBalance() {
    return balance;
  }

  public String getCpf() {
    return cpf;
  }
}
