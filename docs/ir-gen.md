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
- Enums are represented as `i32` (discriminant only, C++ style).
- Compound types are represented as pointers (`ptr`).
- Other types (Unit Type, Never Type) are padded with `i8`.
    -> This is to ensure that structs with unit fields are well-defined.
- Traits have been removed.

All structs are defined globally as `types` in llvm-ir at the beginning. If a struct type is empty, it is filled with a single `i8` field to ensure well-definedness.

## Naming Conventions

The global naming convention for values is `<holder>.<type>.<name>(.<serial>)`.

The serial is calculated by piping the name through a Renamer provided by the LLVM library, and only for variables.

Holders can be:
- `user`, when the value is explicitly defined by the user.
- `prelude`, when the value is defined in the prelude.
- `aux`, special temporary values specified in the documentation. It is named to facilitate debugging.

Types can be:
- `struct`, for struct types.
- `func`, for functions.
- `var`, for variables.
- `block`, for basic blocks.

Constants are evaluated inline and do not have corresponding names.

Temp values should be declared by using null for the name param. This will generate anonymous variables instead of named ones.

The name of blocks are generated should mirror the scope tree.

## Function Generation Guide

### Blocks

Blocks in rust are handled as basic blocks in LLVM IR that modify an aux value (corresponding to the trailing expression's value). For example:

```
let a = {
    if (b > 0) {
        b;
    } else {
        0
    }
};
```

Translates to something like:

```
define i32 @func(...) {
entry:
    %aux.var.0 = alloca i32
    br label %if-then-else
    %cond = icmp sgt i32 %user.var.b.0, 0
    br i1 %cond, label %then-block, label %else-block

then-block:
    store i32 %user.var.b.0, i32* %aux.var.0
    br label %end-if

else-block:
    store i32 0, i32* %aux.var.0
    br label %end-if

end-if:
    %user.var.a.0 = load i32, i32* %aux.var.0
}
```

The block names should be changed according to the scope tree, for demonstration purposes only in the example.

### Params

Two optional params are prepended to the function signature:

- `ptr aux.var.self` - for methods, the self parameter
- `ptr aux.var.ret` - for non-unit returning functions, the return value pointer

If a function returns integers directly, the return value pointer is not added.
