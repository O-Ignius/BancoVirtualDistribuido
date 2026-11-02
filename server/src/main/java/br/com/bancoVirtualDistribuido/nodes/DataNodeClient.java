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

  public boolean readAccount(String accountId) {
    ReadAccountDataRequest request = ReadAccountDataRequest.newBuilder()
        .setAccountId(Long.parseLong(accountId))
        .build();
    ReadAccountDataResponse response = stub.readAccountData(request);
    return response.getFound();
  }

  public boolean saveAccount(String accountId, String owner, double balance) {
    SaveAccountDataRequest request = SaveAccountDataRequest.newBuilder()
        .setAccountId(Long.parseLong(accountId))
        .setOwner(owner)
        .setBalanceCents((long) (balance * 100))
        .build();
    SaveAccountDataResponse response = stub.saveAccountData(request);
    return response.getSuccess();
  }

  public boolean saveTransfer(String transferId, String sourceId, String destinyId, double amount) {
    SaveTransferDataRequest request = SaveTransferDataRequest.newBuilder()
        .setTransferId(transferId)
        .setSourceAccountId(Long.parseLong(sourceId))
        .setDestinationAccountId(Long.parseLong(destinyId))
        .setAmountCents((long) (amount * 100))
        .build();
    SaveTransferDataResponse response = stub.saveTransferData(request);
    return response.getSuccess();
  }

  public boolean executeDebitAndCredit(String idSource, String idDestiny, double amount) {
    // busca saldo das contas
    ReadAccountDataResponse sourceResponse = stub.readAccountData(ReadAccountDataRequest.newBuilder()
        .setAccountId(Long.parseLong(idSource)).build());
    ReadAccountDataResponse destinyResponse = stub.readAccountData(ReadAccountDataRequest.newBuilder()
        .setAccountId(Long.parseLong(idDestiny)).build());

    if (!sourceResponse.getFound() || !destinyResponse.getFound()) {
      return false;
    }

    long amountCents = (long) (amount * 100);

    // saldo disponivel menor que valor de transferencia
    if (sourceResponse.getBalanceCents() < amountCents) {
      return false;
    }

    // atualiza origem
    SaveAccountDataRequest saveSource = SaveAccountDataRequest.newBuilder()
        .setAccountId(sourceResponse.getAccountId())
        .setOwner(sourceResponse.getOwner())
        .setBalanceCents(sourceResponse.getBalanceCents() - amountCents)
        .build();
    boolean sourceSuccess = stub.saveAccountData(saveSource).getSuccess();

    // atualiza destino
    SaveAccountDataRequest saveDestiny = SaveAccountDataRequest.newBuilder()
        .setAccountId(destinyResponse.getAccountId())
        .setOwner(destinyResponse.getOwner())
        .setBalanceCents(destinyResponse.getBalanceCents() + amountCents)
        .build();
    boolean destinySuccess = stub.saveAccountData(saveDestiny).getSuccess();

    return sourceSuccess && destinySuccess;
  }
}
