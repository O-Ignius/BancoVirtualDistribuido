package br.com.bancoVirtualDistribuido.gateway;

import br.com.bancoVirtualDistribuido.grpc.service.*;
import io.grpc.stub.StreamObserver;

public class RequestProcessor extends BankServiceGrpc.BankServiceImplBase {

  // Stub bloqueante para comunicação Gateway -> Controle
  private final BankServiceGrpc.BankServiceBlockingStub controlStub;

  public RequestProcessor(BankServiceGrpc.BankServiceBlockingStub controlStub) {
    this.controlStub = controlStub;
  }

  // @Override
  // public void loginService(LoginServiceRequest request,
  // StreamObserver<LoginServiceResponse> responseObserver) {
  // try {
  // LoginServiceResponse response = controlStub.loginService(request);
  // responseObserver.onNext(response);
  // responseObserver.onCompleted();
  // } catch (Exception e) {
  // System.err.println("[Gateway] Erro ao conectar com Controle: " +
  // e.getMessage());
  // responseObserver.onError(io.grpc.Status.UNAVAILABLE
  // .withDescription("Serviço de Controle indisponível.")
  // .asRuntimeException());
  // }
  // }

  @Override
  public void getAccountService(GetAccountServiceRequest request,
      StreamObserver<GetAccountServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido GetAccountService recebido. Repassando ao Controle...");
    try {
      GetAccountServiceResponse response = controlStub.getAccountService(request);
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
  public void saveTransferService(SaveTransferServiceRequest request,
      StreamObserver<SaveTransferServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido SaveTransferService recebido. Repassando ao Controle...");
    try {
      SaveTransferServiceResponse response = controlStub.saveTransferService(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      SaveTransferServiceResponse fallback = SaveTransferServiceResponse.newBuilder()
          .setSuccess(false)
          .build();
      responseObserver.onNext(fallback);
      responseObserver.onCompleted();
    }
  }

  /*
   * @Override
   * public void getTransferService(GetTransferServiceRequest request,
   * StreamObserver<GetTransferServiceResponse> responseObserver) {
   * System.out.
   * println("[Gateway] Pedido GetTransferService recebido. Repassando ao Controle..."
   * );
   * try {
   * GetTransferServiceResponse response =
   * controlStub.getTransferService(request);
   * responseObserver.onNext(response);
   * responseObserver.onCompleted();
   * } catch (Exception e) {
   * System.err.println("[Gateway] Erro ao conectar com Controle: " +
   * e.getMessage());
   * responseObserver.onError(io.grpc.Status.UNAVAILABLE
   * .withDescription("Serviço de Controle indisponível.")
   * .asRuntimeException());
   * }
   * }
   */

  @Override
  public void createAccountService(CreateAccountServiceRequest request,
      StreamObserver<CreateAccountServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido CreateAccountService recebido. Repassando ao Controle...");
    try {
      CreateAccountServiceResponse response = controlStub.createAccountService(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      CreateAccountServiceResponse fallback = CreateAccountServiceResponse.newBuilder()
          .setSuccess(false)
          .build();
      responseObserver.onNext(fallback);
      responseObserver.onCompleted();
    }
  }
}
