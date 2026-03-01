// Node.js WASI runner for .wasm files
// 사용법: node run_wasm_node.mjs <wasm_file> [args...]
// 예시:   node run_wasm_node.mjs ../wasm/hello_world_rust.wasm World

import { WASI } from 'wasi';
import { argv, env } from 'process';
import { readFileSync } from 'fs';

const [, , wasmFile, ...wasmArgs] = argv;

if (!wasmFile) {
  console.error('Usage: node run_wasm_node.mjs <wasm_file> [args...]');
  process.exit(1);
}

const wasi = new WASI({
  version: 'preview1',
  args: [wasmFile, ...wasmArgs],
  env,
  preopens: { '/': '/' },
});

const wasm = await WebAssembly.compile(readFileSync(wasmFile));
const instance = await WebAssembly.instantiate(wasm, {
  wasi_snapshot_preview1: wasi.wasiImport,
});

wasi.start(instance);
