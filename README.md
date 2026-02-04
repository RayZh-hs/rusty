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

To get started, clone the repository and initialize submodules.

```bash
git clone https://github.com/RayZh-hs/rusty.git
git submodule init
git submodule update
```

Ensure you have Java 21 and Clang installed on your system. Build the project using Gradle:

```bash
./gradlew build
```

If you see no issues, you are ready to go.

## Running

The compiler can be run using Gradle:

```bash
./gradlew run --args="arguments"
```

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
