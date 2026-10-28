package br.com.bancoVirtualDistribuido.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.bancoVirtualDistribuido.persistence.TransferStorage;
import br.com.bancoVirtualDistribuido.model.*;

public class TransferService {
  private String source;
  private String destiny;
  private double amount;
  private LocalDateTime timestamp;

  private TransferStorage transferStorage;
  private BankAccountService accountService;

  public TransferService(String source, String destiny, double amount, String pathTransferStorage,
      String pathAccountStorage) {
    this.source = source;
    this.destiny = destiny;
    this.amount = amount;
    this.timestamp = LocalDateTime.now();
    this.accountService = new BankAccountService(pathAccountStorage);

    try {
      this.transferStorage = new TransferStorage(pathTransferStorage);
    } catch (IOException e) {
      System.out.println(e);
    }

  }

  public TransferService(String pathTransferStorage) {
    try {
      this.transferStorage = new TransferStorage(pathTransferStorage);
    } catch (IOException e) {
      System.out.println(e);
    }
  }

  public TransferService() {
  }

  // TODO: Verificar pois pode causar inconsistencia, definir regra de negocio
  private void RollBackTransfer(double amount, String source, String destiny) {

    // adiciona saldo enviado de volta ao source
    BankAccount sourceAccount = (accountService.readAccount(source));
    accountService.updateBalance(source, (sourceAccount.getBalance() + amount));
    // remove saldo adicionado de destiny
    BankAccount destinyAccount = accountService.readAccount(destiny);
    accountService.updateBalance(destiny, (destinyAccount.getBalance() - amount));
    // deleta transação
    Delete();
  }

  public void Create() {
    try {
      transferStorage.createTransfer(source, destiny, amount, timestamp);
    } catch (IOException e) {
      System.out.println("Erro no salvamento do arquivo de transferencias: " + e.getMessage());
    }
  }

  public BankTransfer[] ReadAllByCPF(String cpf) {
    try {
      JsonNode data = transferStorage.searchAllSendByCPF(cpf);
      List<BankTransfer> transfers = new ArrayList<>();
      for (JsonNode item : data) {
        BankTransfer transfer = new BankTransfer(
            item.path("uuid").asText(),
            item.path("source").asText(),
            item.path("destiny").asText(),
            item.path("amount").asDouble(),
            LocalDateTime.parse(item.path("timestamp").asText()));

        transfers.add(transfer);
      }

      return transfers.toArray(new BankTransfer[0]);
    } catch (Exception e) {
      throw new RuntimeException("Erro ao ler transferências", e);
    }
  }

  public BankTransfer[] readAll() {
    try {
      JsonNode data = transferStorage.readAll();
      List<BankTransfer> transfers = new ArrayList<>();
      for (JsonNode item : data) {
        BankTransfer transfer = new BankTransfer(
            item.path("uuid").asText(),
            item.path("source").asText(),
            item.path("destiny").asText(),
            item.path("amount").asDouble(),
            LocalDateTime.parse(item.path("timestamp").asText()));

        transfers.add(transfer);
      }

      return transfers.toArray(new BankTransfer[0]);
    } catch (Exception e) {
      throw new RuntimeException("Erro ao ler transferências", e);
    }
  }

  public void Delete() {
    try {
      transferStorage.delete(source, destiny);
    } catch (IOException e) {
      System.out.println("Erro ao deletar transação" + e.getMessage());
    }
  }

  public void saveAll(Collection<BankTransfer> transfers) {
    try {
      transferStorage.saveAll(transfers);
    } catch (IOException e) {
      System.out.println("Erro ao salvar dados de transacao recebidos do coordenador" + e.getMessage());
    }
  }

}
