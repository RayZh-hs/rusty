<div align="center">
  <img
    src="public/rusty-logo-square.png"
    alt="Rusty Logo"
    width="128" height="128"
  />
  <h2 align="center">Rusty</h2>
</div>

A minimal Rust compiler written in Kotlin.

---

## About

This project is a compiler for a **simplified** Rust language. It is written in Kotlin and handles lexing, parsing, preprocessing, and semantic analysis. It is part of the ACM 2025-2026 Compiler Design course project at SJTU.

## Language Specification

For detailed information about the simplified Rust language syntax and semantics, refer to the official specification at:

📖 [Simplified Rust Language Spec](https://scr.coffish.ee:3210/)

## Setup

To get started, clone the repository. The internal `llvm` and `riscv-asm-kotlin`
libraries are vendored into this repo, so the main build no longer depends on
publishing or checking them out separately.

```bash
git clone https://github.com/RayZh-hs/rusty.git
```

Ensure you have Java 21 and Clang installed on your system. Build the project using Gradle:

```bash
./gradlew build
```

If you see no issues, you are ready to go.

Official testcase repositories are still tracked as git submodules under
`src/test/resources/@official*`. You only need to initialize them if you want to
run those suites:

```bash
git submodule init
git submodule update
```

## Running

The compiler can be run using Gradle:

```bash
./gradlew run --args="arguments"
```

For the judge-style streaming path requested by the current backend, use:

```bash
./gradlew installDist
make run < program.rx > user.s 2> builtin.s
```

This mode reads RX source from `stdin`, writes the compiler-emitted user assembly
to `stdout`, and writes a GCC-compatible rv64gc/lp64d runtime `builtin.s` to
`stderr`.

It supports a range of arguments for different compilation stages:
- `-i <file>`: Specify input source file.
- `-o <file>`: Specify output file.
- `-m <mode>`: Specify compilation mode (lex, parse, preprocess, semantic), defaults to full compilation.
- `-s <display mode>`: Specify display mode (none, result, verbose), defaults to result.

## Testing

The compiler includes a suite of tests, both official and custom.

To run all custom tests, use:

```bash
./gradlew test
```

Due to time constraints official tests will be skipped in this phase.

Customize testing by specifying the `-DlocalTestFile` and `-DlocalTestMode` options.

Before issues in the official tests were fixed, there existed [a third-party fork](https://github.com/TheUnknownThing/RCompiler-Testcases) with ahead-of-time fixes from @TheUnknownThing. Run the fork using:

IR generation has its own testbench. Manual IR resources run with clang+execution by default:

```bash
./gradlew manualIrTest
```

To exercise the official IR suites (tagged and skipped by default), run:

```bash
./gradlew officialIrTest
```

Pass `-DirNoClang=true` to skip the clang/link/run phase when you only want IR emission.

### Profiling rv64/qemu runtime

To measure official IR-1 testcase runtime on rv64 under qemu, excluding compiler
and link time, run:

```bash
python3 scripts/profile_ir1_rv64.py
```

The profiler builds each testcase once for `--emit ir` and `--emit opt`, links
the rv64 executable, checks the testcase output, then times repeated
`qemu-riscv64` executions. Reports are written to
`build/ir1-rv64-profile/reports/profile.md` and `profile.csv`.

Useful options:

```bash
python3 scripts/profile_ir1_rv64.py --runs 10 --warmups 2
python3 scripts/profile_ir1_rv64.py --case comprehensive1 --case comprehensive2
python3 scripts/profile_ir1_rv64.py --mode opt --timeout 60
```
