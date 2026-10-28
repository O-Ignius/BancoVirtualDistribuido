package br.com.bancoVirtualDistribuido.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CompleteDataNode implements Serializable {

  private static final long serialVersionUID = 1L;

  private final Map<String, BankAccount> accounts;
  private final Map<String, BankTransfer> transfers;

  public CompleteDataNode(
      Map<String, BankAccount> accounts,
      Map<String, BankTransfer> transfers) {

    this.accounts = new HashMap<>(accounts);
    this.transfers = new HashMap<>(transfers);
  }

  public Map<String, BankAccount> getAccounts() {
    return accounts;
  }

  public Map<String, BankTransfer> getTransfers() {
    return transfers;
  }
}
