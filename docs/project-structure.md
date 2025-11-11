# Rusty Compiler Architecture

This document provides an overview of the Rusty compiler's architecture, pipeline stages, and key components.

## Directory Structure

```
src/main/kotlin/rusty/
├── cli/                        # Command-line interface and argument parsing
│   ├── CommandParser.kt        # CLI argument parser
│   └── CommandParserTypes.kt   # Type definitions for CLI
├── core/                       # Core utilities and data structures
│   ├── CompileError.kt         # Error handling
│   ├── CompilerPointer.kt      # Source code position tracking
│   ├── Constants.kt            # Compiler constants
│   ├── MarkedString.kt         # String with position markers
│   ├── MemoryBank.kt           # Memory management utilities
│   ├── Stream.kt               # Stream abstraction for tokens
│   └── utils/                  # Utility functions
├── lexer/                      # Lexical analysis (tokenization)
│   ├── Lexer.kt                # Main lexer implementation
│   ├── Token.kt                # Token type definitions
│   ├── TokenBearer.kt          # Token with position information
│   ├── Validation.kt           # Token validation
│   └── Dumper.kt               # Token output utilities
├── parser/                     # Syntactic analysis (parsing)
│   ├── Parser.kt               # Main parser implementation
│   ├── Dumper.kt               # AST output utilities
│   ├── nodes/                  # AST node definitions
│   │   ├── ASTNode.kt          # Base AST node class
│   │   ├── CrateNode.kt        # Root AST node
│   │   ├── ExpressionNode.kt   # Expression nodes
│   │   ├── ItemNode.kt         # Top-level item nodes
│   │   ├── ParamsNode.kt       # Parameter nodes
│   │   ├── PathNode.kt         # Path nodes
│   │   ├── PatternNode.kt      # Pattern nodes
│   │   ├── StatementNode.kt    # Statement nodes
│   │   ├── TypeNode.kt         # Type nodes
│   │   ├── impl/               # Implementation files for nodes
│   │   ├── support/            # Supporting node types
│   │   └── utils/              # Parser utilities
│   │       ├── Parsable.kt     # Parsing interface
│   │       ├── Peekable.kt     # Lookahead interface
│   │       └── Visitor.kt      # Visitor pattern implementation
│   └── putils/                 # Parser utilities
├── preprocessor/               # Source code preprocessing
│   ├── Preprocessor.kt         # Main preprocessor
│   └── Dumper.kt               # Preprocessor output utilities
├── semantic/                   # Semantic analysis
│   ├── SemanticConstructor.kt  # Main semantic analyzer
│   ├── Dumper.kt               # Semantic analysis output
│   ├── support/                # Supporting classes for semantic analysis
│   │   ├── Context.kt          # Analysis context
│   │   ├── Scope.kt            # Symbol scoping
│   │   ├── SemanticSymbol.kt   # Symbol representation
│   │   ├── SemanticType.kt     # Type representation
│   │   └── SymbolTable.kt      # Symbol table implementation
│   └── visitors/               # Visitor pattern implementations
│       ├── bases/              # Base visitor classes
│       ├── companions/         # Companion visitor classes
│       └── utils/              # Visitor utilities
└── Main.kt                     # Compiler entry point
```

## Compilation Pipeline

The Rusty compiler follows a traditional multi-stage compilation pipeline:

### 1. Preprocessing
- **File**: [`Preprocessor.kt`](src/main/kotlin/rusty/preprocessor/Preprocessor.kt)
- **Purpose**: Removes comments, normalizes whitespace, and handles raw string literals
- **Input**: Raw source code as string
- **Output**: [`MarkedString`](src/main/kotlin/rusty/core/MarkedString.kt) with position information

### 2. Lexical Analysis (Lexing)
- **File**: [`Lexer.kt`](src/main/kotlin/rusty/lexer/Lexer.kt)
- **Purpose**: Converts preprocessed source code into a stream of tokens
- **Input**: [`MarkedString`](src/main/kotlin/rusty/core/MarkedString.kt) from preprocessing
- **Output**: List of [`TokenBearer`](src/main/kotlin/rusty/lexer/TokenBearer.kt) objects
- **Key Features**:
  - Handles numeric literals, string literals, identifiers, keywords, and operators
  - Supports raw strings, byte strings, and C-style strings
  - Provides position tracking for error reporting

### 3. Syntactic Analysis (Parsing)
- **File**: [`Parser.kt`](src/main/kotlin/rusty/parser/Parser.kt)
- **Purpose**: Converts token stream into Abstract Syntax Tree (AST)
- **Input**: Token stream from lexing
- **Output**: [`CrateNode`](src/main/kotlin/rusty/parser/nodes/CrateNode.kt) (root of AST)
- **Key Features**:
  - Recursive descent parser with lookahead capabilities
  - Comprehensive AST node hierarchy for all language constructs
  - Visitor pattern implementation for AST traversal

### 4. Semantic Analysis
- **File**: [`SemanticConstructor.kt`](src/main/kotlin/rusty/semantic/SemanticConstructor.kt)
- **Purpose**: Performs type checking, symbol resolution, and semantic validation
- **Input**: AST from parsing
- **Output**: [`Context`](src/main/kotlin/rusty/semantic/support/Context.kt) with semantic information
- **Key Features**:
  - Multi-pass analysis with specialized visitors
  - Symbol table management and scoping
  - Type resolution and inference

## Key Design Patterns

### 1. Visitor Pattern
- **Implementation**: [`Visitor.kt`](src/main/kotlin/rusty/parser/nodes/utils/Visitor.kt)
- **Purpose**: Enables operations on AST nodes without modifying node classes
- **Usage**: AST traversal, semantic analysis, and pretty-printing

### 2. Parser Combinators
- **Implementation**: [`Parsable.kt`](src/main/kotlin/rusty/parser/nodes/utils/Parsable.kt) and [`Peekable.kt`](src/main/kotlin/rusty/parser/nodes/utils/Peekable.kt)
- **Purpose**: Modular parsing with lookahead capabilities
- **Usage**: Building complex parsers from simpler ones

### 3. Stream Abstraction
- **Implementation**: [`Stream.kt`](src/main/kotlin/rusty/core/Stream.kt)
- **Purpose**: Uniform interface for sequential data access
- **Usage**: Token stream processing and parsing

### 4. Context Pattern
- **Implementation**: [`Context.kt`](src/main/kotlin/rusty/semantic/support/Context.kt)
- **Purpose**: Maintains state during semantic analysis
- **Usage**: Symbol table management and type resolution

## Data Flow

```
Raw Source Code
       ↓
Preprocessor → MarkedString
       ↓
Lexer → TokenBearer[]
       ↓
Parser → AST (CrateNode)
       ↓
SemanticConstructor → SemanticContext
```

## CLI Interface

The compiler supports multiple compilation modes:
- `preprocess`: Output preprocessed source
- `lex`: Output token stream
- `parse`: Output AST
- `semantic`: Output semantic analysis results

Each mode can be combined with display options:
- `none`: Minimal output
- `result`: Standard output
- `verbose`: Detailed output with intermediate results

## Error Handling

Error handling is centralized through [`CompileError`](src/main/kotlin/rusty/core/CompileError.kt), which provides:
- Source code position tracking
- Contextual error messages
- Consistent error formatting across all compilation stages
