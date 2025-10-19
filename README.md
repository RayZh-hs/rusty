# Rusty

<div align="center">
  <picture>
    <source
        srcset="public/rusty-logo-dark.png"
        media="(prefers-color-scheme: dark)"
        height="200"
    />
    <img
        src="public/rusty-logo-light.png"
        alt="Rusty Logo"
        height="200"
    />
  </picture>
</div>

A minimal Rust compiler written in Kotlin that implements a simplified subset of the Rust language.

## About

This project is a compiler for a simplified Rust language. It is written in Kotlin and handles lexing, parsing, preprocessing, and semantic analysis.

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

