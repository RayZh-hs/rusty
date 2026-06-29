# Rusty Optimization System

This document describes every optimization Rusty runs, in two groups:

1. **IR optimizations** — transformations over the LLVM-style SSA IR, orchestrated by
   [`IROptimizer`](../src/main/kotlin/rusty/opt/IROptimizer.kt).
2. **ASM optimizations** — transformations over the emitted RISC-V program, run after instruction
   selection.

Each entry gives the pass's purpose, a small before/after example, and a step-by-step description of
the algorithm. Passes live in either `rusty.opt.passes` (project-specific) or
`space.norb.llvm.transformation.presets` (vendored LLVM-in-Kotlin library, in `vendor/llvm`).

---

## The IR pass pipeline

`IROptimizer.run` builds an `AnalysisManager` (which lazily computes and caches analyses such as the
dominator tree, predecessor map, and use-def chains) and runs the passes below **in order**. Each
pass reports whether it changed anything and, if so, invalidates the cached analyses, so later passes
always see fresh dominator/predecessor information.

```
 1. SizeInliningPass                    13. LoopInvariantCodeMotionPass
 2. FunctionInliningPass (threshold 40) 14. LoopAddressReductionPass
 3. SmallMemcopyLoweringPass            15. InstCombineCleanupPass
 4. InstCombineCleanupPass              16. LoopCounterPromotionPass
 5. PointerSlotForwardingPass           17. InstCombineCleanupPass
 6. ScalarReplacementOfAggregatesPass   18. CFGSimplifyPass
 7. Mem2RegPass                         19. AggressiveDeadCodeEliminationPass
 8. AggressiveDeadCodeEliminationPass   20. InstCombineCleanupPass
 9. InstCombineCleanupPass
10. IdenticalGepReductionPass
11. GlobalValueNumberingPass
12. InstCombineCleanupPass
```

The ordering is deliberate. Inlining and memcpy lowering expose a lot of redundant memory traffic;
SROA and Mem2Reg promote that memory into SSA registers; the GVN and InstCombine cleanups then
collapse the now-register-resident redundancies; and the loop passes run last, once the loop bodies
are in their cleanest register form. `InstCombineCleanupPass` is interleaved repeatedly because almost
every structural pass leaves behind trivially-foldable instructions.

---

## IR optimizations

### 1. SizeInliningPass
*File: `rusty/opt/passes/SizeInliningPass.kt`*

**Purpose.** The frontend emits an auxiliary function `aux.func.sizeof.<Struct>` wherever a program
needs the byte size of a struct. This pass replaces every call to such a helper with a compile-time
`i32` constant and deletes the helper.

**Example.**
```llvm
; before
%n = call i32 @aux.func.sizeof.Point()
; after  (Point is { i32, i32 } -> 8 bytes)
; %n is replaced everywhere by i32 8, and @aux.func.sizeof.Point is deleted
```

**Algorithm.**
1. Collect all functions whose name starts with `aux.func.sizeof.`.
2. For each, strip the prefix to get the bare struct name and resolve the registered struct type
   (`module.getNamedStructType`, falling back to a suffix match against `.<name>`).
3. If the type resolves and is not opaque, compute its size with the layout utility
   (`getSizeInBytes`) and build an `i32` constant.
4. `replaceAllUsesWith(constant)` on every `CallInst` to the helper, then remove those call
   instructions from their blocks.
5. If no callers remain, delete the helper function from the module.

---

### 2. FunctionInliningPass
*File: `vendor/.../presets/FunctionInliningPass.kt` — configured in `IROptimizer` with a 40-instruction threshold*

**Purpose.** Inline small, non-recursive callees into their callers so that the subsequent
SROA / Mem2Reg / GVN / InstCombine passes can collapse the parameter/return memory traffic into
register-resident values.

**Example.**
```rust
fn add(a: i32, b: i32) -> i32 { a + b }
fn main() { let x = add(1, 2); ... }
// after inlining, `add`'s body is spliced into main; the call becomes the
// inlined block(s) plus a continuation, and %x sources directly from a+b.
```

**Algorithm.**
1. Build the call graph, condense it into a DAG of strongly-connected components (Tarjan), and visit
   the SCCs in **reverse topological order** — so a function is processed only after everything it
   calls, leaving its callees already inlined before it is. Within an SCC the order is arbitrary, but
   that never matters: recursive calls are rejected anyway (step 2).
