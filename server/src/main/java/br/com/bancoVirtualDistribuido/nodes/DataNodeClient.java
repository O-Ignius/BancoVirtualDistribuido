package br.com.bancoVirtualDistribuido.nodes;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.grpc.data.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class DataNodeClient {

  private final ManagedChannel channel;
  private final BankDataServiceGrpc.BankDataServiceBlockingStub stub;

  public DataNodeClient() {
    this.channel = ManagedChannelBuilder
        .forAddress(Constants.dataServerIp, Constants.DATA_NODE_PORT)
        .usePlaintext()
        .build();

    this.stub = BankDataServiceGrpc.newBlockingStub(channel);

    System.out.println(
        "[DataNodeClient] Conectado ao DataNode em "
            + Constants.dataServerIp + " :" + Constants.DATA_NODE_PORT);
  }

  public void shutdown() {
    if (channel != null && !channel.isShutdown()) {
      channel.shutdown();
    }
  }

  // ---------- Verificação de credenciais ----------

  public boolean verifyCredentialsData(String id, String password) {

    VerifyCredentialsDataRequest request = VerifyCredentialsDataRequest.newBuilder()
        .setId(id)
        .setPassword(password)
        .build();

    VerifyCredentialsDataResponse response = stub.verifyCredentialsData(request);

    return response.getSuccess();
  }

  // ---------- Leitura de conta ----------

  public boolean readAccountData(String cpf) {

    ReadAccountDataRequest request = ReadAccountDataRequest.newBuilder()
        .setCpf(cpf)
        .build();

    ReadAccountDataResponse response = stub.readAccountData(request);

    return response.getFound();
  }

  // ---------- Salvamento de conta ----------

  public boolean saveAccountData(
      String cpf,
      String owner,
      String password,
      double balance) {

    SaveAccountDataRequest request = SaveAccountDataRequest.newBuilder()
        .setCpf(cpf)
        .setOwner(owner)
        .setPassword(password)
        .setBalanceCents((long) (balance * 100))
        .build();

    SaveAccountDataResponse response = stub.saveAccountData(request);

    return response.getSuccess();
  }

  // ---------- Salvamento de transferência ----------

  public boolean saveTransferData(
      String transferId,
      String sourceId,
      String destinyId,
      double amount) {

    SaveTransferDataRequest request = SaveTransferDataRequest.newBuilder()
        .setTransferId(transferId)
        .setSourceCpf(sourceId)
        .setDestinationCpf(destinyId)
        .setAmountCents((long) (amount * 100))
        .build();

    SaveTransferDataResponse response = stub.saveTransferData(request);

    return response.getSuccess();
  }

  // ---------- Execução de débito e crédito ----------

  public boolean executeDebitAndCreditData(
      String idSource,
      String idDestiny,
      double amount) {

    // Busca saldo da conta de origem
    ReadAccountDataRequest sourceRequest = ReadAccountDataRequest.newBuilder()
        .setCpf(idSource)
        .build();

    ReadAccountDataResponse sourceResponse = stub.readAccountData(sourceRequest);

    // Busca saldo da conta de destino
    ReadAccountDataRequest destinyRequest = ReadAccountDataRequest.newBuilder()
        .setCpf(idDestiny)
        .build();

    ReadAccountDataResponse destinyResponse = stub.readAccountData(destinyRequest);

    if (!sourceResponse.getFound() || !destinyResponse.getFound()) {
      return false;
    }

    long amountCents = (long) (amount * 100);

    // Verifica saldo disponível
    if (sourceResponse.getBalanceCents() < amountCents) {
      return false;
    }

    // Atualiza conta de origem
    SaveAccountDataRequest saveSource = SaveAccountDataRequest.newBuilder()
        .setCpf(sourceResponse.getCpf())
        .setOwner(sourceResponse.getOwner())
        .setBalanceCents(
            sourceResponse.getBalanceCents() - amountCents)
        .build();

    boolean sourceSuccess = stub.saveAccountData(saveSource).getSuccess();

    if (!sourceSuccess) {
      return false;
    }

    // Atualiza conta de destino
    SaveAccountDataRequest saveDestiny = SaveAccountDataRequest.newBuilder()
        .setCpf(destinyResponse.getCpf())
        .setOwner(destinyResponse.getOwner())
        .setBalanceCents(
            destinyResponse.getBalanceCents() + amountCents)
        .build();

    boolean destinySuccess = stub.saveAccountData(saveDestiny).getSuccess();

    return destinySuccess;
  }
}
