<div align="center">
  <img src="https://raw.githubusercontent.com/RayZh-hs/LLVM/main/public/Kotlin-LLVM.png" alt="Kotlin-LLVM Logo" width="200"/>
  <h1>Kotlin-LLVM</h1>
  <p align="center">
  Modern LLVM IR Generation Framework for Kotlin.
  </p>
  <p align="center">
    <a href="https://kotlinlang.org">
      <img src="https://img.shields.io/badge/Kotlin-1.19.22-blue.svg" alt="Kotlin Version"/>
    </a>
    <a href="https://llvm.org">
      <img src="https://img.shields.io/badge/LLVM%20IR-Untyped%20Pointers-orange.svg" alt="LLVM IR"/>
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"/>
    </a>
  </p>
</div>


## Overview

Kotlin-LLVM is a comprehensive framework for generating LLVM Intermediate Representation (IR) using Kotlin. It provides a type-safe, fluent API for constructing LLVM IR programs with full compliance to the latest LLVM IR standard using untyped pointers.

## Features

- **🔧 Type-Safe IR Construction**: Leverages Kotlin's type system to prevent errors during IR construction
- **🏗️ Fluent Builder Pattern**: Intuitive API for building complex LLVM IR programs
- **📋 Modern LLVM IR Compliance**: Uses the latest untyped pointer model compatible with modern LLVM toolchains
- **🎯 Comprehensive Instruction Set**: Support for all major LLVM instruction categories including vector operations
- **📦 Struct Support**: First-class support for named and anonymous structs
- **🔀 Advanced Control Flow**: Support for Phi nodes and Switch instructions
- **💾 Memory Operations**: Complete memory model with alloca, load, store, and GEP
- **📝 IR Comments**: Ability to attach comments to the generated IR
- **🔍 Visitor Pattern**: Built-in support for IR analysis, transformation, and printing
- **✅ Extensive Testing**: Comprehensive test suite with end-to-end validation

## Quick Start

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("space.norb:llvm:1.0-SNAPSHOT")
}
```

### Basic Usage

```kotlin
import space.norb.llvm.structure.*
import space.norb.llvm.types.*
import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.values.constants.IntConstant

// Create a module
val module = Module("example")

// Create an IR builder
val builder = IRBuilder(module)

// Define a simple function that adds two integers
val addFunction = module.registerFunction(
    name = "add",
    returnType = TypeUtils.I32,
    paramTypes = listOf(TypeUtils.I32, TypeUtils.I32)
).apply {
    // Create basic block and set as entry point
    val entryBlock = this.insertBasicBlock("entry", setAsEntrypoint = true)
    builder.positionAtEnd(entryBlock)
    
    // Get function parameters
    val a = this.parameters[0]
    val b = this.parameters[1]
    
    // Add the two parameters
    val result = builder.insertAdd(a, b, "result")
    
    // Return the result
    builder.insertRet(result)
}

// Print the generated IR
println(module.toIRString())
```

This generates the following LLVM IR:

```llvm
define i32 @add(i32 %0, i32 %1) {
entry:
  %result = add i32 %0, %1
  ret i32 %result
}
```

## Project Structure

```
src/
├── main/kotlin/space/norb/llvm/
│   ├── core/               # Core abstractions (Value, Type, User, Constant)
│   ├── types/              # Type system implementations
│   ├── values/             # Value implementations (constants, globals)
│   ├── structure/          # Structural components (Module, Function, BasicBlock)
│   ├── instructions/       # Instruction hierarchy
│   │   ├── base/           # Base instruction classes
│   │   ├── terminators/    # Terminator instructions
│   │   ├── binary/         # Binary operations
│   │   ├── memory/         # Memory operations
│   │   ├── casts/          # Type casting operations
│   │   └── other/          # Other instructions (calls, comparisons, phi)
│   ├── builder/            # IR construction utilities
│   ├── visitors/           # Visitor pattern implementations
│   ├── enums/              # Enumerations and constants
│   ├── utils/              # Utility functions and extensions
│   └── examples/           # Usage examples
└── test/kotlin/            # Comprehensive test suite
```

## Supported LLVM Features

### Types
- ✅ Primitive types (void, integers, floating-point)
- ✅ Derived types (pointers, arrays, structs, functions)
- ✅ Untyped pointers (compliant with latest LLVM IR)

### Instructions
- ✅ **Terminators**: ret, br, switch
- ✅ **Integer Binary Operations**: add, sub, mul, udiv, sdiv, urem, srem
- ✅ **Bitwise Operations**: and, or, xor, shl, lshr, ashr
- ✅ **Floating Point Operations**: fadd, fsub, fmul, fdiv, frem, fcmp
- ✅ **Memory Operations**: alloca, load, store, getelementptr
- ✅ **Cast Operations**: trunc, zext, sext, bitcast, ptrtoint
- ✅ **Other Operations**: call, indirect call, icmp, phi

### Structural Components
- ✅ Modules with functions and global variables
- ✅ Functions with parameters, basic blocks, and configurable linkage types
- ✅ Basic blocks with instruction sequences
- ✅ Global variables with various linkage types

## Building and Testing

### Prerequisites
- JDK 21 or later
- Kotlin 2.2.0

### Build

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

## Examples

The project includes several comprehensive examples in the [`src/main/kotlin/space/norb/llvm/examples`](src/main/kotlin/space/norb/llvm/examples) directory:

- **[`AbsExample.kt`](src/main/kotlin/space/norb/llvm/examples/AbsExample.kt)** - Demonstrates control flow with conditional branches and basic block management
- **[`HelloWorldExample.kt`](src/main/kotlin/space/norb/llvm/examples/HelloWorldExample.kt)** - Shows how to work with global variables and string constants
- **[`StructExample.kt`](src/main/kotlin/space/norb/llvm/examples/StructExample.kt)** - Illustrates struct type registration, memory allocation, and field access

These examples demonstrate the current API patterns and best practices for generating LLVM IR with Kotlin-LLVM.

## Advanced Usage

### Function Linkage

Functions can now declare the same linkage variants that LLVM supports, allowing you to control visibility without leaving Kotlin.

```kotlin
// Internal helper – never exposed outside the module
val helper = module.registerFunction(
    name = "helper",
    returnType = TypeUtils.I32,
    paramTypes = listOf(TypeUtils.I32),
    linkage = LinkageType.INTERNAL
)

