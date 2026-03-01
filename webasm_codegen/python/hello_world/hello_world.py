#!/usr/bin/env python3
# Python → WASM 예제
# 빌드 방법 1 (py2wasm): py2wasm hello_world.py -o ../../wasm/hello_world_py.wasm
# 빌드 방법 2 (wasi-python): CPython WASM 빌드 후 스크립트 실행
# 실행: walrus --wasi ../../wasm/hello_world_py.wasm

import sys
import os

def main():
    args = sys.argv[1:]

    if args:
        print(f"Hello, {args[0]}!")
    else:
        print("Hello, WASM world from Python!")

    # 환경변수 접근 (WASI 지원 시)
    wasm_env = os.environ.get("WASM_ENV", "")
    if wasm_env:
        print(f"WASM_ENV = {wasm_env}")

if __name__ == "__main__":
    main()
