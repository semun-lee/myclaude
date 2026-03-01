#!/usr/bin/env bash
# 빌드된 .wasm 파일을 walrus 또는 wasmtime으로 실행하는 스크립트
# 사용법: ./scripts/run_wasm.sh <wasm_파일> [인자...]
# 예시:   ./scripts/run_wasm.sh wasm/hello_world.wasm
#          ./scripts/run_wasm.sh wasm/hello_world.wasm -- World

set -euo pipefail

WASM_FILE="${1:-}"

if [[ -z "$WASM_FILE" ]]; then
  echo "Usage: $0 <wasm_file> [-- args...]"
  echo "Example: $0 wasm/hello_world.wasm -- World"
  exit 1
fi

if [[ ! -f "$WASM_FILE" ]]; then
  echo "Error: WASM file not found: $WASM_FILE"
  exit 1
fi

shift  # 첫 번째 인자(wasm 파일) 제거, 나머지는 wasm 프로그램 인자

# 런타임 우선순위: walrus → wasmtime → wasmer
if command -v walrus &>/dev/null; then
  echo "[*] Running with walrus: $WASM_FILE"
  walrus --wasi "$WASM_FILE" "$@"
elif command -v wasmtime &>/dev/null; then
  echo "[*] walrus not found, falling back to wasmtime"
  wasmtime "$WASM_FILE" "$@"
elif command -v wasmer &>/dev/null; then
  echo "[*] Running with wasmer: $WASM_FILE"
  wasmer run --wasi "$WASM_FILE" -- "$@"
else
  echo "[!] No WASM runtime found. Install one of:"
  echo "  - walrus (Tizen): https://github.com/Samsung/walrus"
  echo "  - wasmtime: https://wasmtime.dev"
  echo "  - wasmer: https://wasmer.io"
  exit 1
fi