// External declaration – emitted as `declare` so it can be resolved at link time
val printf = module.declareExternalFunction(
    name = "printf",
    returnType = TypeUtils.I32,
    parameterTypes = listOf(PointerType),
    isVarArg = true
)
```

`LinkageType` mirrors the LLVM IR spec (EXTERNAL, INTERNAL, PRIVATE, WEAK, DLL_IMPORT/EXPORT, etc.). Definitions default to `EXTERNAL`, while `declareExternalFunction` keeps declarations external so that they link against existing implementations.

### Working with Structs

```kotlin
val module = Module("StructExample")
val builder = IRBuilder(module)

// Define a Point struct { i32, i32 }
val pointType = module.registerNamedStructType(
    name = "Point",
    elementTypes = listOf(
        BuilderUtils.getIntType(32),
        BuilderUtils.getIntType(32)
    )
)

// Create a function that uses the struct
module.registerFunction(
    name = "createPoint",
    returnType = pointType,
    parameterTypes = listOf(BuilderUtils.getIntType(32), BuilderUtils.getIntType(32))
).apply {
    insertBasicBlock("entry").apply {
        builder.positionAtEnd(this)
        
        // Allocate memory for the struct
        val ptr = builder.insertAlloca(pointType, "point")
        
        // Store x and y coordinates
        val xPtr = builder.insertGep(
            pointType, 
            ptr, 
            listOf(BuilderUtils.getIntConstant(0L, 32), BuilderUtils.getIntConstant(0L, 32)), 
            "xPtr"
        )
        builder.insertStore(parameters[0], xPtr)
        
        val yPtr = builder.insertGep(
            pointType, 
            ptr, 
            listOf(BuilderUtils.getIntConstant(0L, 32), BuilderUtils.getIntConstant(1L, 32)), 
            "yPtr"
        )
        builder.insertStore(parameters[1], yPtr)
        
        // Load and return
        val result = builder.insertLoad(pointType, ptr, "result")
        builder.insertRet(result)
    }
}
```

### Control Flow

```kotlin
// Switch instruction
builder.insertSwitch(
    condition = value,
    defaultDest = defaultBlock,
    cases = listOf(
        Pair(BuilderUtils.getIntConstant(0, 32), case0Block),
        Pair(BuilderUtils.getIntConstant(1, 32), case1Block)
    ),
    name = "switch"
)

// Phi node
builder.insertPhi(
    type = TypeUtils.I32,
    incomingValues = listOf(
        Pair(val1, block1),
        Pair(val2, block2)
    ),
    name = "phi"
)
```

### Floating Point Operations

```kotlin
// Floating point addition
val sum = builder.insertFAdd(float1, float2, "sum")

// Floating point comparison
val cmp = builder.insertFCmp(FcmpPredicate.OEQ, float1, float2, "cmp")
```

### Type Casting

```kotlin
// Bitcast (e.g., float to i32)
val asInt = builder.insertBitcast(floatVal, TypeUtils.I32, "asInt")

// Integer extension (i32 to i64)
val extended = builder.insertSExt(int32Val, TypeUtils.I64, "extended")

// Truncation (i64 to i32)
val truncated = builder.insertTrunc(int64Val, TypeUtils.I32, "truncated")
```

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

1. Clone the repository
2. Import into IntelliJ IDEA or use the command line
3. Run `./gradlew build` to ensure everything builds
4. Make your changes
5. Add tests for new functionality
6. Run `./gradlew test` to ensure all tests pass
7. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
