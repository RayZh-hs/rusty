# IR Generation Implementation Guide

This guide translates the core ideas from `docs/ir-gen.md` into a practical, step-by-step playbook you can follow while wiring new lowering logic or reviewing generated LLVM IR.

## 1. Know the IR Flavor
- **Single pointer type**: everything that behaves like an address uses `ptr`; there are no typed pointers.
- **Primitive lowering**: map ints (i32/u32/isize/usize) to `i32`, bool to `i1`, char to `i8`, strings to `ptr`.
- **Composite lowering**: structs mirror their LLVM layout (empty structs become `{ i8 }`), arrays use the LLVM array syntax, enums collapse to `i32` discriminants, references become `ptr`.
- **Unit/never padding**: emit an `i8 0` placeholder so structs containing unit fields still have layout.

## 2. Pre-declare Everything
Before emitting any function bodies:
1. Define every struct as a global LLVM `type` (with the padding rule above).
2. Emit one `aux.func.sizeof.<Struct>` helper per struct by using the `getelementptr null, 1` trick plus `ptrtoint`.
3. Link in the prelude helpers: `aux.func.memfill` for copying/filling memory and `aux.func.itoa` for integer printing. Treat `memfill` as the default for array/struct initialization instead of manual element loops.

## 3. Naming Cheat Sheet
| Thing | Pattern | Notes |
| --- | --- | --- |
| Struct type | `user.struct.<name>` | Cached in the IR context.
| Function (free) | `user.func.<name>` | Nested functions join with `$` (e.g. `outer$inner`).
| Function (impl) | `user.func.<impl_type>.<name>` | `self` always lives in `%aux.var.self`.
| Basic block | `aux.block.<serial>` | Reset the serial per function via the Renamer.
| Variables / lets | `user.var.<name>.<serial>` | Serial managed by the Renamer; inline `; [line:col] let name` comment.
| Block temporaries | `aux.var.blockret.<serial>` | Used for `{ ... }` expression results.
| Scratch temps | anonymous (`null` name) | LLVM handles the final `%` identifiers.

Always cache generated names so that SSA renaming, phi fixing, and debugging remain predictable. Call `Renamer.clear("base")` whenever you enter a new scope that should restart numbering.

## 4. Function Signatures and Special Parameters
- All functions optionally start with `ptr %aux.var.self` when defined inside an `impl`.
- Add `ptr %aux.var.ret` when the return type is not an immediate scalar (`i1/i8/i32`). The caller allocates space and passes it in, turning complex returns into out-parameters.
- Scalar-returning functions skip the `ret` pointer and `ret` the value directly.

## 5. Lowering Blocks and Control Flow
1. **Allocate block result**: when a block is an expression, create `%aux.var.blockret.N = alloca <type>`.
2. **Branching**: every `if/else`, loop, or match case owns its own `aux.block.<N>` with a leading comment `; [line:col] block <label>`.
3. **Stores**: write the chosen branch result into the block result slot, then branch to the merge block.
4. **Read back**: after the merge, `load` the block result into the destination variable (often a `let`).

This pattern keeps SSA tidy and mirrors the Rust semantics of block expressions.

## 6. Memory Operations and Arrays
- Prefer `aux.func.memfill(dst, src, elsize, elcount)` to copy arrays/structs or broadcast a value.
- For array initialization with a repeated literal, store one element, then call `memfill` with `elcount = array_len`.
- For struct copies or move semantics, compute `size = sizeof(struct)` via the generated helper and pass it as `elsize`; keep `elcount = 1`.

## 7. Comments for Debuggability
- Each basic block starts with a comment indicating source position and role (then/else/loop body, etc.).
- Every `let` store gets an inline comment `; [line:col] let <name>` so that IR dumps cross-reference to the source quickly.
- When in doubt, add brief comments near tricky lowering steps (e.g., manual `ptrtoint` size queries) to save future debuggers time.

## 8. Quick Checklist When Implementing a New Construct
1. **Types available?** Ensure all referenced struct/array types are declared.
2. **Names registered?** Use the Renamer helpers; reset scopes properly.
3. **Return convention handled?** Decide whether you need `%aux.var.ret`.
4. **Block values stored?** Allocate/load via `aux.var.blockret.*` for expression blocks.
5. **Memory copies?** Reach for `aux.func.memfill` before inventing loops.
6. **Diagnostics?** Sprinkle the block/let comments for source traceability.

Follow this sequence and the resulting LLVM IR stays consistent with the rest of the pipeline while remaining readable for future contributors.
