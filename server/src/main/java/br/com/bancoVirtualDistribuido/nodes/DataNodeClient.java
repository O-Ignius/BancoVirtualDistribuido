package br.com.bancoVirtualDistribuido.nodes;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.grpc.data.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class DataNodeClient {

  private final ManagedChannel channel;
  private final BankDataServiceGrpc.BankDataServiceBlockingStub stub;

  public DataNodeClient() {
    this.channel = ManagedChannelBuilder.forAddress(Constants.dataServerIp, Constants.DATA_NODE_PORT)
        .usePlaintext()
        .build();
    this.stub = BankDataServiceGrpc.newBlockingStub(channel);
    System.out.println(
        "[DataNodeClient] Conectado ao DataNode em " + Constants.dataServerIp + " :" + Constants.DATA_NODE_PORT);
  }

  public void shutdown() {
    if (channel != null && !channel.isShutdown()) {
      channel.shutdown();
    }
  }

  public boolean verifyCredentials(String id, String password) {
    VerifyCredentialsRequest request = VerifyCredentialsRequest.newBuilder()
        .setId(id)
        .setPassword(password)
        .build();
    VerifyCredentialsResponse response = stub.verifyCredentials(request);
    return response.getSuccess();
  }

  public boolean readAccount(String cpf) {
    ReadAccountRequest request = ReadAccountRequest.newBuilder()
        .setCpf(Long.parseLong(cpf))
        .build();
    ReadAccountDataResponse response = stub.readAccountData(request);
    return response.getFound();
  }

  public boolean saveAccount(String cpf, String owner, String password, double balance) {
    SaveAccountRequest request = SaveAccountRequest.newBuilder()
        .setCpf(Long.parseLong(cpf))
        .setOwner(owner)
        .setPassword(password)
        .setBalanceCents((long) (balance * 100))
        .build();
    SaveAccountDataResponse response = stub.saveAccountData(request);
    return response.getSuccess();
  }

  public boolean saveTransfer(String transferId, String sourceId, String destinyId, double amount) {
    SaveTransferDataRequest request = SaveTransferDataRequest.newBuilder()
        .setTransferId(transferId)
        .setSourceCpf(Long.parseLong(sourceId))
        .setDestinationCpf(Long.parseLong(destinyId))
        .setAmountCents((long) (amount * 100))
        .build();
    SaveTransferDataResponse response = stub.saveTransferData(request);
    return response.getSuccess();
  }

  public boolean executeDebitAndCredit(String idSource, String idDestiny, double amount) {
    // busca saldo das contas
    ReadAccountResponse sourceResponse = stub.readAccount(ReadAccountRequest.newBuilder()
        .setCpf(Long.parseLong(idSource)).build());
    ReadAccountResponse destinyResponse = stub.readAccount(ReadAccountRequest.newBuilder()
        .setCpf(Long.parseLong(idDestiny)).build());

    if (!sourceResponse.getFound() || !destinyResponse.getFound()) {
      return false;
    }

    long amountCents = (long) (amount * 100);

    // saldo disponivel menor que valor de transferencia
    if (sourceResponse.getBalanceCents() < amountCents) {
      return false;
    }

    // atualiza origem
    SaveAccountRequest saveSource = SaveAccountRequest.newBuilder()
        .setCpf(sourceResponse.getCpf())
        .setOwner(sourceResponse.getOwner())
        .setBalanceCents(sourceResponse.getBalanceCents() - amountCents)
        .build();
    boolean sourceSuccess = stub.saveAccountData(saveSource).getSuccess();

    // atualiza destino
    SaveAccountRequest saveDestiny = SaveAccountRequest.newBuilder()
        .setCpf(destinyResponse.getCpf())
        .setOwner(destinyResponse.getOwner())
        .setBalanceCents(destinyResponse.getBalanceCents() + amountCents)
        .build();
    boolean destinySuccess = stub.saveAccountData(saveDestiny).getSuccess();

    return sourceSuccess && destinySuccess;
  }
}
