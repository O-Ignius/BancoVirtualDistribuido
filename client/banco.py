"""
Duas implementações com a mesma interface:

- BancoGrpc: fala com o Gateway via gRPC (uso real / apresentação).
- BancoMock: banco em memória, para desenvolver o front sem o back rodando.

A escolha é feita pela variável de ambiente BANCO_MODO, para rodar mockado './executaFront.sh mock'
"""

import os
import threading
import uuid
from datetime import datetime

#Valores monetários são sempre inteiros em centavos.
SALDO_INICIAL_CENTAVOS = 100_000  # R$ 1.000,00


class ErroBanco(Exception):
    """Erro de regra de negócio (ex.: saldo insuficiente, conta duplicada)."""


class BancoIndisponivel(Exception):
    """Gateway fora do ar ou sem resposta."""


class OperacaoNaoSuportada(Exception):
    """O servidor ainda não oferece essa operação (falta RPC no .proto)."""


# ----------------------------------------------------------------------
# Mock: banco em memória
# ----------------------------------------------------------------------


class BancoMock:
    def __init__(self):
        self._lock = threading.Lock()
        self._contas = {}  # cpf -> {"nome", "senha", "saldo"}
        self._transferencias = []  # dicts no formato de extrato()

        # contas de exemplo para testar transferência e busca
        self.criar_conta("Alice Souza", "11111111111", "123")
        self.criar_conta("Bruno Lima", "22222222222", "123")

    def criar_conta(self, nome, cpf, senha):
        with self._lock:
            if cpf in self._contas:
                raise ErroBanco("Já existe uma conta com este CPF.")
            if any(c["nome"].casefold() == nome.casefold() for c in self._contas.values()):
                raise ErroBanco("Já existe uma conta com este nome.")
            self._contas[cpf] = {"nome": nome, "senha": senha, "saldo": SALDO_INICIAL_CENTAVOS}
            return True

    def login(self, cpf, senha):
        with self._lock:
            conta = self._contas.get(cpf)
            return conta is not None and conta["senha"] == senha

    def nome_da_conta(self, cpf):
        with self._lock:
            conta = self._contas.get(cpf)
            return conta["nome"] if conta else None

    def consultar_saldo(self, cpf):
        with self._lock:
            return self._contas[cpf]["saldo"]

    def transferir(self, origem, destino, valor_centavos):
        with self._lock:
            if origem == destino:
                raise ErroBanco("Não é possível transferir para a própria conta.")
            if destino not in self._contas:
                raise ErroBanco("Conta de destino não encontrada.")
            if valor_centavos <= 0:
                raise ErroBanco("O valor deve ser maior que zero.")
            if self._contas[origem]["saldo"] < valor_centavos:
                raise ErroBanco("Saldo insuficiente.")

            self._contas[origem]["saldo"] -= valor_centavos
            self._contas[destino]["saldo"] += valor_centavos
            self._transferencias.append({
                "id": str(uuid.uuid4()),
                "origem": origem,
                "destino": destino,
                "valor_centavos": valor_centavos,
                "data": datetime.now(),
            })
            return True

    def extrato(self, cpf):
        with self._lock:
            itens = [t for t in self._transferencias if cpf in (t["origem"], t["destino"])]
            return sorted(itens, key=lambda t: t["data"], reverse=True)

    def buscar_por_nome(self, termo):
        termo = termo.casefold()
        with self._lock:
            return [
                {"cpf": cpf, "nome": c["nome"]}
                for cpf, c in self._contas.items()
                if termo in c["nome"].casefold()
            ]

    def montante(self):
        with self._lock:
            return {
                "total_centavos": sum(c["saldo"] for c in self._contas.values()),
                "contas": len(self._contas),
            }


# ----------------------------------------------------------------------
# gRPC: comunicação real com o Gateway
# ----------------------------------------------------------------------


class BancoGrpc:
    """
    todo: ajustar quando corrigir os .proto
    """

    def __init__(self, host, porta):
        import grpc

        try:
            import bank_service_pb2 as pb
            import bank_service_pb2_grpc as pb_grpc
        except ImportError as e:
            raise RuntimeError(
                "Stubs gRPC não encontrados. Gere-os com ./compileCliente.sh "
                "ou rode com BANCO_MODO=mock."
            ) from e

        self._grpc = grpc
        self._pb = pb
        self._canal = grpc.insecure_channel(f"{host}:{porta}")
        self._stub = pb_grpc.BankServiceStub(self._canal)
        self._timeout = 5

    def _chamar(self, metodo, requisicao):
        try:
            return metodo(requisicao, timeout=self._timeout)
        except self._grpc.RpcError as e:
            if e.code() == self._grpc.StatusCode.UNIMPLEMENTED:
                raise OperacaoNaoSuportada(str(e.details())) from e
            raise BancoIndisponivel(str(e.details() or e.code())) from e

    def criar_conta(self, nome, cpf, senha):
        # assume cpf como string no CreateAccountRequest (hoje está int64 no .proto)
        resp = self._chamar(
            self._stub.createAccount,
            self._pb.CreateAccountRequest(user=nome, password=senha, cpf=cpf),
        )
        if not resp.success:
            raise ErroBanco("Não foi possível criar a conta (CPF ou nome já cadastrado?).")
        return True

    def login(self, cpf, senha):
        resp = self._chamar(self._stub.Login, self._pb.LoginRequest(id=cpf, password=senha))
        return resp.success

    def nome_da_conta(self, cpf):
        return self._chamar(self._stub.GetAccount, self._pb.GetAccountRequest(cpf=int(cpf))).owner

    def consultar_saldo(self, cpf):
        return self._chamar(self._stub.GetAccount, self._pb.GetAccountRequest(cpf=int(cpf))).balance_cents

    def transferir(self, origem, destino, valor_centavos):
        if not hasattr(self._pb, "SaveTransferRequest"):
            raise OperacaoNaoSuportada("Falta a mensagem SaveTransferRequest no bank_service.proto.")
        resp = self._chamar(
            self._stub.SaveTransfer,
            self._pb.SaveTransferRequest(
                source_cpf=int(origem),
                destination_cpf=int(destino),
                amount_cents=valor_centavos,
            ),
        )
        if not resp.success:
            raise ErroBanco("Transferência recusada.")
        return True

    def extrato(self, cpf):
        if not hasattr(self._pb, "GetAllTransferByCPFRequest"):
            raise OperacaoNaoSuportada("Falta a mensagem GetAllTransferByCPFRequest no bank_service.proto.")
        resp = self._chamar(
            self._stub.GetAllTransferByCPF,
            self._pb.GetAllTransferByCPFRequest(cpf=int(cpf)),
        )
        return [
            {
                "id": t.transfer_id,
                "origem": str(t.source),
                "destino": str(t.destiny),
                "valor_centavos": t.amount_cents,
                "data": t.created_at.ToDatetime(),
            }
            for t in resp.transfers
        ]

    def buscar_por_nome(self, termo):
        raise OperacaoNaoSuportada("Falta um RPC de busca por nome no bank_service.proto.")

    def montante(self):
        raise OperacaoNaoSuportada("Falta um RPC de montante total no bank_service.proto.")


def criar_banco():
    modo = os.environ.get("BANCO_MODO", "grpc").lower()
    if modo == "mock":
        return BancoMock()
    return BancoGrpc(
        os.environ.get("GATEWAY_HOST", "localhost"),
        int(os.environ.get("GATEWAY_PORTA", "50050")),
    )
