#!/bin/bash

# Uso:
# ./executar.sh <qtd_DataNode> [qtd_ControlNode] [compilar]

N1=${1:-1}
N2=${2:-0}
COMPILAR=${3:-n}

CLASSE1="br.com.bancoVirtualDistribuido.nodes.DataNode"
CLASSE2="br.com.bancoVirtualDistribuido.nodes.ControlNode"

export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }-Djava.net.preferIPv4Stack=true"

PIDS=()

encerrar() {
  echo
  echo "Encerrando todas as instâncias..."

  trap - INT TERM

  for pid in "${PIDS[@]}"; do
    kill -TERM -- "-$pid" 2>/dev/null ||
      kill -TERM "$pid" 2>/dev/null
  done

  for pid in "${PIDS[@]}"; do
    wait "$pid" 2>/dev/null
  done

  echo "Todas as instâncias foram encerradas."
  exit 0
}

trap encerrar INT TERM

case "$COMPILAR" in
1 | s | S | y | Y)
  echo "Compilando projeto..."
  mvn compile || exit 1
  ;;
*)
  echo "Pulando compilação."
  ;;
esac

iniciar() {
  local classe="$1"
  local nome="$2"

  echo "Iniciando $nome..."

  setsid mvn exec:java \
    -Dexec.mainClass="$classe" \
    -Dexec.args="$nome" &

  PIDS+=("$!")
}

for ((i = 1; i <= N1; i++)); do
  iniciar "$CLASSE1" "DataNode$i"
done

for ((i = 1; i <= N2; i++)); do
  iniciar "$CLASSE2" "ControlNode$i"
done

echo "Todas as instâncias foram iniciadas."
echo "Pressione Ctrl+C para encerrar."

wait
