import grpc

import bank_service_pb2
import bank_service_pb2_grpc

IPSERVER = "localhost"
PORTSERVER = 50050

CPF = "00000000000"
USER = "Usuario"
PASSWORD = "abc123"


def createAccount(channel, user, password, cpf):
    servidor = bank_service_pb2_grpc.BankServiceStub(channel)

    resposta = servidor.CreateAccountService(
        bank_service_pb2.CreateAccountServiceRequest(
            user=user, password=password, cpf=cpf
        )
    )

    if resposta.success:
        print("Conta criada com sucesso!")
    else:
        print("Não foi possível criar a conta!")


def login(channel, password, id):
    servidor = bank_service_pb2_grpc.BankServiceStub(channel)

    resposta = servidor.LoginService(
        bank_service_pb2.LoginServiceRequest(password=password, id=id)
    )

    if resposta.success:
        print("Conta criada com sucesso!")
    else:
        print("Não foi possível criar a conta!")


if __name__ == "__main__":

    with grpc.insecure_channel(f"{IPSERVER}:{PORTSERVER}") as channel:

        while True:
            print("\n===== MENU PRINCIPAL =====")
            print("1 - cria conta")
            print("2 - Login ")

            opcao = input("Escolha uma opção: ")

            if opcao == "0":
                print("Programa encerrado!")
                break

            elif opcao == "1":
                createAccount(channel, USER, PASSWORD, CPF)

            elif opcao == "2":
                login(channel, PASSWORD, CPF)

            elif opcao == "3":
                print("Você escolheu a opção 3!")

            elif opcao == "4":
                print("Você escolheu a opção 4!")

            else:
                print("Opção inválida! Tente novamente.")
