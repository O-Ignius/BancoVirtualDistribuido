"""
Interface web do Banco Virtual.

O navegador fala HTTP com este processo, e este processo é o cliente gRPC
do Gateway (via banco.py).

Uso:
    BANCO_MODO=mock python app.py     # sem back-end, banco em memória
    python app.py                     # gRPC com o Gateway em localhost:50050
"""

import os
import re
import secrets
from functools import wraps

from flask import (
    Flask,
    flash,
    jsonify,
    redirect,
    render_template,
    request,
    session,
    url_for,
)

from banco import BancoIndisponivel, ErroBanco, OperacaoNaoSuportada, criar_banco

app = Flask(__name__)
app.secret_key = os.environ.get("FRONT_SEGREDO") or secrets.token_hex(16)

banco = criar_banco()


# ---------------------------- utilitários ----------------------------


def so_digitos(texto):
    return re.sub(r"\D", "", texto or "")


def formatar_cpf(cpf):
    if len(cpf) != 11:
        return cpf
    return f"{cpf[:3]}.{cpf[3:6]}.{cpf[6:9]}-{cpf[9:]}"


def formatar_reais(centavos):
    reais = (
        f"{abs(centavos) / 100:,.2f}".replace(",", "X")
        .replace(".", ",")
        .replace("X", ".")
    )
    return f"{'-' if centavos < 0 else ''}R$ {reais}"


def para_centavos(texto):
    """Converte '1.234,56' ou '1234.56' em centavos; None se inválido."""
    texto = (texto or "").strip().replace("R$", "").replace(" ", "")
    if "," in texto:
        texto = texto.replace(".", "").replace(",", ".")
    try:
        valor = round(float(texto) * 100)
    except ValueError:
        return None
    return valor if valor > 0 else None


app.jinja_env.filters["cpf"] = formatar_cpf
app.jinja_env.filters["reais"] = formatar_reais


def chamar(funcao, *args, padrao=None):
    """Executa uma operação no banco e transforma erros em mensagens na tela."""
    try:
        return funcao(*args)
    except ErroBanco as e:
        flash(str(e), "erro")
    except OperacaoNaoSuportada as e:
        flash(f"Operação ainda não disponível no servidor: {e}", "aviso")
    except BancoIndisponivel as e:
        flash(f"Servidor do banco indisponível: {e}", "erro")
    return padrao


def login_obrigatorio(view):
    @wraps(view)
    def envolvida(*args, **kwargs):
        if "cpf" not in session:
            return redirect(url_for("login"))
        return view(*args, **kwargs)

    return envolvida


def obter_montante():
    try:
        return banco.montante()
    except (OperacaoNaoSuportada, BancoIndisponivel):
        return None


@app.context_processor
def dados_do_rodape():
    return {"montante": obter_montante()}


# ------------------------------ rotas ------------------------------


@app.route("/")
def inicio():
    return redirect(url_for("painel" if "cpf" in session else "login"))


@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        cpf = so_digitos(request.form.get("cpf"))
        senha = request.form.get("senha", "")
        autenticado = chamar(banco.login, cpf, senha)
        if autenticado:
            session["cpf"] = cpf
            return redirect(url_for("painel"))
        if autenticado is False:
            flash("CPF ou senha incorretos.", "erro")
    return render_template("login.html")


@app.route("/cadastro", methods=["GET", "POST"])
def cadastro():
    if request.method == "POST":
        nome = " ".join(request.form.get("nome", "").split())
        cpf = so_digitos(request.form.get("cpf"))
        senha = request.form.get("senha", "")

        if not nome or len(cpf) != 11 or not senha:
            flash("Preencha nome, CPF (11 dígitos) e senha.", "erro")
        elif senha != request.form.get("confirmacao", ""):
            flash("As senhas não conferem.", "erro")
        elif chamar(banco.criar_conta, nome, cpf, senha):
            flash("Conta criada! Saldo inicial de R$ 1.000,00. Faça login.", "sucesso")
            return redirect(url_for("login"))
    return render_template("cadastro.html")


@app.route("/sair")
def sair():
    session.clear()
    return redirect(url_for("login"))


@app.route("/painel")
@login_obrigatorio
def painel():
    cpf_usuario_logado = session["cpf"]
    termo = request.args.get("busca", "").strip()
    destino = so_digitos(request.args.get("destino"))

    resultados = None
    if termo:
        encontrados = chamar(banco.buscar_por_nome, termo, padrao=[])
        resultados = []
        for r in encontrados:
            if r["cpf"] != cpf_usuario_logado:
                resultados.append(r)

    print(banco.consultar_saldo)
    return render_template(
        "painel.html",
        cpf=cpf_usuario_logado,
        nome=chamar(banco.nome_da_conta, cpf_usuario_logado),
        saldo=chamar(banco.consultar_saldo, cpf_usuario_logado),
        extrato=chamar(banco.extrato, cpf_usuario_logado, padrao=[]),
        termo=termo,
        resultados=resultados,
        destino=destino,
        nome_destino=chamar(banco.nome_da_conta, destino) if destino else None,
    )


@app.route("/transferir", methods=["POST"])
@login_obrigatorio
def transferir():
    destino = so_digitos(request.form.get("destino"))
    valor = para_centavos(request.form.get("valor"))

    if len(destino) != 11:
        flash("Informe o CPF da conta de destino.", "erro")
    elif valor is None:
        flash("Informe um valor válido maior que zero.", "erro")
    elif chamar(banco.transferir, session["cpf"], destino, valor):
        flash(f"Transferência de {formatar_reais(valor)} realizada.", "sucesso")
        return redirect(url_for("painel"))
    return redirect(url_for("painel", destino=destino))


@app.route("/api/montante")
def api_montante():
    """Usado pelo rodapé para atualizar a auditoria sem recarregar a página."""
    m = obter_montante()
    if m is None:
        return jsonify(None)
    return jsonify(
        {
            "total": formatar_reais(m["total_centavos"]),
            "contas": m["contas"],
            "esperado": formatar_reais(m["contas"] * 100_000),
            "consistente": m["total_centavos"] == m["contas"] * 100_000,
        }
    )


if __name__ == "__main__":
    app.run(
        host=os.environ.get("FRONT_HOST", "127.0.0.1"),
        port=int(os.environ.get("FRONT_PORTA", "5000")),
        debug=os.environ.get("FRONT_DEBUG") == "1",
    )
