package br.com.bancoVirtualDistribuido.service;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import br.com.bancoVirtualDistribuido.persistence.AccountStorage;
import br.com.bancoVirtualDistribuido.model.BankAccount;

public class BankAccountService {
  private String user;
  // unique ID
  private String cpf;
  private String password;

  private AccountStorage accountStorage;

  public BankAccountService(String user, String cpf, String password, String pathArchive) {
    this.user = user;
    this.cpf = cpf;
    this.password = password;

    try {
      this.accountStorage = new AccountStorage(pathArchive);
    } catch (IOException e) {
      System.out.println(e);
    }

  }

  public BankAccountService(String pathArchive) {
    try {
      this.accountStorage = new AccountStorage(pathArchive);
    } catch (IOException e) {
      System.out.println(e);
    }
  }

  public BankAccountService() {
  }

  public void login(String id, String password) {
    if (accountStorage.loginAccount(id, password)) {
      System.out.println("Login com sucesso!");
    } else {
      System.out.println("Login ou Senha inválidos!");
    }
  }

  public void create() {
    try {
      accountStorage.createAccount(user, cpf, password);
    } catch (IOException e) {
      System.out.println("Erro no salvamento do arquivo de transferencias: " + e.getMessage());
    }
  }

  public BankAccount[] readAll() {

    try {
      JsonNode data = accountStorage.readAll();
      List<BankAccount> accounts = new ArrayList<>();
      for (JsonNode item : data) {
        BankAccount transfer = new BankAccount(
            item.path("user").asText(),
            item.path("password").asText(),
            item.path("balance").asDouble(),
            item.path("cpf").asText());

        accounts.add(transfer);
      }

      return accounts.toArray(new BankAccount[0]);
    } catch (Exception e) {
      throw new RuntimeException("Erro ao ler contas", e);
    }
  }

  public BankAccount readAccount(String cpf) {
    JsonNode account = accountStorage.searchByCPF(cpf);

    return new BankAccount(
        account.path("user").asText(),
        account.path("password").asText(),
        account.path("balance").asDouble(),
        account.path("cpf").asText());
  }

  public void updateAccount(String user, String cpf, String password) {
    try {
      accountStorage.modifyAccountData(cpf, user, password);
    } catch (IOException e) {
      System.out.println("Erro na modificação dos dados da conta: " + e);
    }
  }

  public void updateBalance(String cpf, double newBalance) {
    try {
      accountStorage.modifyBalance(cpf, newBalance);
    } catch (IOException e) {
      System.out.println("Erro na modificação do saldo: " + e);
    }
  }

  public void delete(String cpf) {
    try {
      accountStorage.delete(cpf);
    } catch (IOException e) {
      System.out.println("Erro na deleção da conta: " + e);
    }
  }

}