2. A call is inlinable when all of these hold: it is a direct call (not through a function pointer);
   the callee has a body (not just a declaration); the callee is not marked `__no_inline`; the callee
   does not depend on itself directly or transitively (rules out self- and mutual recursion); and the
   callee is under 40 IR instructions (the threshold set in `IROptimizer`).
3. To inline a call:
   - Map each callee parameter to the corresponding argument value.
   - Clone every callee basic block into a freshly-named block in the caller; create phi
     placeholders first, then clone the bodies, then fill in phi operands (two-phase to handle
     back-edges).
   - Split the caller block at the call: instructions after the call move into a new continuation
     block; the caller block branches to the inlined entry.
   - `return` instructions in the clone become unconditional branches to the continuation, recorded
     as "return sites".
   - If the call produced a value: a single return site forwards its value directly; multiple return
     sites are merged with a phi in the continuation block. `replaceAllUsesWith` rewrites the call's
     users.
4. Names are kept unique with incrementally-maintained name sets to avoid O(calls × callerSize)
   rescans.

---

### 3. SmallMemcopyLoweringPass
*File: `rusty/opt/passes/SmallMemcopyLoweringPass.kt`*

**Purpose.** The frontend models a small fixed-size aggregate copy as a call
`aux.func.memfill(dst, src, size, 1)`. This pass turns such a call into a single scalar load/store
pair (one wide integer move), which later passes can further forward and coalesce.

**Example.**
```llvm
; before  (copy 8 bytes, repeat count 1)
call void @aux.func.memfill(ptr %dst, ptr %src, i32 8, i32 1)
; after
%c.load = load i64, ptr %src   ; "lowered small memcpy"
store i64 %c.load, ptr %dst    ; "lowered small memcpy"
```

**Algorithm.**
1. For each block, scan instructions for a `CallInst` matching the `aux.func.memfill` shape:
   exactly 4 arguments, repeat count (`arg[3]`) equal to `1`, and a constant size (`arg[2]`) in
   `1..32` bytes.
2. Replace the call with a `load <iN>` from the source pointer followed by a `store <iN>` to the
   destination, where `N = size * 8`. Both carry the comment "lowered small memcpy".
3. Larger copies, non-constant sizes, or repeat counts ≠ 1 are left as calls.

---

### 4. InstCombineCleanupPass  *(runs many times)*
*File: `rusty/opt/passes/InstCombineCleanupPass.kt`*

**Purpose.** Local instruction simplification + constant folding + trivial dead-code removal. This is
the workhorse cleanup pass interleaved throughout the pipeline.

**Examples.**
```llvm
%a = add i32 %x, 0      ->  %a replaced by %x          (identity)
%b = mul i32 %x, 0      ->  %b replaced by i32 0       (annihilator)
%c = sub i32 %x, %x     ->  %c replaced by i32 0
%d = add i32 3, 4       ->  %d replaced by i32 7       (constant fold)
%e = icmp eq i32 %x, %x ->  %e replaced by i1 true
%f = phi [%y, %A], [%y, %B] -> %f replaced by %y       (single-value phi)
```

**Algorithm.** Iterate to a fixpoint (max 8 rounds). Each round:
1. **Simplify** every instruction via `simplifyInstruction`, which dispatches on opcode:
   - **Binary ops:** if both operands are constants, fold (with correct signed/unsigned handling —
     `IntConstant.value` stores the unsigned bit pattern, so signed div/rem/ashr/compare sign-extend
     first). Otherwise apply algebraic identities: `x+0`, `x*1`, `x-0`, `x*0`, `x/1`, `x%1`, `x&-1`,
     `x|0`, `x^0`, `x-x`, `x^x`, shifts by 0, `x&x`, `x|x`, etc.
   - **Casts:** fold `trunc`/`zext`/`sext` of a constant.
   - **ICmp:** `x cmp x` folds by predicate; constant-vs-constant folds with signed/unsigned compare.
   - **Phi:** if all incoming values are either the phi itself or one common value `v`, replace with
     `v` (collapses trivial/self-referential phis).
   A replacement is applied only when its type matches; then `replaceAllUsesWith` + operand detach +
   removal.
