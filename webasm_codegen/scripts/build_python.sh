#!/usr/bin/env bash
# Python 스크립트를 WASM으로 변환하는 스크립트 (py2wasm 사용)
# 사용법: ./scripts/build_python.sh <python_파일> [출력_이름]
# 예시:   ./scripts/build_python.sh python/hello_world/hello_world.py hello_world_py.wasm
#
# 전제조건:
#   pip install py2wasm
#   (또는 wasi-python 빌드 환경 구성)

set -euo pipefail

PYTHON_FILE="${1:-}"
OUTPUT_NAME="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WASM_OUT_DIR="$SCRIPT_DIR/../wasm"

if [[ -z "$PYTHON_FILE" ]]; then
  echo "Usage: $0 <python_file> [output_name]"
  echo "Example: $0 python/hello_world/hello_world.py hello_world_py.wasm"
  exit 1
fi

if [[ ! -f "$PYTHON_FILE" ]]; then
  echo "Error: File not found: $PYTHON_FILE"
  exit 1
fi

mkdir -p "$WASM_OUT_DIR"

BASE_NAME=$(basename "$PYTHON_FILE" .py)
OUT_FILE="$WASM_OUT_DIR/${OUTPUT_NAME:-${BASE_NAME}.wasm}"

# py2wasm 사용 시도
if command -v py2wasm &>/dev/null; then
  echo "[*] Building with py2wasm: $PYTHON_FILE"
  py2wasm "$PYTHON_FILE" -o "$OUT_FILE"
  echo "[+] Output: $OUT_FILE"
else
  echo "[!] py2wasm not found. Install with: pip install py2wasm"
  echo ""
  echo "Alternative: wasi-python 방식"
  echo "  1. CPython WASM 빌드: https://github.com/brettcannon/cpython-wasi-build"
  echo "  2. walrus --wasi python.wasm -- $PYTHON_FILE"
  exit 1
fi
