#!/bin/bash

mostrar_ajuda() {
  cat <<EOF
Uso:
  $(basename "$0") [compile] [mock]

Parâmetros:
  compile       Compila os stubs gRPC a partir de ../proto/bank_service.proto antes de iniciar (opcional)
  mock          Executa o front-end em modo mock sem conectar ao Gateway gRPC (opcional, padrão: grpc)
  -h, --help    Exibe esta mensagem de ajuda

Exemplos:
  $(basename "$0")               # Executa via gRPC
  $(basename "$0") mock          # Executa em modo mock
  $(basename "$0") compile       # Compila os stubs RPC e executa via gRPC
  $(basename "$0") compile mock  # Compila os stubs RPC e executa em modo mock
EOF
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  mostrar_ajuda
  exit 0
fi

cd "$(dirname "$0")" || exit 1

# Carrega o .env da pasta anterior
ENV_FILE="../.envsafe"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  source "$ENV_FILE"
  set +a
else
  echo "Erro: arquivo .env não encontrado em $ENV_FILE"
  exit 1
fi

# Seleciona o Python
PYTHON=python3
if [[ -x .venv/bin/python ]]; then
  PYTHON=.venv/bin/python
fi

echo "Verificando e instalando dependências..."
python -m pip install -r requirements.txt

if [ $? -ne 0 ]; then
  echo "Erro ao instalar as dependências."
  exit 1
fi

echo "Dependências verificadas com sucesso!"

# Compila os stubs gRPC se solicitado
if [[ "$1" == "compile" ]]; then
  echo "Compilando stubs gRPC..."

  "$PYTHON" -m grpc_tools.protoc \
    -I../proto \
    --python_out=. \
    --grpc_python_out=. \
    ../proto/bank_service.proto

  if [[ $? -ne 0 ]]; then
    echo "Erro ao compilar os stubs gRPC."
    exit 1
  fi

  shift
  echo "Compilação concluída!"
fi

# Define o modo de execução
if [[ "$1" == "mock" ]]; then
  export BANCO_MODO=mock
else
  export BANCO_MODO=grpc
fi

echo "Interface em http://${FRONT_HOST:-127.0.0.1}:${FRONT_PORTA:-5000} (modo: ${BANCO_MODO})"

exec "$PYTHON" app.py
