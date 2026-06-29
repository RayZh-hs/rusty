package rusty.opt.passes

// Dominator-tree-scoped global value numbering. Two jobs, both kept sound without alias analysis:
//
//   1. Pure-expression CSE. A pure instruction (arithmetic, cast, icmp, gep) is keyed by opcode +
//      operands; if an equal key already has a leader defined in a *dominating* scope, the
//      instruction is replaced by that leader. Walks the dominator tree with a pre-order DFS,
//      dropping a block's keys when its subtree is finished.
//        x = i % 512   (in header) ... y = i % 512  (in body)  ->  y replaced by x
//
//   2. Redundant-load elimination / store-to-load forwarding. A load whose pointer has no
//      intervening clobber reuses the last loaded or stored value.
//        store v, p ; ... ; x = load p   ->  x replaced by v
//      The available-load map flows across the dominator tree, but only into a block that has a
//      *single* predecessor: such a block's lone predecessor is necessarily its immediate dominator,
//      so memory state arrives unmerged with no intervening block. Blocks with multiple predecessors
//      (loop headers, merges) start empty, so nothing stale crosses a back-edge or join.
//        block A:  x = load p ; ... (no store) ... ; br B   // B's only predecessor
//        block B:  y = load p   ->  y replaced by x
//
// Notes: the load map is cleared on any store or call (conservative, no aliasing). Expects valid SSA
// (runs after Mem2Reg); CSE and the single-predecessor test rely on dominator/predecessor info.

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.analysis.presets.PredecessorMap
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.core.ValueUseRegistry
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.values.constants.IntConstant

object GlobalValueNumberingPass : IRPass() {

    override fun run(module: Module, am: AnalysisManager): Module {
        am.register(PredecessorAnalysis)
        val domResult = am.get(DominatorTreeAnalysis::class)
        val predecessors = am.get(PredecessorAnalysis::class)
        var changed = false

        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            val info = domResult.getFunctionInfo(function) ?: continue
            changed = runOnFunction(function, info.treeChildren, predecessors) || changed
        }

        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun runOnFunction(
        function: Function,
        treeChildren: Map<space.norb.llvm.structure.BasicBlockId, Set<space.norb.llvm.structure.BasicBlockId>>,
        predecessors: PredecessorMap,
    ): Boolean {
        val entry = function.entryBlock ?: return false
        // Pure-expression leaders visible on the current dominator path. We only ever insert a key
        // that is absent, so an entry always corresponds to a dominating definition and is removed
        // again when its defining block's subtree is finished.
        val pureTable = HashMap<ValueKey, Value>()
        var changed = false

        // A block may reuse its immediate dominator's available loads only when it has exactly one
        // predecessor (then that predecessor is the idom and memory flows in directly). The entry
        // block has no predecessor, so it starts empty.
        fun startCacheFor(blockId: space.norb.llvm.structure.BasicBlockId, parentEnd: HashMap<Value, Value>): HashMap<Value, Value> =
            if (predecessors[blockId]?.size == 1) HashMap(parentEnd) else HashMap()

        // Pre-order DFS over the dominator tree using an explicit stack (avoids deep recursion).
        // A frame is processed (block CSE'd) when first pushed; its inserted keys are dropped when
        // the whole subtree has been visited. Each frame keeps the load map left at its block's exit
        // so single-predecessor children can inherit it.
        val stack = ArrayDeque<Frame>()
        val entryFrame = Frame(treeChildren[entry.id].orEmpty().toMutableList())
        changed = processBlock(entry, pureTable, entryFrame, HashMap()) || changed
        stack.addLast(entryFrame)

        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (frame.pendingChildren.isEmpty()) {
                // Leaving this block's scope: drop the keys it introduced.
                for (key in frame.insertedKeys) pureTable.remove(key)
                stack.removeLast()
                continue
            }
            val childId = frame.pendingChildren.removeLast()
            val childBlock = BasicBlock.fromId(childId) ?: continue
            val childFrame = Frame(treeChildren[childId].orEmpty().toMutableList())
            val childStart = startCacheFor(childId, frame.endLoadCache)
            changed = processBlock(childBlock, pureTable, childFrame, childStart) || changed
            stack.addLast(childFrame)
        }

