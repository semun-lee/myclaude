// Rust → WASM (wasm32-wasi) 예제
// 빌드: cargo build --target wasm32-wasi --release
// 실행: walrus --wasi target/wasm32-wasi/release/hello_world.wasm

use std::env;

fn main() {
    let args: Vec<String> = env::args().collect();

    if args.len() > 1 {
        println!("Hello, {}!", args[1]);
    } else {
        println!("Hello, WASM world from Rust!");
    }

    // WASI에서 환경변수 접근
    if let Ok(val) = env::var("WASM_ENV") {
        println!("WASM_ENV = {}", val);
    }
}