2. **Eliminate dead instructions:** walk blocks in reverse; remove any instruction with no uses that
   is "dead-removable" (pure binary/cast/icmp/`getelementptr`, but never terminators, stores, or
   side-effecting calls). Repeats until no more are removed.
3. If a round made no change, stop early.

---

### 5. PointerSlotForwardingPass
*File: `rusty/opt/passes/PointerSlotForwardingPass.kt`*

**Purpose.** A scalar promotion specialized to **pointer-typed** stack slots that Mem2Reg doesn't
yet handle here: an `alloca` of a pointer that is stored exactly once and only loaded can have all
its loads replaced by the stored pointer value, removing the slot.

**Example.**
```llvm
%p = alloca ptr
store ptr %src, ptr %p        ; the one and only store
%a = load ptr, ptr %p         ; dominated by the store
... use %a ...
; after: %a replaced by %src; the alloca, store, and load are deleted.
```

**Algorithm.** Per function (using the dominator tree):
1. Find every `alloca` whose allocated type is a pointer.
2. Require exactly one store *into* the slot, at least one load, and **no other uses** (no escape).
3. Require the single store to **dominate every load** (so the loaded value is always the stored
   one). Dominance is checked intra-block by instruction index, inter-block by walking immediate
   dominators.
4. Replace each load's uses with the stored value, then delete the loads, the store, and the alloca.

---

### 6. ScalarReplacementOfAggregatesPass (SROA)
*File: `rusty/opt/passes/ScalarReplacementOfAggregatesPass.kt`*

**Purpose.** Split a struct `alloca` whose individual scalar fields are accessed only through
constant-index GEPs into one independent `alloca` per field. This breaks the aggregate apart so
Mem2Reg can promote each field into an SSA register separately.

**Example.**
```llvm
; before
%s = alloca { i32, i32 }
%f0 = getelementptr {i32,i32}, ptr %s, i32 0, i32 0
store i32 1, ptr %f0
; after
%s.field0.sroa = alloca i32
store i32 1, ptr %s.field0.sroa     ; the GEP is gone; uses point at the scalar alloca
```

**Algorithm.** Per function, for each entry-block struct `alloca`:
1. **Soundness gate:** every direct use of the alloca must be a GEP off the alloca itself. Any other
   use (passed to a call, bitcast for memcpy, the pointer stored somewhere) means the aggregate can
   be observed as a whole, so the pass bails out on that alloca.
2. For each field GEP, compute a `FieldKey` from its constant index path (must start with index 0 and
   be entirely constant).
   - If that field is accessed **only** by loads/stores, allocate a scalar `alloca` of the field's
     type (`<name>.field<i>.sroa`) and mark the GEP for removal.
   - If a field is itself further GEP'd into (nested aggregate), keep it in the struct and retain the
     original alloca.
3. Insert the new field allocas right after the original, rewrite each removed GEP's uses to the
   matching field alloca, and remove the GEPs (and the original alloca if no field needed to stay).

---

### 7. Mem2RegPass
*File: `vendor/.../presets/Mem2RegPass.kt`*

**Purpose.** The core SSA-construction pass: promote `alloca` slots that are only loaded/stored into
SSA virtual registers, inserting phi nodes at control-flow merge points. This is what turns
memory-shaped IR into register-shaped IR.

**Example.**
```llvm
; before
%x = alloca i32
store i32 0, ptr %x
br ... ; then store i32 1 on one path
%v = load i32, ptr %x
; after
%x.phi = phi i32 [ 0, %entry ], [ 1, %then ]
; %v replaced by %x.phi; alloca/loads/stores removed
```

**Algorithm** (textbook Cytron et al. SSA construction):
1. **Find candidates:** an `alloca` is promotable if all its uses are loads/stores directly of that
   alloca (no escaping or aggregate use) and all the blocks that use it are reachable.
2. **Phi insertion:** for each candidate, compute the iterated dominance frontier of its store blocks
   and insert an empty phi placeholder for the alloca at each frontier block. All placeholders are
   inserted up front, sharing one name set so names are stable.
