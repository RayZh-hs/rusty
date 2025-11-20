# IR Generation Design

## IR Version Overview

The IR system embraces the new simplified `ptr` approach, without pointer type distinctions. Use this system throughout the IR generation phase.

## Type System Overview

The `SemanticType` system has a corresponding IR type system:
- String types are all represented as pointers to i8 (`ptr`).
- Other primitive types are cast to integers:
    - I32, U32, ISize, USize -> `i32`
    - Bool -> `i1`
    - Char -> `i8`
- Compound types are represented as pointers (`ptr`).
- Other types (Unit Type, Never Type) are padded with `i8`.
    -> This is to ensure that structs with unit fields are well-defined.

All structs are defined globally as `types` in llvm-ir at the beginning. If a struct type is empty, it is filled with a single `i8` field to ensure well-definedness.

