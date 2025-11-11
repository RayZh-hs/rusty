# Rusty Compiler Pipeline

This document provides a detailed overview of the Rusty compiler pipeline, explaining each compilation stage, their responsibilities, and how data flows through the system.

## Overview

The Rusty compiler processes source code through four main stages, orchestrated by [`Main.kt`](src/main/kotlin/rusty/Main.kt:25):

1. **Preprocessing** - Removes comments and normalizes whitespace
2. **Lexical Analysis** - Converts source text into tokens
3. **Parsing** - Builds an Abstract Syntax Tree (AST) from tokens
4. **Semantic Analysis** - Performs type checking and semantic validation

## Compilation Modes

The compiler supports four compilation modes ([`CompileMode`](src/main/kotlin/rusty/core/Constants.kt:3)):

- **PREPROCESS** (`pp`, `pre`, `preprocess`) - Stops after preprocessing
- **LEX** (`lex`) - Stops after lexical analysis
- **PARSE** (`parse`, `parser`, `parsing`) - Stops after parsing (default)
- **SEMANTIC** (`sem`, `semantic`) - Completes all stages

## Stage 1: Preprocessing

### Purpose and Responsibilities
The preprocessor ([`Preprocessor.kt`](src/main/kotlin/rusty/preprocessor/Preprocessor.kt:9)) prepares source code for lexical analysis by:
- Removing line comments (`//`) and block comments (`/* */`)
- Normalizing whitespace (converting newlines to spaces)
- Preserving string and character literals
- Maintaining position tracking for error reporting

### Key Classes
- [`Preprocessor`](src/main/kotlin/rusty/preprocessor/Preprocessor.kt:9) - Main preprocessing logic
- [`MarkedString`](src/main/kotlin/rusty/core/MarkedString.kt:6) - String with position tracking
- [`CompilerPointer`](src/main/kotlin/rusty/core/CompilerPointer.kt:7) - Position in source file

### Input and Output
- **Input**: Raw source text (`String`)
- **Output**: [`MarkedString`](src/main/kotlin/rusty/core/MarkedString.kt:6) with normalized content and position marks

### Important Techniques
- State machine for tracking context (in comment, in string, etc.)
- Raw string literal handling with hash delimiters
- Escape sequence processing
- Position tracking through all transformations

### Error Handling
- Detects unterminated comments and string literals
- Reports errors with precise source locations using [`CompileError`](src/main/kotlin/rusty/core/CompileError.kt:5)

## Stage 2: Lexical Analysis

### Purpose and Responsibilities
The lexer ([`Lexer.kt`](src/main/kotlin/rusty/lexer/Lexer.kt:8)) converts the preprocessed text into a stream of tokens:
- Identifies keywords, identifiers, literals, and operators
- Categorizes tokens by type
- Maintains source position information
- Validates token syntax

### Key Classes
- [`Lexer`](src/main/kotlin/rusty/lexer/Lexer.kt:8) - Main tokenization logic
- [`TokenBearer`](src/main/kotlin/rusty/lexer/TokenBearer.kt:5) - Token with position data
- [`Token`](src/main/kotlin/rusty/lexer/Token.kt:12) - Enumeration of all token types

### Input and Output
- **Input**: [`MarkedString`](src/main/kotlin/rusty/core/MarkedString.kt:6) from preprocessor
- **Output**: `MutableList<TokenBearer>` - Ordered list of tokens

### Important Techniques
- Token classification using [`TokenPeekClass`](src/main/kotlin/rusty/lexer/Lexer.kt:10)
- Longest-match algorithm for operators
- String literal parsing with escape handling
- Raw string literal detection and parsing

### Error Handling
- Validates token syntax (e.g., unterminated literals)
- Reports lexical errors with source positions
- Uses [`CompileError`](src/main/kotlin/rusty/core/CompileError.kt:5) for consistent error reporting

## Stage 3: Parsing

