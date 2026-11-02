#!/bin/bash

# Uso:
# ./executar.sh <qtd_DataNode> [qtd_ControlNode] [compilar]

N1=${1:-1}
N2=${2:-0}
COMPILAR=${3:-n}

CLASSE1="br.com.bancoVirtualDistribuido.nodes.DataNode"
CLASSE2="br.com.bancoVirtualDistribuido.nodes.ControlNode"
CLASSE3="br.com.bancoVirtualDistribuido.gateway.FrontendConnector"

# Mensagens que indicam que os servidores estão prontos
MSG_DATA="Servidor gRPC escutando o Controle na porta"
MSG_CONTROL="Conectado ao canal de Controle."

# Diretório dos logs
LOG_DIR="/tmp/banco-logs"
mkdir -p "$LOG_DIR"

# Verifica se os dois primeiros parâmetros foram informados
INICIAR_FRONTEND=false

if [[ $# -ge 2 ]]; then
  INICIAR_FRONTEND=true
fi

export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }-Djava.net.preferIPv4Stack=true"

PIDS=()

# -----------------------------
# Encerrar todas as instâncias
# -----------------------------

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

# -----------------------------
# Compilação
# -----------------------------

case "$COMPILAR" in
1 | s | S | y | Y)
  echo "Compilando projeto..."
  mvn compile || exit 1
  ;;
*)
  echo "Pulando compilação."
  ;;
esac

# -----------------------------
# Iniciar instância
# -----------------------------

iniciar() {
  local classe="$1"
  local nome="$2"

  echo "Iniciando $nome..."

  # Remove log anterior da mesma instância
  rm -f "$LOG_DIR/$nome.log"

  setsid mvn exec:java \
    -Dexec.mainClass="$classe" \
    -Dexec.args="$nome" \
    >"$LOG_DIR/$nome.log" 2>&1 &

  PIDS+=("$!")

  echo "$nome iniciado (PID: $!)."
}

# -----------------------------
# Aguardar primeira instância pronta
# -----------------------------

aguardar_pronto() {
  local mensagem="$1"

  echo "Aguardando inicialização de uma instância..."

  if timeout 60s bash -c '
        tail -n 0 -F "$1"/*.log 2>/dev/null |
        grep -m 1 -F "$2"
    ' _ "$LOG_DIR" "$mensagem"; then

    echo "Pelo menos uma instância está pronta!"
    return 0

  else
    echo "ERRO: Nenhuma instância ficou pronta em 60 segundos."
    return 1
  fi
}

# -----------------------------
# Inicialização dos DataNodes
# -----------------------------

echo
echo "===== INICIANDO DATANODES ====="

for ((i = 1; i <= N1; i++)); do
  iniciar "$CLASSE1" "DataNode$i"
done

# Aguarda pelo menos um DataNode
if ((N1 > 0)); then
  aguardar_pronto "$MSG_DATA" || exit 1
fi

# -----------------------------
# Inicialização dos ControlNodes
# -----------------------------

echo
echo "===== INICIANDO CONTROLNODES ====="

for ((i = 1; i <= N2; i++)); do
  iniciar "$CLASSE2" "ControlNode$i"
done

# Aguarda pelo menos um ControlNode
if ((N2 > 0)); then
  aguardar_pronto "$MSG_CONTROL" || exit 1
fi

# -----------------------------
# Inicialização do Frontend
# -----------------------------

if [[ "$INICIAR_FRONTEND" == true ]]; then
  echo
  echo "===== INICIANDO FRONTEND ====="

  iniciar "$CLASSE3" "FrontendConnector"
fi

# -----------------------------
# Finalização
# -----------------------------

echo
echo "Todas as instâncias foram iniciadas."
echo "Logs disponíveis em: $LOG_DIR"
echo "Pressione Ctrl+C para encerrar."

wait
