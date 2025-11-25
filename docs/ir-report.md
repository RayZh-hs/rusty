# IR Generation Technical Report

This document provides a comprehensive technical overview of the IR (Intermediate Representation) generation phase in the Rusty compiler. It covers module architecture, data flow, key abstractions, and implementation details.

## Table of Contents

1. [Overview](#overview)
2. [Module Architecture](#module-architecture)
3. [Core Components](#core-components)
4. [IR Generation Pipeline](#ir-generation-pipeline)
5. [Type System Mapping](#type-system-mapping)
6. [Visitor Components](#visitor-components)
7. [Support Utilities](#support-utilities)
8. [Prelude System](#prelude-system)
9. [Data Flow Diagram](#data-flow-diagram)
10. [Key Algorithms](#key-algorithms)

---

## Overview

The IR generation phase transforms a semantically-validated AST into LLVM IR. It is the final major compiler phase before code generation. The IR module resides in `src/main/kotlin/rusty/ir/` and depends on:

- **Semantic Context**: Type information, symbol tables, and scope trees from semantic analysis
- **AST Nodes**: Parser-produced abstract syntax tree
- **LLVM Library** (`space.norb.llvm`): External library for constructing LLVM IR

### Directory Structure

```
src/main/kotlin/rusty/ir/
├── IRConstructor.kt          # Main entry point for IR generation
├── Dumper.kt                 # IR output utilities
├── prelude/                  # Runtime support functions
│   ├── prelude.c             # C implementation of runtime functions
│   ├── prelude.c.ll          # LLVM IR compiled from prelude.c
│   └── prelude.ll            # Hand-written LLVM IR prelude
└── support/                  # IR generation support infrastructure
    ├── FunctionPlan.kt       # Function signature planning
    ├── GeneratedValue.kt     # Value wrapper with semantic type
    ├── GenerationEnvironment.kt  # Function-local generation state
    ├── IRContext.kt          # Global IR generation state
    ├── Name.kt               # Naming convention utilities
    ├── Renamer.kt            # SSA name serial generation
    ├── TypeConversion.kt     # SemanticType → LLVM Type mapping
    └── visitors/             # AST traversal and code emission
        ├── ControlFlowEmitter.kt     # Control flow (if/while/loop)
        ├── ExpressionEmitter.kt      # Expression code generation
        ├── FunctionBodyGenerator.kt  # Function body emission
        ├── FunctionRegistrar.kt      # Function declaration registration
        ├── PreludeHandler.kt         # Prelude function setup
        ├── StructHandler.kt          # Struct type registration
        ├── StructSizeFunctionGenerator.kt  # sizeof helper functions
        └── pattern/                  # Pattern matching utilities
            ├── ParameterBinder.kt        # Function parameter binding
            └── ParameterNameExtractor.kt # Parameter name extraction
```

---

## Module Architecture

### Entry Point: `IRConstructor`

`IRConstructor.kt` serves as the main orchestrator for IR generation. It follows a strict five-phase pipeline:

```kotlin
class IRConstructor {
    companion object {
        fun run(semanticContext: SemanticContext, dumpToScreen: Boolean = false): String {
            IRContext.reset()
            val module = IRContext.module.also {
                PreludeHandler(semanticContext).run()        // Phase 1
                StructHandler(semanticContext).run()         // Phase 2
                StructSizeFunctionGenerator().run()          // Phase 3
                FunctionRegistrar(semanticContext).run()     // Phase 4
                FunctionBodyGenerator(semanticContext).run() // Phase 5
            }
            return module.toIRString()
        }
    }
}
```

### Dumper Utilities

`Dumper.kt` provides extension functions for outputting IR:

- `dump(irModule: String, outputPath: String)`: Writes IR to file
- `dumpScreen(irModule: String)`: Prints IR to console with colored header

---

## Core Components

### IRContext (Global State)

`IRContext` is the central singleton maintaining all IR generation state:

| Field | Type | Purpose |
|-------|------|---------|
| `module` | `Module` | LLVM module being constructed |
| `enumIntegerLookup` | `Map<EnumValue, Int>` | Enum variant → integer discriminant |
| `functionNameLookup` | `Map<Function, Name>` | Function symbol → IR name |
| `functionLookup` | `Map<Function, Function>` | Semantic function → LLVM function |
| `functionPlans` | `Map<Function, FunctionPlan>` | Function planning metadata |
| `functionRenamers` | `Map<Function, Renamer>` | Per-function name generators |
| `structTypeLookup` | `Map<String, StructType>` | Struct name → LLVM struct type |
| `structSizeFunctionLookup` | `Map<String, Function>` | Struct name → sizeof helper |
| `stringLiteralLookup` | `Map<String, GlobalVariable>` | String deduplication cache |
| `cStringLiteralLookup` | `Map<String, GlobalVariable>` | C-string deduplication cache |

### FunctionEnvironment (Local State)

`FunctionEnvironment` encapsulates per-function generation state:

```kotlin
data class FunctionEnvironment(
    val bodyBuilder: IRBuilder,       // Main code builder
    val allocaBuilder: IRBuilder,     // Alloca instruction builder
    val allocaEntryBlock: BasicBlock, // Entry block for allocas
    val bodyEntryBlock: BasicBlock,   // First body block
    val plan: FunctionPlan,           // Function signature info
    val function: Function,           // LLVM function
    val scope: Scope,                 // Semantic scope
    val renamer: Renamer,             // Name generator
    val locals: ArrayDeque<MutableMap<Variable, Value>>, // Variable slots
    val loopStack: ArrayDeque<LoopFrame>, // Loop context stack
    var terminated: Boolean,          // Block termination flag
    val returnSlot: Value?,           // Optional return value slot
)
```

Key methods:
- `findLocalSymbol(identifier: String)`: Resolves variable by name
- `findLocalSlot(symbol: Variable)`: Gets the alloca slot for a variable

### FunctionPlan

`FunctionPlan` captures the IR-level signature planning for a function:

```kotlin
data class FunctionPlan(
    val symbol: SemanticSymbol.Function,
    val name: Name,
    val type: FunctionType,
    val returnType: SemanticType,
    val returnsByPointer: Boolean,  // True for struct/array returns
    val selfParamIndex: Int?,       // Index of self param (if any)
    val retParamIndex: Int?,        // Index of return pointer param (if any)
)
```

The `FunctionPlanBuilder` constructs plans with special handling for:
- `self` parameters (methods)
- Return-by-pointer for aggregate types
- User-defined parameter names

### GeneratedValue

A simple wrapper pairing an LLVM value with its semantic type:

```kotlin
data class GeneratedValue(
    val value: Value,
    val type: SemanticType,
)
```

### LoopFrame

Captures loop context for break/continue handling:

```kotlin
data class LoopFrame(
    val breakTarget: BasicBlock,
    val continueTarget: BasicBlock,
    val resultSlot: Value? = null,  // For loop expressions with values
)
```

---

## IR Generation Pipeline

### Phase 1: Prelude Setup (`PreludeHandler`)

**Purpose**: Declare external prelude functions for runtime support.

**Process**:
1. Iterate all functions in the prelude scope
2. Build `FunctionPlan` for each prelude function
3. Register as external declaration (no body)
4. Register the `prelude.struct.String` type

**Key Functions Declared**:
- Print/println functions (`__c_print_int`, `__c_println_str`, etc.)
- Input functions (`__c_get_int`, `__c_get_str`)
- Memory utilities (`aux.func.memfill`)
- String utilities (`__c_strlen`, `__c_strcpy`, `aux.func.itoa`)

### Phase 2: Struct Registration (`StructHandler`)

**Purpose**: Define all user struct types in the LLVM module.

**Process**:
1. Collect all structs from the scope tree (excluding prelude)
2. **First pass**: Register opaque struct types
3. **Second pass**: Complete struct bodies with field types

**Empty Struct Handling**: Empty structs receive a single `i8` field for well-definedness.

```kotlin
val fieldTypes = buildList {
    for ((fieldName, fieldTypeSlot) in symbol.fields) {
        add(fieldType.toStorageIRType())
    }
}.ifEmpty { listOf(TypeUtils.I8) }
```

### Phase 3: Size Function Generation (`StructSizeFunctionGenerator`)

**Purpose**: Generate `aux.func.sizeof.<StructName>` helper functions.

**Implementation**: Uses GEP + ptrtoint trick to compute struct size at runtime:

```kotlin
val scratch = builder.insertAlloca(structType, null)
val baseInt = builder.insertPtrToInt(scratch, size64Type, null)
val gep = builder.insertGep(structType, scratch, listOf(getIntConstant(1)), null)
val nextInt = builder.insertPtrToInt(gep, size64Type, null)
val diff = builder.insertSub(nextInt, baseInt, null)
val size32 = builder.insertTrunc(diff, size32Type, null)
builder.insertRet(size32)
```

### Phase 4: Function Registration (`FunctionRegistrar`)

**Purpose**: Register all user-defined functions with their signatures.

**Process**:
1. Traverse AST visiting `FunctionItemNode`
2. Resolve function symbol from scope
3. Build `FunctionPlan` with proper naming
4. Register function in LLVM module
5. Store plan in `IRContext.functionPlans`
6. **Continue visiting the function body** to register any nested functions

**Naming Convention**:
- Free functions: `user.func.<name>`
- Methods: `user.func.<Type>.<name>`
- Nested functions: `user.func.<outer>$<inner>`

**Nested Function Support**: After registering a function, the registrar calls `visitFunctionInternal(node)` to continue traversing the function's body. This ensures nested functions defined inside another function body are also registered.

### Phase 5: Function Body Generation (`FunctionBodyGenerator`)

**Purpose**: Emit LLVM IR instructions for function bodies.

**Process**:
1. Create entry block (for allocas) and body block
2. Bind function parameters to local slots
3. Emit statements and expressions
4. Handle function return

**Nested Function Support**: When encountering an `ItemStatementNode` containing a function definition, the `emitItemStatement` method recursively calls `visitFunctionItem` to generate the nested function's body.

**Two-Block Pattern**:
```
entry_block:
    ; All allocas go here
    br body_block

body_block:
    ; Actual code starts here
```

This ensures allocas are always in the entry block for proper lifetime management.

---

## Type System Mapping

### `toIRType()` - Value Types

Maps semantic types to LLVM IR types for value operations:

| SemanticType | LLVM Type | Notes |
|--------------|-----------|-------|
| `I32Type`, `U32Type`, `ISizeType`, `USizeType` | `i32` | All 32-bit integers |
| `BoolType` | `i1` | Single bit |
| `CharType` | `i8` | ASCII character |
| `StrType`, `CStrType` | `ptr` | Opaque pointer |
| `UnitType`, `NeverType` | `i8` | Padded for consistency |
| `ArrayType` | `ptr` | Pointer to array storage |
| `ReferenceType` | `ptr` | Pointer to referent |
| `StructType` | `ptr` | Pointer to struct storage |
| `EnumType` | `i32` | Discriminant value |

### `toStorageIRType()` - Storage Types

Maps semantic types to LLVM types for memory allocation:

| SemanticType | LLVM Type | Notes |
|--------------|-----------|-------|
| `StructType` | `%user.struct.Name` | Named struct type |
| `ArrayType` | `[N x ElementType]` | Fixed-size array |
| Others | Same as `toIRType()` | Primitive storage |

### Helper Functions

- `unwrapReferences()`: Strip reference wrappers to get base type
- `requiresReturnPointer()`: True for struct/array returns (not through registers)

---

## Visitor Components

### ExpressionEmitter

**File**: `ExpressionEmitter.kt` (~1060 lines)

The largest and most complex visitor, handling all expression code generation.

**Constructor Dependencies**:
```kotlin
class ExpressionEmitter(
    ctx: SemanticContext,
    scopeMaintainer: ScopeMaintainerCompanion,
    staticResolver: StaticResolverCompanion,
    emitBlock: (BlockExpressionNode) -> GeneratedValue?,
    resolveVariable: (Variable) -> GeneratedValue,
    declareVariable: (Variable, Name?) -> Value,
    emitFunctionReturn: (FunctionPlan, GeneratedValue?) -> Unit,
    currentEnv: () -> FunctionEnvironment,
    addBlockComment: (CompilerPointer, String) -> Unit,
)
```

**Expression Categories**:

| Category | Expressions |
|----------|-------------|
| Literals | Integer, bool, char, string, C-string |
| Paths | Variable access, self, function references |
| Structures | Struct literal, field access |
| Arrays | Array literal, index access |
| Calls | Function calls, method calls |
| Operators | Infix (+, -, *, /, comparisons), prefix (-, !) |
| References | Reference creation (`&`), dereference (`*`) |
| Control Flow | Return, break, continue (delegated) |
| Type Casts | `as` expressions |

**Key Methods**:

1. `emitExpression(node)`: Main dispatch method
2. `emitLiteral(node)`: Literal value generation
3. `emitPath(node)`: Path resolution (variables, functions)
4. `emitStructLiteral(node)`: Struct construction
5. `emitField(node)`: Field access with GEP
6. `emitIndex(node)`: Array indexing with GEP
7. `emitArrayLiteral(node)`: Array allocation and initialization
8. `emitCall(node)`: Function/method call generation
9. `emitInfix(node)`: Binary operator generation
10. `emitReference(node)`: Address-of operation
11. `emitDereference(node)`: Pointer dereference

**Special Handling**:

- **Short-circuit evaluation**: `&&` and `||` use conditional branches
- **Compound assignment**: `+=`, `-=`, etc. load-modify-store
- **Aggregate copies**: Structs/arrays copied by value when needed
- **String literals**: Deduplicated via global variable cache

### ControlFlowEmitter

**File**: `ControlFlowEmitter.kt` (~130 lines)

Handles structured control flow constructs.

**Methods**:

1. `emitIf(node)`: If-else-if chains with phi-like result slots
2. `emitWhile(node)`: While loops with head/body/exit blocks
3. `emitLoop(node)`: Infinite loops with break/continue
4. `emitBreak(node)`: Jump to loop exit block
5. `emitContinue()`: Jump to loop header

**If-Expression Pattern**:
```
guard_block_0:
    ; Evaluate condition
    br i1 %cond, label %then_0, label %guard_block_1

then_0:
    ; Execute then branch
    store result to %result_slot
    br label %merge

guard_block_1:
    ; Next else-if or else branch
    ...

merge:
    ; Load result from %result_slot
```

### FunctionBodyGenerator

**File**: `FunctionBodyGenerator.kt` (~300 lines)

Orchestrates function body emission.

**Key Responsibilities**:

1. Create function entry structure
2. Bind parameters to local slots
3. Manage lexical scopes with `withScope()`
4. Emit statements via `emitStatement()`
5. Handle let bindings with `emitLet()`
6. Handle nested function definitions via `emitItemStatement()`
7. Finalize function return with `emitFunctionReturn()`

**Nested Function Handling**:
```kotlin
private fun emitItemStatement(node: StatementNode.ItemStatementNode) {
    when (val item = node.item) {
        is ItemNode.FunctionItemNode -> {
            // Nested function: generate its body
            visitFunctionItem(item)
        }
        else -> Unit // Other item types don't generate code here
    }
}
```

**Parameter Binding Process**:
```kotlin
fun bindParameters(symbol, plan) {
    // 1. Bind self parameter (if method)
    plan.selfParamIndex?.let { idx ->
        val selfSymbol = scope.variableST.resolve("self")
        val slot = declareVariable(selfSymbol)
        bodyBuilder.insertStore(args[idx], slot)
    }
    
    // 2. Bind user parameters
    val binder = ParameterBinder(scope)
    paramSymbols.forEachIndexed { idx, sym ->
        val slot = declareVariable(sym)
        bodyBuilder.insertStore(userArgs[idx], slot)
    }
}
```

**Let Statement Handling**:
- Extract symbols from pattern
- Allocate stack slot for each binding
- Store initializer value (with aggregate copy if needed)

---

## Support Utilities

### Name

**File**: `Name.kt`

Provides debug-friendly, unique identifiers following the pattern:
`<holder>.<type>.<name>(.<serial>)`

**Factory Methods**:

| Method | Example Output |
|--------|----------------|
| `ofStruct("Point")` | `user.struct.Point` |
| `ofFunction(symbol, "Vec")` | `user.func.Vec.add` |
| `ofVariable(symbol, renamer)` | `user.var.count.0` |
| `auxSelf()` | `aux.var.self` |
| `auxReturn()` | `aux.var.ret` |
| `block(renamer)` | `aux.block.3` |
| `blockResult(renamer)` | `aux.var.blockret.2` |
| `auxTemp("ptr", renamer)` | `aux.var.ptr.1` |
| `auxTempGlobal("str")` | `aux.var.str.5` |

### Renamer

**File**: `Renamer.kt`

Simple serial number generator for SSA-compliant naming:

```kotlin
class Renamer {
    private val counters = mutableMapOf<String, Int>()
    
    fun next(base: String): Int {
        val current = counters.getOrDefault(base, -1) + 1
        counters[base] = current
        return current
    }
    
    fun clear(base: String) { counters.remove(base) }
    fun clearAll() { counters.clear() }
}
```

Each function gets its own Renamer to restart numbering per function body.

### Pattern Utilities

**ParameterNameExtractor**: Extracts user-friendly names from function parameter patterns for use in LLVM parameter names.

**ParameterBinder**: Maps function parameters to their semantic symbol bindings, handling pattern destructuring.

---

## Prelude System

### C Runtime Functions

The prelude provides essential runtime support via C functions:

```c
// Output
void __c_print_int(int32_t value);
void __c_println_int(int32_t value);
void __c_print_str(const char* str);
void __c_println_str(const char* str);

// Input
int32_t __c_get_int();
void __c_get_str(char* buffer);

// String utilities
int32_t __c_strlen(const char* str);
void __c_strcpy(char* dest, const char* src);
void __c_itoa(int32_t value, char* str);

// Memory
void __c_memfill(char* dest, const char* src, 
                 int32_t element_size, int32_t element_count);
```

### Wrapper Functions

LLVM IR wrappers provide cleaner interfaces:

- `aux.func.memfill`: Memory fill for array initialization
- `aux.func.itoa`: Integer to string conversion

### String Type

The `prelude.struct.String` type is registered as:
```llvm
%prelude.struct.String = type { ptr }
```

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     SemanticContext                             │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────────────┐  │
│  │  Scope Tree  │  │ Symbol Tables│ │ ExpressionTypeMemory  │  │
│  └──────┬───────┘  └──────┬──────┘  └───────────┬───────────┘  │
└─────────┼─────────────────┼─────────────────────┼──────────────┘
          │                 │                     │
          ▼                 ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      IRConstructor                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      IRContext                            │  │
│  │  • Module            • functionLookup                     │  │
│  │  • structTypeLookup  • functionPlans                      │  │
│  │  • stringLiteralLookup                                    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Phase 1: PreludeHandler ──────► External function declarations │
│           │                                                     │
│  Phase 2: StructHandler ───────► Named struct type definitions  │
│           │                                                     │
│  Phase 3: StructSizeFunctionGenerator ─► sizeof helper functions│
│           │                                                     │
│  Phase 4: FunctionRegistrar ───► Function declarations          │
│           │                                                     │
│  Phase 5: FunctionBodyGenerator                                 │
│           │                                                     │
│           ├── FunctionEnvironment (per-function state)          │
│           │   • bodyBuilder                                     │
│           │   • allocaBuilder                                   │
│           │   • locals stack                                    │
│           │   • loopStack                                       │
│           │                                                     │
│           ├── ExpressionEmitter ─► Expression IR                │
│           │   • Literals                                        │
│           │   • Operators                                       │
│           │   • Calls                                           │
│           │   • Field/Index access                              │
│           │                                                     │
│           └── ControlFlowEmitter ─► Control flow IR             │
│               • If/else chains                                  │
│               • While loops                                     │
│               • Loop/break/continue                             │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                     LLVM IR String Output                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Algorithms

### 1. Aggregate Value Copying

When structs or arrays are assigned or passed by value:

```kotlin
fun storeValueInto(targetType, sourceValue, destinationPtr, label) {
    val underlying = targetType.unwrapReferences()
    val storedValue = if (targetType.requiresAggregateStorageCopy()) {
        // Load the aggregate from source pointer
        bodyBuilder.insertLoad(underlying.toStorageIRType(), 
                               sourceValue.value, temp("$label.copy"))
    } else {
        sourceValue.value
    }
    bodyBuilder.insertStore(storedValue, destinationPtr)
}
```

### 2. Array Initialization with Memfill

Arrays use a chunked memfill strategy:

```kotlin
// 1. Store first element(s)
node.elements.forEachIndexed { index, elementExpr ->
    val value = emitExpression(elementExpr)
    storeArrayElement(storageType, storage, index, value, elementType)
}

// 2. If repeated pattern, memfill the rest
if (repeat > 1) {
    val tailPtr = builder.insertGep(storage, [0, elementCount])
    callMemfill(tailPtr, destPtr, chunkSize, repeat - 1)
}
```

### 3. Method Receiver Preparation

For method calls, the receiver undergoes reference unwrapping:

```kotlin
fun prepareMethodReceiver(baseExpr, initialValue): Pair<GeneratedValue, SemanticType> {
    var currentValue = initialValue
    var currentType = expressionTypeMemory.recall(baseExpr)
    
    while (currentType is ReferenceType) {
        val inner = currentType.type.get()
        // Stop at pointer types (don't load struct pointers)
        if (inner !is ReferenceType && inner.toIRType() == PTR) {
            break
        }
        val loaded = bodyBuilder.insertLoad(inner.toIRType(), currentValue.value)
        currentValue = GeneratedValue(loaded, inner)
        currentType = inner
    }
    
    return currentValue to currentType.unwrapReferences()
}
```

### 4. Call Argument Preparation

Aggregate arguments are copied to fresh allocations:

```kotlin
fun prepareCallArguments(arguments, targetSymbol): List<Value> {
    return arguments.mapIndexed { index, argument ->
        val paramType = targetSymbol.funcParams[index].type.get()
        if (paramType.requiresAggregateStorageCopy()) {
            copyAggregateArgument(argument, paramType, index)
        } else {
            argument.value
        }
    }
}
```

---

## Module Interaction Summary

| Module | Depends On | Provides To |
|--------|------------|-------------|
| `IRConstructor` | All others | Entry point, pipeline orchestration |
| `IRContext` | None | Global state for all visitors |
| `PreludeHandler` | `IRContext`, `SemanticContext` | External function declarations |
| `StructHandler` | `IRContext`, `SemanticContext` | Struct type definitions |
| `StructSizeFunctionGenerator` | `IRContext` | sizeof helper functions |
| `FunctionRegistrar` | `IRContext`, `SemanticContext` | Function declarations |
| `FunctionBodyGenerator` | All support modules | Function body IR |
| `ExpressionEmitter` | `FunctionEnvironment`, `IRContext` | Expression IR |
| `ControlFlowEmitter` | `FunctionEnvironment` | Control flow IR |
| `Name`, `Renamer` | None | Naming utilities |
| `TypeConversion` | `IRContext` | Type mapping |
| `FunctionPlan`, `FunctionPlanBuilder` | `Name`, `Renamer` | Function signature planning |
| `GeneratedValue` | None | Value + type wrapper |
| `FunctionEnvironment`, `LoopFrame` | None | Local generation state |

---

## Related Documentation

- **[ir-gen.md](ir-gen.md)**: Design guidelines and conventions for IR generation
- **[project-structure.md](project-structure.md)**: Overall compiler architecture
- **[compiler-pipeline.md](compiler-pipeline.md)**: Complete compilation pipeline

---

*Generated: November 2025*
