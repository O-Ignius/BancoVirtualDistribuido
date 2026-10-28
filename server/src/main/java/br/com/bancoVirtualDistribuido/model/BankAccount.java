package br.com.bancoVirtualDistribuido.model;

public class BankAccount {
  private String user;
  private String password;
  private double balance;
  private String cpf;
  private boolean isDeleted;

  public BankAccount(String user, String password,
      double balance, String cpf, boolean isDeleted) {
    this.user = user;
    this.password = password;
    this.balance = balance;
    this.cpf = cpf;
    this.isDeleted = isDeleted;
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

  public boolean getIsDeleted() {
    return isDeleted;
  }
}
