#!/bin/bash

mostrar_ajuda() {
  cat <<EOF
Uso:
  $(basename "$0") [compile] [quantidade] [nome_base]
  $(basename "$0") [quantidade] [nome_base]

Parâmetros:
  compile       Compila o projeto com 'mvn compile' antes de iniciar (opcional)
  quantidade    Número de instâncias a executar (padrão: 1, no terminal atual; >1 abre terminais separados)
  nome_base     Prefixo do nome dos nós (padrão: controlNode -> controlNode_1, controlNode_2...)
  -h, --help    Exibe esta mensagem de ajuda

Exemplos:
  $(basename "$0")                 # Roda 1 instância (controlNode) no terminal atual
  $(basename "$0") 2               # Abre 2 terminais (controlNode_1, controlNode_2)
  $(basename "$0") 2 ctrl          # Abre 2 terminais (ctrl_1, ctrl_2)
  $(basename "$0") compile         # Compila e roda 1 instância no terminal atual
  $(basename "$0") compile 3       # Compila e abre 3 terminais (controlNode_1, controlNode_2, controlNode_3)
EOF
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  mostrar_ajuda
  exit 0
fi

if [[ "$1" =~ ^[0-9]+$ ]]; then
  COMPILAR="n"
  QTD="$1"
  NOME_BASE=${2:-"controlNode"}
else
  COMPILAR=${1:-"n"}
  QTD=${2:-1}
  NOME_BASE=${3:-"controlNode"}
fi

CLASSE="br.com.bancoVirtualDistribuido.nodes.ControlNode"
LOG_DIR="/tmp/banco/control"
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
  echo "Iniciando $NOME_BASE no terminal atual (log em $LOG_DIR/$NOME_BASE.log)..."
  mvn exec:java -Dexec.mainClass="$CLASSE" -Dexec.args="$NOME_BASE" 2>&1 | tee "$LOG_DIR/$NOME_BASE.log"
  exit "${PIPESTATUS[0]}"
fi

PIDS=()

encerrar() {
  echo
  echo "Encerrando todas as instâncias de $NOME_BASE..."
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
  NOME="${NOME_BASE}_${i}"
  LOG_FILE="$LOG_DIR/$NOME.log"
  echo "Abrindo terminal para $NOME (log em $LOG_FILE)..."

  setsid x-terminal-emulator -T "$NOME" -e bash -c "
    export MAVEN_OPTS=\"$MAVEN_OPTS\"
    mvn exec:java -Dexec.mainClass=\"$CLASSE\" -Dexec.args=\"$NOME\" 2>&1 | tee \"$LOG_FILE\"
    echo 'Instância $NOME finalizada. Pressione Enter para fechar...'
    read
  " >/dev/null 2>&1 &
  PIDS+=("$!")
done

echo "$QTD instâncias iniciadas em terminais separados."
echo "Logs salvos em: $LOG_DIR"
echo "Pressione Ctrl+C aqui para encerrar todas."
wait
