package br.com.bancoVirtualDistribuido.gateway;

import br.com.bancoVirtualDistribuido.grpc.*;
import io.grpc.stub.StreamObserver;

public class RequestProcessor extends BankServiceGrpc.BankServiceImplBase {

  // Stub bloqueante para comunicação Gateway -> Controle
  private final BankServiceGrpc.BankServiceBlockingStub controlStub;

  public RequestProcessor(BankServiceGrpc.BankServiceBlockingStub controlStub) {
    this.controlStub = controlStub;
  }

  @Override
  public void getAccount(GetAccountRequest request, StreamObserver<GetAccountResponse> responseObserver) {
    System.out.println("[Gateway] Pedido GetAccount recebido. Repassando ao Controle...");
    try {
      GetAccountResponse response = controlStub.getAccount(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(io.grpc.Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
    System.out.println("[Gateway] Pedido Transfer recebido. Repassando ao Controle...");
    try {
      TransferResponse response = controlStub.transfer(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      TransferResponse fallback = TransferResponse.newBuilder()
          .setSuccess(false)
          .setMessage("Erro no Gateway: Não foi possível contatar o Controle.")
          .build();
      responseObserver.onNext(fallback);
      responseObserver.onCompleted();
    }
  }

  @Override
  public void getTransfer(GetTransferRequest request, StreamObserver<GetTransferResponse> responseObserver) {
    System.out.println("[Gateway] Pedido GetTransfer recebido. Repassando ao Controle...");
    try {
      GetTransferResponse response = controlStub.getTransfer(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(io.grpc.Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
    System.out.println("[Gateway] Pedido CreateAccount recebido. Repassando ao Controle...");
    try {
      CreateAccountResponse response = controlStub.createAccount(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      CreateAccountResponse fallback = CreateAccountResponse.newBuilder()
          .setSuccess(false)
          .build();
      responseObserver.onNext(fallback);
      responseObserver.onCompleted();
    }
  }
}
