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
  public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
    try {
      LoginResponse response = controlStub.login(request);
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
  public void saveTransfer(SaveTransferRequest request, StreamObserver<SaveTransferResponse> responseObserver) {
    System.out.println("[Gateway] Pedido SaveTransfer recebido. Repassando ao Controle...");
    try {
      SaveTransferResponse response = controlStub.saveTransfer(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      SaveTransferResponse fallback = SaveTransferResponse.newBuilder()
          .setSuccess(false)
          .build();
      responseObserver.onNext(fallback);
      responseObserver.onCompleted();
    }
  }

  /*
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
  */

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
