<div align="center">
  <img
    src="public/rusty-logo-square.png"
    alt="Rusty Logo"
    width="128" height="128"
  />
  <h3 align="center">Rusty</h3>
</div>

A minimal Rust compiler written in Kotlin.

---

## About

This project is a compiler for a **simplified** Rust language. It is written in Kotlin and handles lexing, parsing, preprocessing, and semantic analysis. It is part of the ACM 2025-2026 Compiler Design course project at SJTU.

## Language Specification

For detailed information about the simplified Rust language syntax and semantics, refer to the official specification at:

📖 [Simplified Rust Language Spec](https://scr.coffish.ee:3210/)

## Building

This project uses Gradle for building:

```bash
./gradlew build
```

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

Customize testing by specifying the `-DlocalTestFile` and `-DlocalTestMode` options.

To run all official tests, use:

```bash
./gradlew officialTest --tests rusty.OfficialSemanticTests
```

Before issues in the official tests were fixed, there existed [third-party forks](https://github.com/TheUnknownThing/RCompiler-Testcases) with ahead-of-time fixes from @theunknownthing. Run the fork using:

```bash
./gradlew officialFixedTest  --tests rusty.OfficialFixedSemanticTests
```

