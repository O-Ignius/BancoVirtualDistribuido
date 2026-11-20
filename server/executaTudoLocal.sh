#!/bin/bash

mostrar_ajuda() {
  cat <<EOF
Uso:
  $(basename "$0") [compile] [qtd_DataNode] [qtd_ControlNode]
  $(basename "$0") [qtd_DataNode] [qtd_ControlNode]

Parâmetros:
  compile           Compila o projeto com 'mvn compile' antes de subir os nós (opcional)
  qtd_DataNode      Quantidade de DataNodes a iniciar (padrão: 1)
  qtd_ControlNode   Quantidade de ControlNodes a iniciar (padrão: 1)
  -h, --help        Exibe esta mensagem de ajuda

Exemplos:
  $(basename "$0")                 # Sobe 1 DataNode, 1 ControlNode e o Gateway
  $(basename "$0") 3 2             # Sobe 3 DataNodes, 2 ControlNodes e o Gateway
  $(basename "$0") compile         # Compila e sobe 1 DataNode, 1 ControlNode e o Gateway
  $(basename "$0") compile 3 2     # Compila e sobe 3 DataNodes, 2 ControlNodes e o Gateway
EOF
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  mostrar_ajuda
  exit 0
fi

# Interpreta parâmetros com ou sem 'compile' na frente
if [[ "$1" =~ ^[0-9]+$ ]]; then
  COMPILAR="n"
  N_DATA="$1"
  N_CONTROL=${2:-1}
else
  COMPILAR=${1:-"n"}
  N_DATA=${2:-1}
  N_CONTROL=${3:-1}
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Diretórios de logs usados pelos scripts filhos
LOG_DATA="/tmp/banco/data"
LOG_CONTROL="/tmp/banco/control"
LOG_GATEWAY="/tmp/banco/gateway"
mkdir -p "$LOG_DATA" "$LOG_CONTROL" "$LOG_GATEWAY"

# Mensagens que indicam que cada camada está pronta
MSG_DATA="Servidor gRPC escutando na porta"
MSG_CONTROL="Conectado ao canal de Controle."

PIDS=()

encerrar() {
  echo
  echo "Encerrando toda a topologia (DataNodes, ControlNodes e Gateway)..."
  trap - INT TERM

  for pid in "${PIDS[@]}"; do
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
  done

  pkill -f "br.com.bancoVirtualDistribuido" 2>/dev/null
  echo "Todas as instâncias foram encerradas."
  exit 0
}

trap encerrar INT TERM

# Compilação única antes de iniciar todas as camadas
case "$COMPILAR" in
1 | s | S | y | Y | compile | COMPILE)
  echo "Compilando projeto..."
  mvn compile || exit 1
  ;;
esac

aguardar_pronto() {
  local dir_log="$1"
  local mensagem="$2"
  local tentativas=0
  local max_tentativas=120 # 60 segundos (0.5s cada)

  echo "Aguardando prontidão em $dir_log..."
  while ((tentativas < max_tentativas)); do
    if grep -q -F "$mensagem" "$dir_log"/*.log 2>/dev/null; then
      echo "Pronto!"
      return 0
    fi
    sleep 0.5
    ((tentativas++))
  done

  echo "ERRO: Timeout de 60s aguardando: \"$mensagem\""
  echo "Últimas linhas do log:"
  tail -n 15 "$dir_log"/*.log 2>/dev/null
  return 1
}

# 1. DataNodes
if ((N_DATA > 0)); then
  echo
  echo "===== INICIANDO DATANODES ($N_DATA) ====="
  rm -f "$LOG_DATA"/*.log
  setsid "$SCRIPT_DIR/executarDataNodes.sh" "$N_DATA" >/dev/null 2>&1 &
  PIDS+=("$!")
  aguardar_pronto "$LOG_DATA" "$MSG_DATA" || encerrar
fi

# 2. ControlNodes
if ((N_CONTROL > 0)); then
  echo
  echo "===== INICIANDO CONTROLNODES ($N_CONTROL) ====="
  rm -f "$LOG_CONTROL"/*.log
  setsid "$SCRIPT_DIR/executarControlNodes.sh" "$N_CONTROL" >/dev/null 2>&1 &
  PIDS+=("$!")
  aguardar_pronto "$LOG_CONTROL" "$MSG_CONTROL" || encerrar
fi

# 3. Gateway
echo
echo "===== INICIANDO GATEWAY ====="
rm -f "$LOG_GATEWAY"/*.log
setsid "$SCRIPT_DIR/executarGateway.sh" &
PIDS+=("$!")

echo
echo "Topologia completa no ar!"
echo "Logs em: /tmp/banco/{data,control,gateway}"
echo "Pressione Ctrl+C para encerrar tudo."

wait