3. **Renaming (single DFS):** walk the dominator tree once in pre-order, keeping a per-alloca stack of
   the value currently held in the slot:
   - On entering a block, push that block's phis as the current value of their allocas.
   - Walk the block: a load of a promoted alloca records a replacement = current value (or a typed
     zero if none); a store pushes its stored value; both are marked for removal.
   - For each successor, add the current value of each alloca as the incoming operand of that
     successor's phi for the corresponding predecessor edge.
   - Recurse into dominator-tree children; on exit, pop the values this block pushed.
4. Rewrite all users that referenced a promoted load to the resolved value, then delete the marked
   allocas/loads/stores.

   The single combined DFS (rather than one walk per alloca) avoids O(allocas × blocks) blowup on
   heavily-inlined functions, while producing identical results.

---

### 8 & 19. AggressiveDeadCodeEliminationPass (ADCE)
*File: `rusty/opt/passes/AggressiveDeadCodeEliminationPass.kt`*

**Purpose.** Mark-and-sweep DCE that can remove **self-referential dead cycles** which the use-count
based cleanup in InstCombine cannot. Mem2Reg routinely creates such cycles — e.g. a loop-carried phi
pair that is reassigned every iteration but never observed.

**Example.**
```llvm
; a phi cycle whose only consumers are each other, observed by nothing:
%i.h  = phi i32 [ 0, %pre ], [ %i.l, %latch ]   ; never read by store/call/branch
%i.l  = add i32 %i.h, 1
; both are removed (use-count DCE can't, because each still "uses" the other)
```

**Algorithm.**
1. **Seed** the live set with all *observable* instructions: terminators, stores, and calls.
2. **Propagate** backwards: pop a live instruction and mark all of its instruction-operands live;
   repeat to a fixpoint with a worklist.
3. **Sweep:** any instruction not in the live set can only be used by other dead instructions; detach
   its operands and remove it.

Soundness: removing a value no live (observable) instruction transitively depends on cannot change
program behavior.

---

### 10. IdenticalGepReductionPass
*File: `rusty/opt/passes/IdenticalGepReductionPass.kt`*

**Purpose.** Block-local CSE specialized to `getelementptr`: fold identical address computations in
the same block into one, a cheap cleanup after Mem2Reg.

**Example.**
```llvm
%a = getelementptr {i32,i32}, ptr %s, i32 0, i32 1
%b = getelementptr {i32,i32}, ptr %s, i32 0, i32 1   ; identical
; %b replaced by %a and removed
```

**Algorithm.** Per block, keep a map keyed by `(elementType, pointer, indices, inBounds)`. The first
GEP with a key becomes the leader; any later GEP with the same key is `replaceAllUsesWith(leader)`
and removed. The map is cleared at each block boundary (no cross-block reasoning).

---

### 11. GlobalValueNumberingPass (GVN)
*File: `rusty/opt/passes/GlobalValueNumberingPass.kt`*

**Purpose.** Two conservative redundancy eliminators, neither of which needs alias analysis:
1. **Pure-expression CSE across the dominator tree** — a recomputed pure expression whose value is
   already available from a dominating block is replaced by that earlier result.
2. **Block-local redundant-load elimination and store-to-load forwarding.**

**Examples.**
```llvm
; (1) CSE across dominator tree — i % 512 computed twice on a dominated path:
%m1 = srem i32 %i, 512    ; leader, in a dominating block
...
%m2 = srem i32 %i, 512    ; replaced by %m1
; (2) store-to-load forwarding within a block:
store i32 %v, ptr %p
%l = load i32, ptr %p     ; replaced by %v
```

**Algorithm.**
- **Pure-expression CSE:** pre-order DFS of the dominator tree with an explicit stack. A `pureTable`
  maps a value-numbering key → leader value. The key is `(opcode, type, operand-tokens, extra)`, where
  commutative binary operands are canonicalized (sorted), constants collapse to a value token, and SSA
  values key on identity. On entering a block, each pure instruction (binary/cast/icmp/gep) either
  matches an existing dominating leader (→ replace) or becomes the new leader, recorded so it can be
  dropped when its subtree is finished. Because a key is only ever inserted when absent, every entry in
  the table corresponds to a definition that dominates the current block.
- **Block-local memory:** a `loadCache` maps pointer → last loaded/stored value, reset per block. A
  store clears the cache (it may alias anything) and then records its own pointer → value for an
  immediate reload; a load reuses a cached value of matching type or otherwise records itself; a call
  or any may-write instruction clears the cache. Clearing on every possibly-aliasing write is what
  keeps this sound without alias analysis.

