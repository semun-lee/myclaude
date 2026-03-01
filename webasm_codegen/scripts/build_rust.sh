#!/usr/bin/env bash
# Rust 프로젝트를 wasm32-wasip1 타겟으로 빌드하는 스크립트
# (wasm32-wasi는 Rust 1.78+에서 wasm32-wasip1로 이름 변경됨)
# 사용법: ./scripts/build_rust.sh <프로젝트_경로> [출력_이름]
# 예시:   ./scripts/build_rust.sh rust/hello_world hello_world

set -euo pipefail

PROJECT_DIR="${1:-}"
OUTPUT_NAME="${2:-}"
WASM_OUT_DIR="$(dirname "$0")/../wasm"
TARGET="wasm32-wasip1"

if [[ -z "$PROJECT_DIR" ]]; then
  echo "Usage: $0 <project_dir> [output_name]"
  echo "Example: $0 rust/hello_world hello_world"
  exit 1
fi

# wasm32-wasip1 타겟 설치 확인
if ! rustup target list --installed | grep -q "$TARGET"; then
  echo "[*] Installing $TARGET target..."
  rustup target add "$TARGET"
fi

# 프로젝트 디렉토리로 이동하여 빌드
cd "$PROJECT_DIR"
echo "[*] Building $PROJECT_DIR for $TARGET..."
cargo build --target "$TARGET" --release

# 빌드 결과물을 wasm/ 디렉토리로 복사
mkdir -p "$WASM_OUT_DIR"
WASM_FILES=(target/"$TARGET"/release/*.wasm)

for wasm_file in "${WASM_FILES[@]}"; do
  if [[ -f "$wasm_file" ]]; then
    base=$(basename "$wasm_file")
    dest="$WASM_OUT_DIR/${OUTPUT_NAME:-$base}"
    cp "$wasm_file" "$dest"
    echo "[+] Output: $dest"
  fi
done
