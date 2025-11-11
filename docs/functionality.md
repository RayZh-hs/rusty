# Rusty Compiler Functionality

A comprehensive overview of implemented features, compiler stages, and deprecated functionality in the Rusty compiler.

## Table of Contents
1. [Feature Status Matrix](#feature-status-matrix)
2. [Lexing](#lexing)
3. [Parsing](#parsing)
4. [Semantic Analysis](#semantic-analysis)
5. [Deprecated Functionality](#deprecated-functionality)
6. [Error Reporting & Diagnostics](#error-reporting--diagnostics)
7. [Examples](#examples)

## Feature Status Matrix

| Feature Category | Status | Notes |
|------------------|--------|-------|
| Basic Types | Implemented | i32, u32, isize, usize, bool, char, str |
| Arrays & Slices | Implemented | Fixed-size arrays with compile-time length |
| Structs | Implemented | Field definitions and instantiation |
| Enums | Implemented | Simple enum variants without data |
| Functions | Implemented | Parameters, return types, blocks |
| Control Flow | Implemented | if/else, while, loop, break, continue |
| Pattern Matching | Partial | Basic patterns without guards |
| References | Implemented | & and &mut references |
| Type Inference | Partial | Limited to local variables |
| Generics | Deprecated | Removed from specification |
| Traits | Deprecated | Removed from specification |
| Lifetimes | Deprecated | Removed from specification |
| Macros | Deprecated | Removed from specification |

## Lexing

The lexer ([`src/main/kotlin/rusty/lexer/Lexer.kt`](src/main/kotlin/rusty/lexer/Lexer.kt:1)) tokenizes source code into a stream of tokens for parsing.

### Supported Token Types
- **Identifiers**: Variable and function names
- **Keywords**: fn, struct, enum, let, if, else, while, loop, etc.
- **Literals**: Integers, strings, chars, booleans
- **Operators**: Arithmetic (+, -, *, /, %), comparison (==, !=, <, >, <=, >=), logical (&&, ||, !)
- **Delimiters**: Parentheses, brackets, braces, commas, semicolons
- **Special**: underscore, colon, double colon, arrow, etc.

### Notable Limitations
- Raw string literals (r"...", r#"..."#) are parsed but not fully implemented
- Byte strings (b"...") are recognized but not fully processed
- No support for Unicode escape sequences in string literals

### Errors Produced
- Unterminated string/char literals
- Unrecognized operators
- Invalid numeric literals

## Parsing

The parser ([`src/main/kotlin/rusty/parser/Parser.kt`](src/main/kotlin/rusty/parser/Parser.kt:1)) builds an AST from the token stream using a Pratt parsing approach.

### Supported Syntax Constructs
- **Items**: Functions, structs, enums, constants, impl blocks
- **Functions**: Parameters, return types, block expressions
- **Structs**: Field definitions with types
- **Enums**: Simple variant definitions
- **Patterns**: Literals, identifiers, wildcards, destructuring
- **Match Expressions**: Basic pattern matching without guards
- **Loops**: loop, while, for (limited)
- **Expressions**: Literals, binary/unary operators, function calls, field access, indexing

## Semantic Analysis

The semantic analyzer ([`src/main/kotlin/rusty/semantic/SemanticConstructor.kt`](src/main/kotlin/rusty/semantic/SemanticConstructor.kt:1)) performs type checking and symbol resolution.

### Supported Type Checks
- Basic type compatibility (integers, booleans, etc.)
- Reference type checking (mutability requirements)
- Array bounds checking (compile-time)
- Function parameter/return type matching

### Symbol Resolution Behavior
- Hierarchical scoping (global, function, block)
- Shadowing is allowed
- Forward references are not permitted
- Self parameter resolution for methods

### Inference Limits
- Limited to local variable initialization
- No generic type inference
- Cannot infer closure types
- Reference types must be explicit

### Runtime-Value Modeling
- Constants are evaluated at compile time
- Array lengths must be compile-time constants
- No support for compile-time function evaluation

## Deprecated Functionality

These features have been removed from the simplified Rust language specification:

| Feature | Reason for Deprecation | Migration Notes |
|---------|-----------------------|-----------------|
| Traits and Trait Implementations | Complexity reduction | Use concrete types and impl blocks |
| Generics | Complexity reduction | Use concrete types instead |
| Macros | Complexity reduction | Use functions instead |
| Unsafe Code | Safety focus | Not applicable in simplified version |
| Modules and Crates | Simplified compilation | Single-file programs only |
| Lifetimes and Borrowing Annotations | Complexity reduction | Simple reference model only |
| Closures and Anonymous Functions | Complexity reduction | Use named functions |

## Error Reporting & Diagnostics

Errors flow through the [`CompileError`](src/main/kotlin/rusty/core/CompileError.kt:1) class with position tracking via [`CompilerPointer`](src/main/kotlin/rusty/core/CompilerPointer.kt:1).

### Error Flow
1. Error detected at specific position
2. Context added via `.with()` method
3. Position information added via `.at()` method
4. Formatted with source code snippet and caret pointer

### Typical Error Examples
```
Compile Error occurred at position: 10:15
    let x: i32 = "hello";
                 ^^^^^^
Type mismatch: expected i32, found string
```

```
Compile Error occurred at position: 5:8
    return x;
           ^
Cannot find value 'x' in this scope
```

## Examples

### Supported Program
```rust
struct Rectangle {
    width: i32,
    height: i32,
}

fn area(rect: Rectangle) -> i32 {
    return rect.width * rect.height;
}

fn main() {
    let rect = Rectangle { width: 10, height: 20 };
    let a = area(rect);
    println!("Area: {}", a);
}
```

### Deprecated Construct with Replacement
```rust
// Deprecated: Generic function
fn max<T: PartialOrd>(a: T, b: T) -> T {
    if a > b { a } else { b }
}

// Replacement: Concrete types
fn max_i32(a: i32, b: i32) -> i32 {
    if a > b { a } else { b }
}

// Deprecated: Trait implementation
impl Display for Point {
    fn fmt(&self, f: &mut Formatter) -> Result {
        write!(f, "({}, {})", self.x, self.y)
    }
}

// Replacement: Method in impl block
impl Point {
    fn to_string(&self) -> String {
        format!("({}, {})", self.x, self.y)
    }
}