---

### 13. LoopInvariantCodeMotionPass (LICM)
*File: `rusty/opt/passes/LoopInvariantCodeMotionPass.kt`*

**Purpose.** Hoist side-effect-free instructions whose operands don't change inside a loop out into
the loop preheader, so they're computed once instead of every iteration.

**Example.**
```rust
for i in 0..n { a[i] = x * y; }   // x*y is loop-invariant
// %t = mul %x, %y  is hoisted into the preheader; the loop body reuses %t
```

**Algorithm.**
1. Identify natural loops: a back-edge `latch -> header` where `header` dominates `latch`. Discover
   the loop body by walking predecessors back from the latch; find the single preheader (the unique
   outside predecessor of the header whose only relevant successor is the header).
2. Compute the loop's exit blocks.
3. Iterate to a fixpoint: an instruction is **hoistable** if it is pure/computable
   (binary/cast/gep/icmp/fcmp/pure-other; never phi or memory), its block **dominates every loop
   exit** (so hoisting never adds work on a path the original wouldn't run), and all its operands are
   loop-invariant (defined outside the loop, or already hoisted). Move such instructions to the
   preheader, just before its terminator.

The exit-dominance guard is what makes the conservative hoist safe without speculation.

---

### 14. LoopAddressReductionPass (strength reduction of array addressing)
*File: `rusty/opt/passes/LoopAddressReductionPass.kt`*

**Purpose.** Indexing `a[i]` inside a loop recomputes `base + i * elemSize` every iteration (a
multiply). Since `i` only ever steps by one, the address only ever steps by one element — so keep a
running pointer and advance it by one element each iteration instead. Classic induction-variable
strength reduction: a per-iteration multiply becomes a per-iteration add.

**Example.**
```rust
for i in 0..n { sum += a[i]; }
// before: addr = getelementptr a, 0, i      (index multiply per iteration)
// after:
//   pre:    addr.start = getelementptr a, 0, start
//   header: addr.phi   = phi [addr.start, pre], [addr.next, latch]
//   latch:  addr.next  = getelementptr addr.phi, 1
//   the original a[i] GEP is replaced by addr.phi
```

**Algorithm.** For each simple natural loop (header/latch/preheader/body):
1. **Find an induction variable:** a header phi of integer type with two incomings — `start` from the
   preheader and `phi + 1` from the latch (the `+1` add living in the latch).
2. **Find target GEPs:** a `getelementptr` of the form `a[0, i]` where `i` is the induction phi, the
   element type is a scalar array element (not nested array/struct), and the base pointer `a` is
   loop-invariant, in a block dominated by the header.
3. **Rewrite** each such GEP into a pointer induction:
   - `addr.start = getelementptr base, 0, start` in the preheader,
   - `addr.phi = phi [addr.start, preheader], [addr.next, latch]` in the header,
   - `addr.next = getelementptr addr.phi, 1` in the latch (after the IV update),
   - replace all uses of the original GEP with `addr.phi` and delete it.

---

### 16. LoopCounterPromotionPass
*File: `rusty/opt/passes/LoopCounterPromotionPass.kt`*

**Purpose.** A memory-resident counter that a loop loads, increments, and stores back every iteration
(e.g. a struct field `self.count += k`) is turned into a register accumulator carried by a phi, with
a single flush store after the loop. This removes a load+store pair per iteration from the hot path.

**Example.**
```rust
loop { self.count += 1; ... }
// before (per iteration): load count; add 1; store count
// after:
//   pre:    init = load self.count
//   header: acc  = phi [init, pre], [acc+1, latch]   ; load/store removed from body
//   exit:   store acc, self.count                    ; single flush
```

**Algorithm.** For each simple natural loop (rejected outright if the loop contains any call):
1. Require a single header exit block.
2. **Collect updates:** find `store (add (load fieldPtr), constIncrement), fieldPtr` triples where
   the load and store address the *same* struct field (constant-index GEP), the increment is
   nonzero, and load/add live in the same block.
3. For each field key, require: the base pointer is loop-invariant; the update is straight-line on
   **every** iteration (single update block that dominates the latch); and the field is **safe** —
   no other load/store in the loop may alias the same field (a simple base+field alias test).
4. **Rewrite:** create a preheader GEP + initial load, a header phi accumulator
   `[init, preheader], [lastAdd, latch]`, replace each in-loop load with the running accumulator
   value (chaining adds), delete the in-loop loads and stores, and emit one flush store of the final
   accumulator into the field in the exit block.

---

### 18. CFGSimplifyPass
*File: `vendor/.../presets/CFGSimplifyPass.kt`*

**Purpose.** Remove unreachable blocks and merge straight-line block chains (a block whose single
successor has it as the only predecessor is absorbed), shrinking the CFG.

**Example.**
```
A: ... ; br B          A: ...           (B's body appended,
B: ... ; br C    -->      ...            B removed; phis in B
(B has only pred A)       ... ; br C     resolved to A's incoming)
```

**Algorithm.**
1. Run dead-code elimination first to drop unreachable blocks (so every remaining non-entry block has
   a predecessor).
2. Using the predecessor map, for each block whose terminator is an **unconditional** branch to a
   destination that has ≤ 1 predecessor, **absorb** the destination:
   - Resolve the destination's phi nodes to their single incoming value from this block.
   - Update phi nodes in the destination's successors to reference this block.
   - Append the destination's (non-terminator, non-phi) instructions and adopt its terminator.
   Repeat while the (new) terminator is still an unconditional branch into a single-pred block.
3. Remove all absorbed blocks. Membership is tracked with a hash set, and merging appends in place, to
   avoid quadratic behavior on long chains.

---

## ASM optimizations

After IR optimization, instruction selection lowers the IR to a RISC-V `AssemblyProgram`. The selector
materializes each IR value into a virtual register and computes GEP/load results into reserved scratch
registers (`t3`–`t6`) before moving them into the allocated destination, which leaves a `mv` after
roughly every address computation and load.

Copy `mv`s are removed in **two coalescing stages** that operate on disjoint move populations:

| Move source | Example | Removed by |
|---|---|---|
| Phi resolution | `mv phiReg, incomingReg` (`emitScalarPhiMoves`) | **RegallocCoalescing** (graph-level) |
| Parameter setup | `mv allocReg, argReg` (`moveParametersDirectly`) | RegallocCoalescing — *biasing, future work* |
| Load result | `mv dst, t5` (`lowerLoad`) | **AsmCoalescing** R1 (scratch artifact) |
| GEP result | `mv dst, t4` (`lowerGep`) | AsmCoalescing R1/R2 (scratch artifact) |

The first two are real IR value-to-value copies the allocator can eliminate by giving both sides the same
register; the last two are codegen artifacts of scratch lowering the allocator cannot see. The two stages
are described below.

### Branch lowering & compare fusion
*File: `rusty/asm/AsmTranslator.kt` (`lowerBranch`, `computeFusedComparisons`, `emitConditionalBranch`)*

**Purpose.** Lower a conditional `br i1 %c, A, B` to as few RISC-V instructions as possible. A naive
lowering materializes the boolean (`icmp` → `slt`/`xor`/`seqz`…), tests it (`beqz`), and routes **both**
edges through explicit jumps via a trampoline block. This pass instead (1) folds a comparison directly
into a compare-and-branch, (2) makes the not-taken edge a **fall-through**, and (3) emits a trampoline
only in the rare case where both edges carry phi moves. Because a conditional branch is the bottom of
essentially every loop, this is a per-iteration win.

**Example.**
```llvm
; IR
%c = icmp slt i32 %i, %n
br i1 %c, label %body, label %exit   ; %body is the next block in layout order
```
```asm
; before  (boolean materialized, both edges jump through a trampoline)
slt  t0, %i, %n
beqz t0, .L.__false_edge
j    .L.body
.L.__false_edge:
j    .L.exit
; after  (compare folded; not-taken edge falls through into .L.body)
bge  %i, %n, .L.exit
; ...   .L.body follows immediately
```
Compare-to-zero collapses further: `icmp ne %x, 0; br` becomes `bne %x, zero, …` (one instruction)
because the constant `0` is lowered to the hardwired `zero` register.

**Algorithm.**
1. **Fusion candidates (`computeFusedComparisons`).** A comparison is fusable when its result feeds
   *only* a conditional branch and it is the last value-producing instruction before that branch's
   terminator. The adjacency requirement is a correctness condition, not a heuristic: the branch
   re-reads the comparison's operands, so nothing may run between the (now un-emitted) compare and the
   branch that could reuse an operand's register. The terminator is read from the **last element of the
   block's instruction sequence**, not the `BasicBlock.terminator` field, which is not reliably
   populated. Fused comparisons are skipped during instruction selection (no boolean is materialized).
2. **Orientation choice.** The two edges are the *taken* (branch) edge and the *not-taken*
   (fall-through) edge; inverting the predicate swaps which is which. The not-taken edge runs its phi
   moves inline and pays nothing when it is the layout-successor block; the taken edge needs an extra
   jump only if it carries phi moves. `lowerBranch` scores both orientations by the number of
   unconditional jumps emitted and picks the cheaper (ties prefer the non-inverted form).
3. **Emit (`emitConditionalBranch` / `emitPredicatedBranch`).** For a fused compare, map the (possibly
   inverted) predicate to a native branch — `beq/bne/blt/bge/bltu/bgeu`, swapping operands for the
   greater-than / less-or-equal forms RISC-V lacks, and using `zero` for compare-to-zero operands. For a
   non-fused condition, test the materialized boolean with `bnez`/`beqz`. Then emit the not-taken edge's
   phi moves and fall through (or `j` if it is not the layout-successor).
4. **Trampoline only when unavoidable.** If *both* edges carry phi moves, the taken edge's moves are
   placed in a `.__br_edge` block reached by the conditional branch; the not-taken edge still falls
   through / jumps as above. Unconditional branches likewise drop their `j` when the destination is the
   layout-successor.

Validated on the official IR-1 suite: −17.5% dynamic instructions (qemu rv64) versus the trampoline
lowering, every case improved, no regressions, all `officialAsmTests` green.

---

### RegallocCoalescing — phi/copy coalescing in the allocator
*File: `rusty/asm/support/RegallocCoalescing.kt` (invoked from `RegisterAllocator.allocateFunction`)*

**Purpose.** Eliminate phi-resolution `mv`s by giving a phi and its copy-related operands the same
register. In SSA the only true value-to-value copies are phi operands: `p = phi [v, B], …` is a copy of
`v` along the edge from `B`. If `p` and `v` share a register, the move `emitScalarPhiMoves` would emit on
that edge has identical source and destination and the move sequencer drops it for free. This is the
class of move the asm-level pass below structurally cannot see (neither operand is a scratch register).

**Algorithm — Briggs conservative coalescing**, run as a pre-pass on the interference graph before the
simplify/select colorer.
1. **Move set.** Collect every `(phi, incoming)` pair where both are register candidates (constants,
   globals and force-spilled operands are skipped — they are never in the graph).
2. **Coalesce loop.** With a union-find over graph nodes, repeatedly attempt each move `(x, y)`:
   - skip if already merged (`find(x) == find(y)`) or interfering (`y ∈ adj[x]`);
   - otherwise apply the **Briggs test** — merge iff the combined neighbourhood `adj[x] ∪ adj[y]` has
     fewer than `K` neighbours of *significant degree* (degree `≥ K`), where `K` is the allocatable
     register count. Briggs proved this can never make a `K`-colorable graph non-colorable, so a coalesce
     never trades a move for a spill.

   Merging redirects the dropped node's edges onto the survivor, which lowers the degree of their common
   neighbours; this can unblock a move that failed the test earlier, so the loop iterates to a fixpoint.
3. **Color & expand.** The colorer runs unchanged on the merged graph of representatives (a merged node's
   spill weight is the sum of its members', and it prefers callee-saved registers if any member crosses a
   loop call). Every original value then inherits its representative's slot.

Because conservative coalescing only ever merges non-interfering nodes, a phi-swap permutation or a
"lost copy" (an incoming still live past the phi) interferes and is never coalesced — the move sequencer
handles the remainder, including cycles, exactly as before. Measured on the IR-1 resource suite: total
`mv` count fell 454 → 420 (−7.5%) with all `officialAsmTests`/`officialOptTests` still green.

---

### AsmCoalescing — scratch-register copy coalescing
*File: `rusty/asm/support/AsmCoalescing.kt` (invoked from `AsmTranslator.translate`)*

**Purpose.** Eliminate the redundant `mv` instructions produced by scratch-register lowering. Before
this pass ~45% of hot-loop instructions were moves; the reference compiler's hot loops contain almost
none.

It relies on one codegen invariant: scratch registers `t3`–`t6` are never live across an
instruction-selection boundary (written and consumed within the lowering of a single IR instruction,
never carried across IR instructions or block edges). Anything the instruction classifier doesn't
positively recognize is treated as a **barrier**, so unknown mnemonics can stop optimization but never
be miscompiled.

It applies two rewrites, iterated to a fixpoint (max 8 rounds) because each exposes opportunities for
the other, followed by a self-move cleanup.

**R1 — result forwarding:** `OP tX, ...; mv rd, tX` → `OP rd, ...`
```asm
add  t3, a0, a1
mv   s1, t3        ; t3 dead after this
; becomes:
add  s1, a0, a1
```
Valid because a RISC-V instruction reads all sources before writing its destination, the two
instructions are adjacent, and a scan (`scratchDeadAfter`) proves `tX` is not read again before being
redefined or leaving the block.

**R2 — scratch copy propagation:** `mv tX, rs; ... use(tX) ...` → substitute `rs` for `tX`
```asm
mv   t3, s2
lw   a0, 0(t3)
addi a1, t3, 4
; becomes (t3 copy deleted):
lw   a0, 0(s2)
addi a1, s2, 4
```
Every in-block use of scratch `tX` is rewritten to `rs`, but only if `rs` is not redefined between the
copy and the uses and `tX` is not redefined first. Once all uses are rewritten the copy is dead (`tX`
is scratch, dead at the block boundary) and is removed. Substitution rewrites only **source**
operands and `Address` bases — never the destination operand of a destination-writing mnemonic.

**Instruction classification.** The rewrites depend on knowing, per instruction, which register is the
written destination and which are read. Three mnemonic sets encode this:
- `destinationFirst` — operand[0] is purely written (`mv`, `li`, `add`, `lw`, …); used to find the
  destination and to skip it when scanning sources.
- `readsOnly` — reads all register operands, writes none (`sw`, branches, …).
- `controlExit` — control transfers / fences that end a straight-line block (`j`, `call`, `ret`, …).

`readsRegister` and `writesRegister` are sound over-approximations (an unknown mnemonic is assumed to
clobber its register), and `isBlockExit` treats branches and anything unrecognized as a block
boundary, preserving the "unknown ⇒ barrier" safety property.

**Final cleanup.** `removeSelfMoves` drops any `mv rd, rd` left behind (e.g. after R1 redirected a
producer onto the move's own source).

---

## Summary table

| # | Pass | Level | One-line effect |
|---|------|-------|-----------------|
| 1 | SizeInlining | IR | `sizeof` helper calls → constants |
| 2 | FunctionInlining | IR | inline small non-recursive callees (<40 insts) |
| 3 | SmallMemcopyLowering | IR | `memfill` ≤32B → scalar load/store |
| 4 | InstCombineCleanup | IR | algebraic identities + const fold + trivial DCE (×6 in pipeline) |
| 5 | PointerSlotForwarding | IR | single-store pointer alloca → forward the value |
| 6 | SROA | IR | split struct alloca into per-field scalar allocas |
| 7 | Mem2Reg | IR | promote allocas to SSA registers + phis |
| 8 | AggressiveDCE | IR | mark-sweep DCE, kills dead phi/value cycles |
| 10 | IdenticalGepReduction | IR | block-local GEP CSE |
| 11 | GVN | IR | dominator-tree pure CSE + block-local load forwarding |
| 13 | LICM | IR | hoist loop-invariant pure instructions to preheader |
| 14 | LoopAddressReduction | IR | array index multiply → advancing pointer add |
| 16 | LoopCounterPromotion | IR | memory counter → register accumulator + flush |
| 18 | CFGSimplify | IR | drop unreachable blocks, merge straight-line chains |
| — | BranchLowering | ASM | fold `icmp+br` into compare-and-branch, fall through not-taken edge |
| — | RegallocCoalescing | ASM | Briggs coalescing of phi/copy `mv`s during register allocation |
| — | AsmCoalescing | ASM | coalesce scratch-register `mv`s (R1 forward, R2 propagate) |
