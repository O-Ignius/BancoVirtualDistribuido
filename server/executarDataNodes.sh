#!/bin/bash

mostrar_ajuda() {
  cat <<EOF
Uso:
  $(basename "$0") [compile] [quantidade] [nome_custom]
  $(basename "$0") [quantidade] [nome_custom]

Parâmetros:
  compile       Compila o projeto com 'mvn compile' antes de iniciar (opcional)
  quantidade    Número de instâncias a executar (padrão: 1, no terminal atual; >1 abre terminais separados)
  nome_custom   Nome fixo/prefixo manual dos nós (opcional; se omitido, o Java resolve automaticamente via .locks)
  -h, --help    Exibe esta mensagem de ajuda

Exemplos:
  $(basename "$0")                 # Roda 1 instância com identidade automática no terminal atual
  $(basename "$0") 3               # Abre 3 terminais com identidade automática
  $(basename "$0") 3 meuNo         # Abre 3 terminais com nomes fixos (meuNo_1, meuNo_2, meuNo_3)
  $(basename "$0") compile         # Compila e roda 1 instância no terminal atual
  $(basename "$0") compile 2       # Compila e abre 2 terminais com identidade automática
EOF
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  mostrar_ajuda
  exit 0
fi

if [[ "$1" =~ ^[0-9]+$ ]]; then
  COMPILAR="n"
  QTD="$1"
  NOME_CUSTOM="${2:-}"
else
  COMPILAR=${1:-"n"}
  QTD=${2:-1}
  NOME_CUSTOM="${3:-}"
fi

CLASSE="br.com.bancoVirtualDistribuido.nodes.DataNode"
LOG_DIR="/tmp/banco/data"
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

if [ "$QTD" -eq 1 ]; then
  LOG_NAME="${NOME_CUSTOM:-dataNode}"
  echo "Iniciando $LOG_NAME no terminal atual (log em $LOG_DIR/$LOG_NAME.log)..."
  mvn exec:java -Dexec.mainClass="$CLASSE" -Dexec.args="$NOME_CUSTOM" 2>&1 | tee "$LOG_DIR/$LOG_NAME.log"
  exit "${PIPESTATUS[0]}"
fi

PIDS=()

encerrar() {
  echo
  echo "Encerrando todas as instâncias..."
  trap - INT TERM
  for pid in "${PIDS[@]}"; do
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
  done
  pkill -f "$CLASSE" 2>/dev/null
  echo "Todas as instâncias encerradas."
  exit 0
}

trap encerrar INT TERM

for ((i = 1; i <= QTD; i++)); do
  if [ -n "$NOME_CUSTOM" ]; then
    ARG_NOME="${NOME_CUSTOM}_${i}"
    LOG_NAME="$ARG_NOME"
  else
    ARG_NOME=""
    LOG_NAME="dataNode_${i}"
  fi

  LOG_FILE="$LOG_DIR/$LOG_NAME.log"
  echo "Abrindo terminal para $LOG_NAME (log em $LOG_FILE)..."

  setsid x-terminal-emulator -T "$LOG_NAME" -e bash -c "
    export MAVEN_OPTS=\"$MAVEN_OPTS\"
    mvn exec:java -Dexec.mainClass=\"$CLASSE\" -Dexec.args=\"$ARG_NOME\" 2>&1 | tee \"$LOG_FILE\"
    echo 'Instância $LOG_NAME finalizada. Pressione Enter para fechar...'
    read
  " >/dev/null 2>&1 &
  PIDS+=("$!")
done

echo "$QTD instâncias iniciadas em terminais separados."
echo "Logs salvos em: $LOG_DIR"
echo "Pressione Ctrl+C aqui para encerrar todas."
wait
