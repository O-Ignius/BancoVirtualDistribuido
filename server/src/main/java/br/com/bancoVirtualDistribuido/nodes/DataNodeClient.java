package br.com.bancoVirtualDistribuido.nodes;

import br.com.bancoVirtualDistribuido.config.Constants;
import br.com.bancoVirtualDistribuido.config.ServiceDiscovery;
import br.com.bancoVirtualDistribuido.grpc.data.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import com.google.protobuf.Empty;

public class DataNodeClient {

  private ManagedChannel channel;
  private BankDataServiceGrpc.BankDataServiceBlockingStub stub;

  public DataNodeClient() throws Exception {
    reconnect();
  }

  public synchronized void reconnect() throws Exception {
    if (channel != null && !channel.isShutdown()) {
      channel.shutdownNow();
    }

    System.out.println("[DataNodeClient] Buscando DataNode coordenador via Multicast...");
    String[] endpoint = ServiceDiscovery.discover("DATA");
    String host = endpoint[0];
    int port = Integer.parseInt(endpoint[1]);

    this.channel = ManagedChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build();

    this.stub = BankDataServiceGrpc.newBlockingStub(channel);

    System.out.println("[DataNodeClient] Conectado ao DataNode em " + host + ":" + port);
  }

  private <T> T callWithFailover(java.util.function.Supplier<T> rpcCall) {
    try {
      return rpcCall.get();
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
        System.err.println("[DataNodeClient] DataNode indisponível. Redescobrindo novo coordenador...");
        try {
          reconnect();
          return rpcCall.get();
        } catch (Exception ex) {
          throw new RuntimeException("Falha ao reconectar ao novo DataNode coordenador", ex);
        }
      }
      throw e;
    }
  }

  public void shutdown() {
    if (channel != null && !channel.isShutdown()) {
      channel.shutdown();
    }
  }

  // pesquisa por nome e retorna as contas
  public ReadAccountsByNameDataResponse readAccountsByNameData(String name) {
    ReadAccountsByNameDataRequest request = ReadAccountsByNameDataRequest.newBuilder()
        .setName(name)
        .build();

    return callWithFailover(() -> stub.readAccountsByNameData(request));
  }

  // ---------- Pega todo valor do banco e retorna em R$ ou seja lá qual moeda
  // ----------
  public long readTotalBankMoneyData() {
    Empty request = Empty.getDefaultInstance();

    ReadTotalBankMoneyDataResponse response = callWithFailover(() -> stub.readTotalBankMoneyData(request));

    return response.getTotalMoney();
  }

  // ---------- Verificação de credenciais ----------

  public boolean verifyCredentialsData(String id, String password) {

    VerifyCredentialsDataRequest request = VerifyCredentialsDataRequest.newBuilder()
        .setId(id)
        .setPassword(password)
        .build();

    VerifyCredentialsDataResponse response = callWithFailover(() -> stub.verifyCredentialsData(request));

    return response.getSuccess();
  }

  // ---------- Leitura de conta ----------

  public ReadAccountDataResponse readAccountData(String cpf) {

    ReadAccountDataRequest request = ReadAccountDataRequest.newBuilder()
        .setCpf(cpf)
        .build();

    return callWithFailover(() -> stub.readAccountData(request));
  }

  // ---------- Leitura de transferencias ----------
  public GetTransferDataResponse readTransferData(String cpf) {

    GetTransferDataRequest request = GetTransferDataRequest.newBuilder()
        .setCpf(cpf)
        .build();

    return callWithFailover(() -> stub.getTransferData(request));
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
        .setBalanceCents(Math.round(balance * 100))
        .build();

    SaveAccountDataResponse response = callWithFailover(() -> stub.saveAccountData(request));

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

    SaveTransferDataResponse response = callWithFailover(() -> stub.saveTransferData(request));

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

    ReadAccountDataResponse sourceResponse = callWithFailover(() -> stub.readAccountData(sourceRequest));

    // Busca saldo da conta de destino
    ReadAccountDataRequest destinyRequest = ReadAccountDataRequest.newBuilder()
        .setCpf(idDestiny)
        .build();

    ReadAccountDataResponse destinyResponse = callWithFailover(() -> stub.readAccountData(destinyRequest));

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

    boolean sourceSuccess = callWithFailover(() -> stub.saveAccountData(saveSource).getSuccess());

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

    boolean destinySuccess = callWithFailover(() -> stub.saveAccountData(saveDestiny)).getSuccess();

    // se falhar o destino, estorna o valor
    if (!destinySuccess) {
      SaveAccountDataRequest estorno = SaveAccountDataRequest.newBuilder()
          .setCpf(sourceResponse.getCpf())
          .setOwner(sourceResponse.getOwner())
          .setBalanceCents(sourceResponse.getBalanceCents()) // saldo original antes do débito
          .build();

      callWithFailover(() -> stub.saveAccountData(estorno));
      return false;
    }

    return destinySuccess;
  }
}
