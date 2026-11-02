import grpc

import bank_service_pb2
import bank_service_pb2_grpc

IPSERVER = "localhost"
PORTSERVER = 50050

CPF = "00000000000"
USER = "Usuario"
PASSWORD = "abc123"


def run():
    with grpc.insecure_channel(f"{IPSERVER}:{PORTSERVER}") as channel:

        servidor = bank_service_pb2_grpc.BankServiceStub(channel)

        resposta = servidor.createAccount(
            bank_service_pb2.CreateAccountRequest(user=USER, passw=PASSWORD, cpf=CPF)
        )

        if resposta.success:
            print("Conta criada com sucesso!")
        else:
            print("Não foi possível criar a conta!")


if __name__ == "__main__":
    run()


# while True:
#     print("\n===== MENU PRINCIPAL =====")
#     print("1 - cria conta")
#     print("2 - Opção 2")
#     print("3 - Opção 3")
#     print("4 - Opção 4")
#     print("0 - Encerrar")
#
#     opcao = input("Escolha uma opção: ")
#
#     if opcao == "0":
#         print("Programa encerrado!")
#         break
#
#     elif opcao == "1":
#
#
#     elif opcao == "2":
#         print("Você escolheu a opção 2!")
#
#     elif opcao == "3":
#         print("Você escolheu a opção 3!")
#
#     elif opcao == "4":
#         print("Você escolheu a opção 4!")
#
#     else:
#         print("Opção inválida! Tente novamente.")
