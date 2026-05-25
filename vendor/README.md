This directory vendors local copies of Norb-maintained libraries that `rusty`
depends on at build time:

- `vendor/llvm` from `~/Projects/LLVM`
- `vendor/kolor/*.kt` from the previously embedded `com.andreapivetta.kolor` sources
- `vendor/riscv-asm-kotlin` from `~/Projects/RISCV`

The root Gradle build compiles these source trees directly through the main
Kotlin source set, so the project no longer resolves them from Maven or through
an external composite build.
