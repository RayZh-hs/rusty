# Future

This document outlines the planned future developments for the rusty compiler project. It also documents possible implementations for work-in-progress features.

## Current Working Focus

- **Semantic Analysis Enhancements**: Reserving more information from the semantic analysis phase using Memory Banks to facilitate IR generation.
- **Intermediate Representation (IR) Generation**: Developing a robust IR generation module that takes the semantic context and produces IR code.

## Feature Design

### Intermediate Representation (IR)

The IR generation module generates IR while visiting the AST-tree and referencing semantic context. It relies on the following components:

- `scopeTree`: Provides scope information for global objects, including constant values and type definitions.
- `expressionTypeMemory`: Stores type information for in-function-body objects.
- `derefCountMemory`: Tracks auto-dereference counts for reference types.
- `letRenamingMemory`: Maps `let` statement names to renamed counterparts for uniqueness.

The IR generation process involves:

1. **Declaring Types**: All structs are declared at the beginning of the IR code.
2. **Function Declarations**: All functions are declared at top-level, and methods are raised from impl blocks to top-level functions with `STRUCT.METHOD` names.
3. **Function Definitions**: Each function is traced and the body filled in with IR instructions. This step is done while a visitor traverses the AST-tree, from each function declaration via the lookup table.

For more details entailing the design of the IR generation phase, refer to the [IR Generation Design Document](./ir-gen.md).