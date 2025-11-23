# IR Generation Implementation Notes

This document captures the concrete design for the LLVM IR generator. It translates
the semantic context into an LLVM `Module` by visiting the AST and emitting IR with
`space.norb.llvm` primitives. The plan balances correctness with debuggability
while sticking to the naming and pointer model in `docs/ir-gen.md`.

## High-level pipeline

1. **Reset IR context** – start every run with a fresh `Module`, builder helpers,
   lookup maps (struct types, function names, cached globals), and a cleared
   renamer cache.
2. **Struct registration** – walk the scope tree (skipping prelude when requested)
   to declare opaque LLVM structs, then fill their fields. Empty structs get an
   `i8` filler to stay well-defined. Field types use the canonical LLVM storage
   type (structs, nested arrays, etc.) so that later passes see the true layout.
3. **Struct sizeof helpers** – after struct bodies are finalized, generate
   `aux.func.sizeof.<StructName>` helpers that return the runtime size in bytes
   using the `getelementptr %Struct, ptr null, i32 1` trick.
4. **Prelude declarations** – declare external functions for prelude entries
   (`print`, `println`, `printInt`, `printlnInt`, `getString`, `getInt`, `exit`)
   so calls can be lowered without providing bodies.
5. **Function collection** – traverse items to create LLVM function declarations
   for every user function/method. Map the semantic symbol to a mangled name and
   a `Function` instance stored in `IRContext`.
6. **Body generation** – revisit functions with bodies and emit instructions:
   build entry block, bind parameters, materialize locals/temps, translate
   expressions/statements, and stitch control flow.
7. **Dump** – complete all blocks and emit `module.toIRString()`.

## Naming strategy

All IR identifiers follow `<holder>.<type>.<name>(.<serial>)`. Holders are
`user` (user-defined), `prelude` (built-ins), or `aux` (compiler-generated).
Types are `struct`, `func`, `var`, or `block`. Serials come from the LLVM
`Renamer` and only apply where SSA demands uniqueness.

Principles and helpers:
- Cache names in `IRContext` so downstream passes reuse the same strings.
- Use the `Renamer` for anything that might need disambiguation across SSA
  values: basic blocks, user parameters, lets, and aux variables with names.
- Call `Renamer.clear(baseName)` when starting a new function to reset block and
  variable counters for that function scope.
- Structs: `user.struct.<name>` (or `prelude.struct.<name>`), no serial.
- Functions:
  - Inherent impl: `user.func.<Type>.<function_name>`
  - Free function: `user.func.<function_name>`
  - Nested functions: join with `$`, e.g. `user.func.Type.outer$inner`
  - Prelude/built-ins swap the `user` holder for `prelude`.
- Parameters/locals: `user.var.<ident>.<serial>` via the renamer.
- Special parameters: `aux.var.self`, `aux.var.ret` (no serial).
- Block-return temps: `aux.var.blockret.<serial>`; other intermediates pass
  `null` to emit anonymous temps.
- Basic blocks: `aux.block.<serial>`, with the counter cleared per function.

## Return/value model

- IR values use `SemanticType.toIRType()` for the in-SSA representation. Structs,
  arrays, strings, and references all appear as `ptr` in SSA form; unit/never
  lower to `i8` padding with value 0. Whenever the bytes of an aggregate are
  needed (struct copies, array fills), we materialize canonical storage based on
  the LLVM struct/array types tracked in `IRContext`.
- Every local variable gets its own `alloca` of **value type** (so structs are
  stored as `ptr`, integers as `i{1|8|32}`), enabling mutation and `&` borrows.
- Struct payloads live in separately allocated storage of the concrete struct
  type; the `ptr` to that storage is the value stored in the variable slot.
- Function returns:
  - If the lowered return type is an integer (`i1/i8/i32`) → returned directly.
  - Otherwise, prepend `ptr aux.var.ret` parameter and return `void`, storing the
    real value through this pointer.
  - Methods prepend `ptr aux.var.self` before the optional return pointer.

## Control-flow lowering

- **Blocks** allocate an `aux` slot (only when the block can produce a value) to
  hold the trailing expression result. Branches store into the aux slot and jump
  to the merge block. Names use `aux.var.blockret.<serial>`.
- **If** lowers to the standard `cond → then/else → merge` shape, writing the
  chosen branch value into the aux slot.
- **Loop/while** create `guard`, `body`, and `exit` blocks. `break` jumps to
  `exit` (optionally after storing the break value into the loop aux slot);
  `continue` jumps back to the guard block.
- **Logical &&/||** short-circuit via control flow to avoid eager evaluation.

## Expression lowering summary

- **Literals**: mapped to LLVM constants using `BuilderUtils` and cached globals
  for string/cstring literals (null-terminated arrays with `getelementptr` to
  the first element). Constants are inlined and unnamed.
- **Paths**: resolve through the semantic scope; variables load from their slot,
  consts translate to IR constants, functions fetch the pre-registered `Function`.
- **Struct literals**: allocate concrete struct storage, set each field with GEP
  + store, then yield the pointer to that storage.
- **Array literals**: allocate storage for the final array plus a temporary
  pattern buffer, populate the buffer once, and call `aux.func.memfill` to copy
  the pattern across the destination. Struct element sizes come from the generated
  `aux.func.sizeof.*` helpers.
- **Binary ops**: arithmetic via `add/sub/mul/sdiv`, comparisons via `icmp`
  signed predicates, bitwise via `and/or/xor`. Assignment variants load/compute
  then store back, yielding unit.
- **Casts**: support integer truncation/extension as needed to reach the target
  width, plus bitcasts to `ptr` for reference-like casts.
- **References/& and *:** `&expr` stores the address of an l-value; `*expr` loads
  from the pointer value. Auto-deref counts from semantic context are honored
  when available.
- **Comments**: emit full-line comments at each basic block entry with
  `[line:column] <label>` text, and inline comments for `let` bindings,
  following the guidance in `docs/ir-gen.md`.

## State helpers

- **IRContext**: now resettable; holds module, struct lookup, function maps,
  enum discriminants, renamer, and literal caches.
- **GenerationEnvironment** (per function): current builder, function, return
  policy, map from `SemanticSymbol.Variable` to its `alloca`, pending break
  targets for loops, and the current block name stack for naming.
- **Semantic bridges**: use `ScopeMaintainerCompanion` to walk the scope tree in
  the same order as semantic passes; reuse `StaticResolverCompanion` where
  possible to resolve constants and paths.

## Open items / stretch goals

- Add unsigned-friendly div/rem ops when the LLVM binding exposes them.
- Expand match lowering once the language re-enables `match`.
- Model prelude implementations more faithfully (currently extern stubs).
