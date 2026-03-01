# webasm_codegen

Rust, TypeScript, Python 등 다양한 언어로 작성된 코드를
**WASI (WebAssembly System Interface)** 기반의 `.wasm` 바이너리로 변환하는 실험 공간입니다.

타겟 런타임: **Tizen walrus** (WASI-compliant WebAssembly runtime)

---

## 디렉토리 구조

```
webasm_codegen/
├── rust/           # Rust → wasm32-wasi 실험
├── typescript/     # AssemblyScript → WASM 실험
├── python/         # Python → WASM 실험 (py2wasm / wasi-python)
├── scripts/        # 언어별 빌드 스크립트
└── wasm/           # 컴파일된 .wasm 출력 파일
```

---

## 언어별 툴체인

### Rust → WASM (wasm32-wasi)
```bash
# 타겟 설치
rustup target add wasm32-wasi

# 빌드
cargo build --target wasm32-wasi --release

# 실행 (walrus 또는 wasmtime)
wasmtime target/wasm32-wasi/release/<name>.wasm
```

### TypeScript → WASM (AssemblyScript)
AssemblyScript는 TypeScript의 문법을 사용하면서 WASM으로 직접 컴파일됩니다.
```bash
# 설치
npm install --save-dev assemblyscript

# 빌드
npx asc assembly/index.ts --target release -o output.wasm

# 실행
wasmtime output.wasm
```

### Python → WASM (py2wasm / wasi-python)
Python은 두 가지 접근 방식이 있습니다:
- **py2wasm**: Nuitka 기반으로 Python → WASM 직접 변환
- **wasi-python**: CPython 자체를 WASM으로 빌드하여 Python 스크립트 실행
```bash
# py2wasm 사용
pip install py2wasm
py2wasm script.py -o output.wasm
wasmtime output.wasm
```

---

## Tizen walrus 런타임

[walrus](https://github.com/Samsung/walrus)는 Samsung이 개발한 경량 WASI-compliant WASM 런타임으로,
Tizen 플랫폼에서 사용됩니다.

```bash
# walrus로 실행
walrus --wasi output.wasm

# 인자 전달
walrus --wasi output.wasm -- arg1 arg2
```

walrus는 WASI Preview1 스펙을 지원하므로 `wasm32-wasi` 타겟으로 빌드된 바이너리와 호환됩니다.

---

## 빌드 스크립트

각 언어별 빌드는 `scripts/` 디렉토리의 쉘 스크립트를 사용합니다:

| 스크립트 | 역할 |
|---------|------|
| `scripts/build_rust.sh` | Rust 프로젝트를 wasm32-wasi로 빌드 |
| `scripts/build_ts.sh` | AssemblyScript 프로젝트를 WASM으로 빌드 |
| `scripts/build_python.sh` | Python 스크립트를 WASM으로 변환 |
| `scripts/run_wasm.sh` | 빌드된 .wasm 파일을 walrus로 실행 |
