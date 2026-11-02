#!/bin/bash

cd "$(dirname "$0")" || exit 1

PYTHON=python3
if [[ -x .venv/bin/python ]]; then
  PYTHON=.venv/bin/python
fi

"$PYTHON" -m grpc_tools.protoc -I../proto --python_out=. --grpc_python_out=. ../proto/bank_service.proto