        return changed
    }

    private class Frame(val pendingChildren: MutableList<space.norb.llvm.structure.BasicBlockId>) {
        val insertedKeys = ArrayList<ValueKey>()
        // Available-load map at this block's exit, handed down to single-predecessor children.
        var endLoadCache: HashMap<Value, Value> = HashMap()
    }

    private fun processBlock(
        block: BasicBlock,
        pureTable: HashMap<ValueKey, Value>,
        frame: Frame,
        startLoadCache: HashMap<Value, Value>,
    ): Boolean {
        var changed = false
        // Memory state for redundant-load elimination / store-to-load forwarding, seeded from the
        // immediate dominator when this block has a single predecessor (see runOnFunction).
        val loadCache = startLoadCache
        frame.endLoadCache = loadCache
        val insertedKeys = frame.insertedKeys

        for (inst in block.instructions.toList()) {
            when (inst) {
                is StoreInst -> {
                    // A store may alias any cached pointer; clear everything, then make the freshly
                    // stored value available for an immediate reload of the exact same pointer.
                    loadCache.clear()
                    loadCache[inst.pointer] = inst.value
                }
                is LoadInst -> {
                    val cached = loadCache[inst.pointer]
                    if (cached != null && cached.type == inst.loadedType && cached !== inst) {
                        replaceWith(block, inst, cached)
                        changed = true
                    } else {
                        loadCache[inst.pointer] = inst
                    }
                }
                is CallInst -> loadCache.clear() // conservative: a call may write to memory
                else -> {
                    if (inst.mayWriteToMemoryConservative()) loadCache.clear()
                    val key = pureKeyOf(inst) ?: continue
                    val leader = pureTable[key]
                    if (leader != null && leader.type == inst.type && leader !== inst) {
                        replaceWith(block, inst, leader)
                        changed = true
                    } else if (leader == null) {
                        pureTable[key] = inst
                        insertedKeys.add(key)
                    }
                }
            }
        }
        return changed
    }

    private fun replaceWith(block: BasicBlock, inst: Instruction, replacement: Value) {
        inst.replaceAllUsesWith(replacement)
        inst.detachOperands()
        block.instructions.remove(inst)
    }

    // --- value numbering keys ----------------------------------------------------------------

    private fun pureKeyOf(inst: Instruction): ValueKey? = when (inst) {
        is BinaryInst -> {
            val operands = listOf(inst.lhs, inst.rhs).let { if (inst.isCommutative()) canonicalize(it) else it }
            ValueKey(inst::class.java.name, inst.type, operandTokens(operands), null)
        }
        is CastInst -> ValueKey(inst::class.java.name, inst.type, operandTokens(listOf(inst.value)), null)
        is ICmpInst -> {
            ValueKey("icmp", inst.type, operandTokens(listOf(inst.lhs, inst.rhs)), inst.predicate)
        }
        is GetElementPtrInst -> {
            val operands = buildList { add(inst.pointer); addAll(inst.indices) }
            ValueKey("gep", inst.type, operandTokens(operands), Pair(inst.elementType, inst.isInBounds))
        }
        else -> null
    }

    /** Stable per-operand token: equal constants collapse to one token, SSA values key on identity. */
    private fun operandTokens(operands: List<Value>): List<Any> =
        operands.map { value ->
            when (value) {
                is IntConstant -> "c:${value.value}:${value.type}"
                else -> IdentityToken(value)
            }
        }

    private fun canonicalize(operands: List<Value>): List<Value> =
        operands.sortedBy { sortToken(it) }

    private fun sortToken(value: Value): String = when (value) {
        is IntConstant -> "1c:${value.value}"
        else -> "0v:${System.identityHashCode(value)}"
    }

    private data class ValueKey(
        val opcode: String,
        val type: Any,
        val operands: List<Any>,
        val extra: Any?,
    )

    /** Wraps a Value so map keys compare by reference identity rather than structural equality. */
    private class IdentityToken(val value: Value) {
        override fun equals(other: Any?): Boolean = other is IdentityToken && other.value === value
        override fun hashCode(): Int = System.identityHashCode(value)
    }

    private fun Instruction.mayWriteToMemoryConservative(): Boolean = when (this) {
        is space.norb.llvm.instructions.base.MemoryInst -> mayWriteToMemory()
        is space.norb.llvm.instructions.base.OtherInst -> mayWriteToMemory()
        else -> false
    }

    private fun User.detachOperands() {
        for (index in 0 until getNumOperands()) {
            ValueUseRegistry.unregisterUse(getOperand(index), this)
        }
    }
}
