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
      if (account.path("cpf").asText().equals(cpf) && account.path("isDeleted").asBoolean() == false) {
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

  // tenta logar com as credenciais
  public boolean loginAccount(String id, String password) {
    ArrayNode accounts = (ArrayNode) data.get("accounts");

    for (JsonNode account : accounts) {
      if (account.path("cpf").asText().equals(id) || account.path("user").equals(id)) {
        if (account.path("password").equals(password)) {
          return true;
        }
      }
    }

    return false;
  }

  // cria conta
  public void createAccount(
      String cpf,
      String user,
      String password) throws IOException {

    boolean reCreating = false;

    if (cpf == null || cpf.isBlank()) {
      throw new IllegalArgumentException("CPF is required.");
    }

    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("password is required.");
    }

    if (user == null || user.isBlank()) {
      throw new IllegalArgumentException("Username is required.");
    }

    ArrayNode accounts = (ArrayNode) data.get("accounts");

    for (JsonNode account : accounts) {
      if (account.path("cpf").asText().equals(cpf)) {
        if (account.path("isDeleted").asBoolean() == true) {
          ((ObjectNode) account).put("isDeleted", false);
          reCreating = true;
        } else {
          throw new IllegalArgumentException(
              "An account with this CPF already exists.");
        }
      }
    }

    ObjectNode newAccount = mapper.createObjectNode();
    newAccount.put("user", user);
    newAccount.put("cpf", cpf);
    newAccount.put("password", password);
    // Saldo inicial de 1000 dinheiros
    if (!reCreating) {
      newAccount.put("balance", 1000);
    }
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

  // altera nome e senha
  public void modifyAccountData(String cpf, String newUser, String newPassword)
      throws IOException {

    boolean needSave = false;

    JsonNode account = searchByCPF(cpf);
    if (newUser != null && !newUser.isBlank()) {
      ((ObjectNode) account).put("user", newUser);
      needSave = true;
    }

    if (newPassword != null && !newPassword.isBlank()) {
      ((ObjectNode) account).put("password", newPassword);
      needSave = true;
    }

    if (needSave) {
      save();
    }
  }

  public void delete(String cpf) throws IOException {
    JsonNode account = searchByCPF(cpf);

    if (account.path("balance").asDouble() > 0) {
      throw new IllegalStateException("Ainda há saldo na conta, impossível excluir!");
    }

    ((ObjectNode) account).put("isDeleted", true);

    save();
  }

  public void save() throws IOException {
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(archive.toFile(), data);
  }
}
