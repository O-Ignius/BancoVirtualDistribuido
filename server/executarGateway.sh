#!/bin/bash

mostrar_ajuda() {
  cat <<EOF
Uso:
  $(basename "$0") [compile]

Parâmetros:
  compile       Compila o projeto com 'mvn compile' antes de iniciar o Gateway (opcional)
  -h, --help    Exibe esta mensagem de ajuda

Exemplos:
  $(basename "$0")           # Inicia o Gateway no terminal atual sem compilar
  $(basename "$0") compile   # Compila o projeto e inicia o Gateway no terminal atual
EOF
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  mostrar_ajuda
  exit 0
fi

COMPILAR=${1:-"n"}
CLASSE="br.com.bancoVirtualDistribuido.gateway.FrontendConnector"
LOG_DIR="/tmp/banco/gateway"
mkdir -p "$LOG_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }-Djava.net.preferIPv4Stack=true"

case "$COMPILAR" in
s | S | y | Y | compile | COMPILE)
  echo "Compilando projeto..."
  mvn compile || exit 1
  ;;
esac

echo "Iniciando Gateway no terminal atual (log em $LOG_DIR/gateway.log)..."
mvn exec:java -Dexec.mainClass="$CLASSE" 2>&1 | tee "$LOG_DIR/gateway.log"
exit "${PIPESTATUS[0]}"
