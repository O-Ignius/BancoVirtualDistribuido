package br.com.bancoVirtualDistribuido.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AccountStorage {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Path archive;
  private ObjectNode data;

  public AccountStorage(String path) throws IOException {
    this.archive = Path.of(path);

    if (Files.exists(archive)) {
      JsonNode root = mapper.readTree(archive.toFile());

      if (!(root instanceof ObjectNode)) {
        throw new IOException(
            "The JSON root must be an object.");
      }

      this.data = (ObjectNode) root;

      if (!(data.get("accounts") instanceof ArrayNode)) {
        throw new IOException(
            "The JSON must contain an accounts array.");
      }
    } else {
      Path directory = archive.getParent();

      if (directory != null) {
        Files.createDirectories(directory);
      }

      this.data = mapper.createObjectNode();
      this.data.putArray("accounts");
      save();
    }
  }

  public JsonNode readAll() {
    return data;
  }

  // pega conta por cpf
  public JsonNode searchByCPF(String cpf) {
    ArrayNode accounts = (ArrayNode) data.get("accounts");

    for (JsonNode account : accounts) {
      if (account.path("cpf").asText().equals(cpf)) {
        return account;
      }
    }

    // se não encontra lança excessão
    throw new IllegalArgumentException("Conta não encontrada");
  }

  // pega saldo
  public double getBalance(String cpf) {
    JsonNode account = searchByCPF(cpf);

    return account.path("balance").asDouble();
  }

  // cria conta
  public void createAccount(
      String cpf,
      String holder,
      double initialBalance) throws IOException {

    if (cpf == null || cpf.isBlank()) {
      throw new IllegalArgumentException("CPF is required.");
    }

    if (holder == null || holder.isBlank()) {
      throw new IllegalArgumentException("Holder is required.");
    }

    if (initialBalance < 0) {
      throw new IllegalArgumentException(
          "Initial balance cannot be negative.");
    }

    ArrayNode accounts = (ArrayNode) data.get("accounts");

    for (JsonNode account : accounts) {
      if (account.path("cpf").asText().equals(cpf)) {
        throw new IllegalArgumentException(
            "An account with this CPF already exists.");
      }
    }

    ObjectNode newAccount = mapper.createObjectNode();
    newAccount.put("cpf", cpf);
    newAccount.put("holder", holder);
    newAccount.put("balance", initialBalance);

    accounts.add(newAccount);
    save();
  }

  // altera saldo
  public void modifyBalance(String cpf, double newBalance)
      throws IOException {

    if (newBalance < 0) {
      throw new IllegalArgumentException(
          "Balance cannot be negative.");
    }

    JsonNode account = searchByCPF(cpf);
    ((ObjectNode) account).put("balance", newBalance);

    save();
  }

  public void save() throws IOException {
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(archive.toFile(), data);
  }
}