### Purpose and Responsibilities
The parser ([`Parser.kt`](src/main/kotlin/rusty/parser/Parser.kt:12)) builds an Abstract Syntax Tree (AST):
- Organizes tokens into a hierarchical structure
- Validates syntax according to Rust grammar rules
- Creates semantic relationships between nodes
- Preserves source position information

### Key Classes
- [`Parser`](src/main/kotlin/rusty/parser/Parser.kt:12) - Main parsing logic
- [`CrateNode`](src/main/kotlin/rusty/parser/nodes/CrateNode.kt:9) - Root AST node
- [`ASTNode`](src/main/kotlin/rusty/parser/nodes/ASTNode.kt) - Base class for all AST nodes
- [`Context`](src/main/kotlin/rusty/parser/putils/Context.kt) - Parsing context and utilities

### Input and Output
- **Input**: `MutableList<TokenBearer>` from lexer
- **Output**: [`ASTTree`](src/main/kotlin/rusty/parser/Parser.kt:9) (alias for [`CrateNode`](src/main/kotlin/rusty/parser/nodes/CrateNode.kt:9))

### Important Techniques
- Recursive descent parsing
- Visitor pattern for AST traversal
- Context-aware parsing with lookahead
- Error recovery mechanisms

### Error Handling
- Syntax error detection and reporting
- Expected token vs. actual token information
- Position tracking for precise error locations

## Stage 4: Semantic Analysis

### Purpose and Responsibilities
The semantic analyzer ([`SemanticConstructor.kt`](src/main/kotlin/rusty/semantic/SemanticConstructor.kt:16)) performs semantic validation:
- Type checking and inference
- Symbol resolution and scope management
- Function signature validation
- Trait implementation verification

### Key Classes
- [`SemanticConstructor`](src/main/kotlin/rusty/semantic/SemanticConstructor.kt:16) - Orchestrates semantic analysis
- [`Context`](src/main/kotlin/rusty/semantic/support/Context.kt) - Semantic analysis context
- [`SymbolTable`](src/main/kotlin/rusty/semantic/support/SymbolTable.kt) - Symbol management
- Various visitor classes for different analysis phases

### Input and Output
- **Input**: [`ASTTree`](src/main/kotlin/rusty/parser/Parser.kt:9) from parser
- **Output**: [`SemanticContext`](src/main/kotlin/rusty/semantic/SemanticConstructor.kt:13) with analysis results

### Important Techniques
- Multi-pass analysis with visitor pattern
- Symbol table construction and lookup
- Type inference algorithms
- Scope-aware name resolution

### Error Handling
- Type mismatch detection
- Undefined symbol reporting
- Duplicate symbol detection
- Semantic error messages with source positions

## Pipeline Orchestration

The compilation pipeline is orchestrated in [`Main.kt`](src/main/kotlin/rusty/Main.kt:25):

1. Read source file and register with error reporting
2. Execute preprocessing stage
3. Execute lexical analysis stage
4. Execute parsing stage
5. Execute semantic analysis stage

Each stage can output intermediate results for debugging, and the pipeline can be stopped at any point using the compilation modes.

## Data Flow and Position Tracking

Position tracking is maintained throughout the pipeline:

1. **Preprocessing**: Each character is marked with its original position using [`CompilerPointer`](src/main/kotlin/rusty/core/CompilerPointer.kt:7)
2. **Lexical Analysis**: Tokens inherit position information from their source characters
3. **Parsing**: AST nodes store position information from their constituent tokens
4. **Semantic Analysis**: Errors are reported using the original source positions

This ensures that error messages can accurately point to the original source code location, even after multiple transformations.

## Error Handling Strategy

The compiler uses a unified error handling approach:

- All errors extend [`CompileError`](src/main/kotlin/rusty/core/CompileError.kt:5)
- Errors include source position information
- Error messages provide context and suggestions
- The error system supports chaining additional context information

This consistent approach ensures that users receive helpful, actionable error messages throughout the compilation process.