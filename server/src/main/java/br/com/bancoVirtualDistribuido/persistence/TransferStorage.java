package br.com.bancoVirtualDistribuido.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import java.util.UUID;

public class TransferStorage {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Path archive;
  private ObjectNode data;

  public TransferStorage(String path) throws IOException {
    this.archive = Path.of(path);

    if (Files.exists(archive)) {
      JsonNode root = mapper.readTree(archive.toFile());

      if (!(root instanceof ObjectNode)) {
        throw new IOException(
            "The JSON root must be an object.");
      }

      this.data = (ObjectNode) root;

      if (!(data.get("transfers") instanceof ArrayNode)) {
        throw new IOException(
            "The JSON must contain an accounts array.");
      }
    } else {
      Path directory = archive.getParent();

      if (directory != null) {
        Files.createDirectories(directory);
      }

      this.data = mapper.createObjectNode();
      this.data.putArray("transfers");
      save();
    }
  }

  public JsonNode readAll() {
    return data;
  }

  // todos envios por cpf
  public JsonNode searchAllSendByCPF(String cpf) {
    ArrayNode transfers = (ArrayNode) data.get("transfers");
    ArrayNode result = mapper.createArrayNode();

    for (JsonNode transfer : transfers) {
      if (transfer.path("source").asText().equals(cpf)) {
        result.add(transfer);
      }
    }

    return result;
  }

  // todos os recebimentos por cpf
  public JsonNode searchAllRecieveByCPF(String cpf) {
    ArrayNode transfers = (ArrayNode) data.get("transfers");
    ArrayNode result = mapper.createArrayNode();

    for (JsonNode transfer : transfers) {
      if (transfer.path("destiny").asText().equals(cpf)) {
        result.add(transfer);
      }
    }

    return result;
  }

  // pega todas transferencia de uma conta por cpf
  public ArrayNode searchAllByCPF(String cpf) {
    ArrayNode transfers = (ArrayNode) data.get("transfers");
    ArrayNode result = mapper.createArrayNode();

    for (JsonNode transfer : transfers) {
      String source = transfer.path("source").asText();
      String destiny = transfer.path("destiny").asText();

      if (source.equals(cpf) || destiny.equals(cpf)) {
        result.add(transfer);
      }
    }

    return result;
  }

  // pega todas as transferencias realizadas entre 2 contas
  public JsonNode searchCPFToCPF(String cpf1, String cpf2) {
    ArrayNode transfers = (ArrayNode) data.get("accounts");
    ArrayNode result = mapper.createArrayNode();

    for (JsonNode transfer : transfers) {
      if (transfer.path("source").asText().equals(cpf1) && transfer.path("destiny").asText().equals(cpf2)) {
        result.add(transfer);
      }
    }

    return result;
  }

  // pega todas por determinada data/hora
  public ArrayNode searchByDateTime(
      LocalDateTime start,
      LocalDateTime end) {
    if (start.isAfter(end)) {
      throw new IllegalArgumentException(
          "Start date must be before end date.");
    }

    ArrayNode transfers = (ArrayNode) data.get("transfers");
    ArrayNode result = mapper.createArrayNode();

    for (JsonNode transfer : transfers) {
      String timestamp = transfer.path("timestamp").asText();

      try {
        LocalDateTime dateTime = LocalDateTime.parse(timestamp);

        // Inclui os limites inicial e final
        if (!dateTime.isBefore(start)
            && !dateTime.isAfter(end)) {
          result.add(transfer);
        }

      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(
            "Invalid transfer timestamp: " + timestamp, e);
      }
    }

    return result;
  }

  public void createTransfer(
      String source,
      String destiny,
      double amount,
      LocalDateTime timestamp) throws IOException {

    if (source == null || source.isBlank()
        || destiny == null || destiny.isBlank()) {
      throw new IllegalArgumentException(
          "Source and destiny CPFs are required.");
    }

    if (source.equals(destiny)) {
      throw new IllegalArgumentException(
          "Source and destiny must be different.");
    }

    if (!Double.isFinite(amount) || amount <= 0) {
      throw new IllegalArgumentException(
          "Transfer amount must be positive and finite.");
    }

    ArrayNode transfers = (ArrayNode) data.get("transfers");

    ObjectNode transfer = mapper.createObjectNode();
    transfer.put("uuid", UUID.randomUUID().toString());
    transfer.put("isDeleted", false);
    transfer.put("source", source);
    transfer.put("destiny", destiny);
    transfer.put("amount", amount);
    transfer.put("timestamp", timestamp.toString());

    transfers.add(transfer);

    save();
  }

  // deleta a ultima transação entre fonte e destino
  public void delete(String source, String destiny) throws IOException {
    JsonNode transfer = searchCPFToCPF(source, destiny);

    ((ObjectNode) transfer).put("isDeleted", true);

    save();
  }

  public void save() throws IOException {
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(archive.toFile(), data);
  }
}
