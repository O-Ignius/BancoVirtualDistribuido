#!/bin/bash

# Uso:
#   ./executaFront.sh          # conecta ao Gateway via gRPC (localhost:50050)
#   ./executaFront.sh mock     # banco em memória, sem precisar do back-end
#
# Variáveis opcionais: GATEWAY_HOST, GATEWAY_PORTA, FRONT_HOST, FRONT_PORTA

cd "$(dirname "$0")" || exit 1

if [[ "$1" == "mock" ]]; then
  export BANCO_MODO=mock
fi

PYTHON=python3
if [[ -x .venv/bin/python ]]; then
  PYTHON=.venv/bin/python
fi

echo "Interface em http://${FRONT_HOST:-127.0.0.1}:${FRONT_PORTA:-5000} (modo: ${BANCO_MODO:-grpc})"
exec "$PYTHON" app.py
