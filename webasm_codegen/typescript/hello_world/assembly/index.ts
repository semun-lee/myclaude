// AssemblyScript → WASM 예제
// AssemblyScript는 TypeScript 문법으로 WASM을 직접 생성합니다.
// 빌드: npx asc assembly/index.ts --target release -o ../../wasm/hello_world_ts.wasm
// 실행: walrus --wasi ../../wasm/hello_world_ts.wasm

// WASI에서 stdout 출력을 위한 외부 함수 import
@external("wasi_snapshot_preview1", "fd_write")
declare function fd_write(fd: i32, iovs: i32, iovs_len: i32, nwritten: i32): i32;

// 간단한 문자열 출력 함수
function writeString(s: string): void {
  const encoded = String.UTF8.encode(s + "\n");
  const buf = heap.alloc(encoded.byteLength);
  memory.copy(buf, changetype<usize>(encoded), encoded.byteLength);

  // iovec 구조체 설정 (ptr, len)
  const iov = heap.alloc(8);
  store<u32>(iov, buf as u32);
  store<u32>(iov + 4, encoded.byteLength as u32);

  const nwritten = heap.alloc(4);
  fd_write(1, iov as i32, 1, nwritten as i32); // fd=1: stdout

  heap.free(buf);
  heap.free(iov);
  heap.free(nwritten);
}

export function _start(): void {
  writeString("Hello, WASM world from AssemblyScript!");
}
