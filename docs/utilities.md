# Utilities Documentation

This document describes the utility libraries used throughout the Rusty compiler project.

## Table of Contents
1. [Core Utilities](#core-utilities)
2. [CLI Utilities](#cli-utilities)
3. [Dumping/Debugging Helpers](#dumpingdebugging-helpers)
4. [Small Utility Modules](#small-utility-modules)
5. [Best Practice Notes](#best-practice-notes)

## Core Utilities

### Stream
[`src/main/kotlin/rusty/core/Stream.kt`](src/main/kotlin/rusty/core/Stream.kt:1)

Provides a programmatic way to traverse iterable objects with cursor management. The Stream class maintains a position in a collection and supports operations like peeking, reading, and navigating.

Key operations:
- [`peek()`](src/main/kotlin/rusty/core/Stream.kt:47) - Returns current element without consuming
- [`read()`](src/main/kotlin/rusty/core/Stream.kt:35) - Returns and consumes current element
- [`position`](src/main/kotlin/rusty/core/Stream.kt:11) - Current cursor position
- [`pushCursor()`](src/main/kotlin/rusty/core/Stream.kt:77) / [`popCursor()`](src/main/kotlin/rusty/core/Stream.kt:82) - Stack-based cursor management

Example usage:
```kotlin
val stream = Stream(listOf('a', 'b', 'c'))
val first = stream.peek()  // 'a'
val consumed = stream.read()  // 'a', position now at index 1
```

### MarkedString
[`src/main/kotlin/rusty/core/MarkedString.kt`](src/main/kotlin/rusty/core/MarkedString.kt:1)

Associates every character with its original position in the source file via CompilerPointer. This is essential for preserving location information throughout the compilation pipeline.

Key features:
- Preserves character-level position mapping
- Used across lexer, parser, and semantic analysis
- Supports building with [`MarkedStringBuilder`](src/main/kotlin/rusty/core/MarkedString.kt:19)

### CompilerPointer
[`src/main/kotlin/rusty/core/CompilerPointer.kt`](src/main/kotlin/rusty/core/CompilerPointer.kt:1)

Represents a precise position in the original source file with 1-based line and column indices. Provides special values for prelude and unknown positions.

### MemoryBank
[`src/main/kotlin/rusty/core/MemoryBank.kt`](src/main/kotlin/rusty/core/MemoryBank.kt:1)

A generic key-value storage utility for caching computed values. Supports lazy evaluation with the [`recall()`](src/main/kotlin/rusty/core/MemoryBank.kt:7) method and explicit value overwriting.

### CompileError
[`src/main/kotlin/rusty/core/CompileError.kt`](src/main/kotlin/rusty/core/CompileError.kt:1)

Enhanced exception class for compilation errors with context support. Provides:
- Position-aware error reporting with [`at()`](src/main/kotlin/rusty/core/CompileError.kt:26)
- Context chaining with [`with()`](src/main/kotlin/rusty/core/CompileError.kt:20)
- Source code snippet display

## CLI Utilities

### CommandParser and Types
[`src/main/kotlin/rusty/cli/CommandParser.kt`](src/main/kotlin/rusty/cli/CommandParser.kt:1), [`src/main/kotlin/rusty/cli/CommandParserTypes.kt`](src/main/kotlin/rusty/cli/CommandParserTypes.kt:1)

Simple keyword-only CLI parser supporting required and optional arguments. Handles quoted values and provides validation.

Example invocation:
```bash
./rusty --input file.rs --output out.json --verbose
```

## Dumping/Debugging Helpers

### Lexer Dumper
[`src/main/kotlin/rusty/lexer/Dumper.kt`](src/main/kotlin/rusty/lexer/Dumper.kt:1)

Provides token visualization utilities:
- [`dump()`](src/main/kotlin/rusty/lexer/Dumper.kt:8) - Write tokens to file
- [`dumpScreen()`](src/main/kotlin/rusty/lexer/Dumper.kt:20) - Display tokens with color coding

### Parser Dumper
[`src/main/kotlin/rusty/parser/Dumper.kt`](src/main/kotlin/rusty/parser/Dumper.kt:1)

Visitor-based AST pretty printer:
- [`dump()`](src/main/kotlin/rusty/parser/Dumper.kt:12) - Write AST to file
- [`dumpScreen()`](src/main/kotlin/rusty/parser/Dumper.kt:15) - Display AST with colors

### Semantic Dumper
[`src/main/kotlin/rusty/semantic/Dumper.kt`](src/main/kotlin/rusty/semantic/Dumper.kt:1)

Visualizes semantic analysis results:
- [`dump()`](src/main/kotlin/rusty/semantic/Dumper.kt:264) - Write scope tree to file
- [`dumpScreen()`](src/main/kotlin/rusty/semantic/Dumper.kt:269) - Display scope tree with colors
- [`dumpPhase()`](src/main/kotlin/rusty/semantic/Dumper.kt:275) - Labeled phase output

## Small Utility Modules

### Core Utils
- [`CharExt.kt`](src/main/kotlin/rusty/core/utils/CharExt.kt:1) - Character classification helpers
- [`ListExt.kt`](src/main/kotlin/rusty/core/utils/ListExt.kt:1) - Collection utilities with unique key association
- [`Slot.kt`](src/main/kotlin/rusty/core/utils/Slot.kt:1) - Single-assignment container for lazy initialization
- [`Volatile.kt`](src/main/kotlin/rusty/core/utils/Volatile.kt:1) - Read-once value container with auto-clearing

### Parser Utility Packages
- [`Context.kt`](src/main/kotlin/rusty/parser/putils/Context.kt:1) - Parsing state management with cursor tracking
- [`Atomic.kt`](src/main/kotlin/rusty/parser/putils/Atomic.kt:1) - Token expectation and consumption helpers
- [`Compound.kt`](src/main/kotlin/rusty/parser/putils/Compound.kt:1) - List and tuple parsing utilities
- [`Flags.kt`](src/main/kotlin/rusty/parser/putils/Flags.kt:1) - Boolean flag management with scoping

## Best Practice Notes

### When to use MarkedString vs raw strings
- Use MarkedString when position information needs to be preserved through the compilation pipeline
- Use raw strings for internal processing where source positions are irrelevant
- Convert to MarkedString early in the pipeline if position tracking might be needed later

### How to add small helper utilities
- Place core utilities in [`src/main/kotlin/rusty/core/utils/`](src/main/kotlin/rusty/core/utils/)
- Place parser-specific utilities in [`src/main/kotlin/rusty/parser/putils/`](src/main/kotlin/rusty/parser/putils/)
- Keep utilities focused and single-purpose
- Follow existing naming conventions (e.g., extension functions for type-specific utilities)

### How dumpers and utilities are expected to be kept side-effect-free
- Dumpers should only read and format data without modifying it
- Utilities should avoid modifying global state
- Use immutable data structures where possible
- When state is necessary, encapsulate it within the utility class