#!/usr/bin/env bash
# AssemblyScript 프로젝트를 WASM으로 빌드하는 스크립트
# 사용법: ./scripts/build_ts.sh <프로젝트_경로> [출력_이름]
# 예시:   ./scripts/build_ts.sh typescript/hello_world hello_world_ts.wasm

set -euo pipefail

PROJECT_DIR="${1:-}"
OUTPUT_NAME="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WASM_OUT_DIR="$SCRIPT_DIR/../wasm"

if [[ -z "$PROJECT_DIR" ]]; then
  echo "Usage: $0 <project_dir> [output_name]"
  echo "Example: $0 typescript/hello_world hello_world_ts.wasm"
  exit 1
fi

cd "$PROJECT_DIR"

# 의존성 설치
if [[ ! -d node_modules ]]; then
  echo "[*] Installing dependencies..."
  npm install
fi

# AssemblyScript 빌드
echo "[*] Building AssemblyScript project..."
mkdir -p "$WASM_OUT_DIR"

if [[ -n "$OUTPUT_NAME" ]]; then
  npx asc assembly/index.ts --target release -o "$WASM_OUT_DIR/$OUTPUT_NAME" --exportRuntime
  echo "[+] Output: $WASM_OUT_DIR/$OUTPUT_NAME"
else
  npm run build
  echo "[+] Output written to wasm/ directory"
fi
