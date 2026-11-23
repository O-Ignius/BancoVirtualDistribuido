package br.com.bancoVirtualDistribuido.gateway;

import br.com.bancoVirtualDistribuido.grpc.service.*;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.function.Function;

public class RequestProcessor extends BankServiceGrpc.BankServiceImplBase {

  private final FrontendConnector connector;

  public RequestProcessor(FrontendConnector connector) {
    this.connector = connector;
  }

  private <T> T chamarControle(Function<BankServiceGrpc.BankServiceBlockingStub, T> call) throws Exception {
    try {
      return call.apply(connector.getControlStub());
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        System.err.println("[Gateway] ControlNode indisponível. Buscando novo coordenador...");
        connector.connectToControl();
        return call.apply(connector.getControlStub());
      }
      throw e;
    }
  }

  @Override
  public void searchAccountsByNameService(SearchAccountsByNameServiceRequest request,
      StreamObserver<SearchAccountsByNameServiceResponse> responseObserver) {
    try {
      SearchAccountsByNameServiceResponse response = chamarControle(stub -> stub.searchAccountsByNameService(request));
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void getTotalBankMoneyService(Empty request,
      StreamObserver<GetTotalBankMoneyServiceResponse> responseObserver) {
    try {
      GetTotalBankMoneyServiceResponse response = chamarControle(stub -> stub.getTotalBankMoneyService(request));
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void loginService(LoginServiceRequest request,
      StreamObserver<LoginServiceResponse> responseObserver) {
    try {
      LoginServiceResponse response = chamarControle(stub -> stub.loginService(request));
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void getAccountService(GetAccountServiceRequest request,
      StreamObserver<GetAccountServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido GetAccountService recebido. Repassando ao Controle...");
    try {
      GetAccountServiceResponse response = chamarControle(stub -> stub.getAccountService(request));
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void saveTransferService(SaveTransferServiceRequest request,
      StreamObserver<SaveTransferServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido SaveTransferService recebido. Repassando ao Controle...");
    try {
      SaveTransferServiceResponse response = chamarControle(stub -> stub.saveTransferService(request));
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

  @Override
  public void getTransferService(GetTransferServiceRequest request,
      StreamObserver<GetTransferServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido GetTransferService recebido. Repassando ao Controle...");
    try {
      GetTransferServiceResponse response = chamarControle(stub -> stub.getTransferService(request));
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      System.err.println("[Gateway] Erro ao conectar com Controle: " + e.getMessage());
      responseObserver.onError(Status.UNAVAILABLE
          .withDescription("Serviço de Controle indisponível.")
          .asRuntimeException());
    }
  }

  @Override
  public void createAccountService(CreateAccountServiceRequest request,
      StreamObserver<CreateAccountServiceResponse> responseObserver) {
    System.out.println("[Gateway] Pedido CreateAccountService recebido. Repassando ao Controle...");
    try {
      CreateAccountServiceResponse response = chamarControle(stub -> stub.createAccountService(request));
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
