package rusty.opt.passes

// Promote a memory counter updated inside a loop into a register accumulator carried by phis, loaded
// once in the preheader and flushed back to memory once on loop exit. Turns a load+add+store per
// iteration into a single add per iteration.
//
//   loop body:  t  = load p           header:  acc = phi [load p (preheader)], [acc.next, latch]
//               t2 = t + c       ->    body:   acc.next = acc + c        (load/store removed)
//               store t2, p            exit:   store acc.last, p
//
// Two paths share the same safety gates (single header exit, no calls in the loop, loop-invariant
// constant-index struct-field base, and the field aliases nothing else in the loop):
//   * Straight-line: the field is updated unconditionally in one block that dominates the latch — a
//     header phi plus one exit flush suffice.
//   * General (SSA): the field is updated on some-but-not-all loop paths (inside an `if`). Runs
//     textbook single-variable SSA construction over the loop region — phis at the iterated dominance
//     frontier of the store sites, then a dominator-tree rename threading the accumulator through them.

import rusty.opt.passes.utils.NaturalLoop
import rusty.opt.passes.utils.dominates
import space.norb.llvm.analysis.presets.FunctionDominanceInfo
import rusty.opt.passes.utils.findSimpleNaturalLoops
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.core.ValueUseRegistry
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.StructType
import space.norb.llvm.values.constants.IntConstant

object LoopCounterPromotionPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        val predecessors = am.get(PredecessorAnalysis::class)
        val dominators = am.get(DominatorTreeAnalysis::class)
        var changed = false

        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            val domInfo = dominators.getFunctionInfo(function) ?: continue
            val loops = findSimpleNaturalLoops(function, predecessors, domInfo)
            for (loop in loops) {
                changed = runOnLoop(loop, domInfo, predecessors) || changed
            }
        }

        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun runOnLoop(
        loop: NaturalLoop,
        dominance: FunctionDominanceInfo,
        predecessors: space.norb.llvm.analysis.presets.PredecessorMap,
    ): Boolean {
        if (loop.blocks.any { block -> block.instructions.any { it is CallInst } }) return false
        val exit = singleHeaderExit(loop) ?: return false
        val owners = instructionOwners(loop.blocks + loop.preheader)

        val updates = collectUpdates(loop, owners)
        if (updates.isEmpty()) return false

        var changed = false
        for ((key, keyUpdates) in updates) {
            if (!isLoopInvariant(key.base, loop, owners)) continue
            if (!isSafeField(loop, key, keyUpdates)) continue
            if (isStraightLineEveryIteration(loop, keyUpdates, dominance)) {
                // Fast path: the field is updated unconditionally in one block — a header phi plus a
                // single exit flush suffices, with no merge phis.
                rewriteField(loop, exit, key, keyUpdates)
                changed = true
            } else if (promoteFieldSSA(loop, exit, key, dominance, predecessors)) {
                // The field is updated on some-but-not-all loop paths (e.g. inside an `if`); promote it
                // with full single-variable SSA construction so the accumulator threads through phis at
                // the loop's internal merges.
                changed = true
            }
        }
        return changed
    }

    // Promote a loop-invariant field that is read/written only through this exact field gep, but whose
    // updates are not straight-line (they sit on a conditional path). Performs textbook single-variable
    // SSA construction restricted to the loop: place phis at the iterated dominance frontier of the
    // store sites, then rename loads/stores to register values threaded through those phis. The field
    // is loaded once in the preheader and flushed once on the single loop exit.
    private fun promoteFieldSSA(
        loop: NaturalLoop,
        exit: BasicBlock,
        key: FieldKey,
        dominance: FunctionDominanceInfo,
        predecessors: space.norb.llvm.analysis.presets.PredecessorMap,
    ): Boolean {
        // The exit flush is inserted at the top of the exit block, so it must run only when leaving the
        // loop — require the exit's sole predecessor to be the header.
        if (predecessors[exit.id]?.singleOrNull() != loop.header.id) return false

        val keyLoads = mutableListOf<LoadInst>()
        val keyStores = mutableListOf<StoreInst>()
        for (block in loop.blocks) {
            for (inst in block.instructions) {
                when (inst) {
                    is LoadInst -> if (fieldKey(inst.pointer) == key) keyLoads.add(inst)
                    is StoreInst -> if (fieldKey(inst.pointer) == key) keyStores.add(inst)
                }
            }
        }
        if (keyStores.isEmpty()) return false
        val fieldType = keyStores.first().storedType
        if (fieldType !is IntegerType) return false
        if (keyStores.any { it.storedType != fieldType } || keyLoads.any { it.loadedType != fieldType }) return false

        val region = loop.blocks + exit
        val regionIds = region.mapTo(linkedSetOf(), BasicBlock::id)

        // Iterated dominance frontier of the defining blocks, intersected with the loop region; each
        // becomes a phi for the promoted value. The def set is the store-owning blocks plus the
        // preheader, which holds the initial definition (the seed load) — exactly as textbook SSA
        // construction treats the entry block. Seeding the preheader is what places the header phi.
        val defBlocks = linkedSetOf<BasicBlock>()
        defBlocks.add(loop.preheader)
        keyStores.mapTo(defBlocks) { ownerBlock(it, loop) ?: return false }
        val phiBlocks = iteratedDominanceFrontier(defBlocks, regionIds, dominance)
        if (loop.header !in phiBlocks) return false // a loop-carried value must have a header phi

        val namePrefix = "${key.name}.loop.${loop.header.id}.ssa"
        val gep = GetElementPtrInst.create(
            "$namePrefix.addr",
            key.structType,
            key.base,
            listOf(IntConstant(0L, IntegerType.I32), IntConstant(key.field, IntegerType.I32)),
        )
        loop.preheader.instructions.add(loop.preheader.insertionIndexBeforeTerminator(), gep)
        val initialLoad = LoadInst("$namePrefix.init", fieldType, gep)
        loop.preheader.instructions.add(loop.preheader.insertionIndexBeforeTerminator(), initialLoad)

        val phis = HashMap<BasicBlock, PhiNode>()
        for (block in phiBlocks) {
            val phi = PhiNode.createPlaceholder("$namePrefix.${block.id}.phi", fieldType)
            block.instructions.add(0, phi)
            phis[block] = phi
        }

        val keyLoadSet = keyLoads.toHashSet()
        val keyStoreSet = keyStores.toHashSet()
        val removals = mutableListOf<Instruction>()
        var flushValue: Value = initialLoad

        // Rename via a pre-order walk of the dominator tree, entering only region blocks. `currentDef`
        // is the SSA value of the field reaching the current program point.
        fun rename(block: BasicBlock, incoming: Value) {
            var currentDef = phis[block] ?: incoming
            for (inst in block.instructions.toList()) {
                when {
                    inst is PhiNode -> Unit
                    inst is LoadInst && inst in keyLoadSet -> {
                        inst.replaceAllUsesWith(currentDef)
                        removals.add(inst)
                    }
                    inst is StoreInst && inst in keyStoreSet -> {
                        currentDef = inst.value
                        removals.add(inst)
                    }
                }
            }
            for (successor in block.terminator?.getSuccessors().orEmpty().distinct()) {
                phis[successor]?.addIncomingMutable(currentDef, block)
            }
            if (block == exit) flushValue = currentDef
            for (childId in dominance.treeChildren[block.id].orEmpty()) {
                if (childId !in regionIds) continue
                rename(BasicBlock.fromId(childId) ?: continue, currentDef)
            }
        }
        rename(loop.preheader, initialLoad)

        for (inst in removals) {
            inst.detachOperands()
            ownerBlock(inst, loop)?.instructions?.remove(inst) ?: exit.instructions.remove(inst)
        }

        val flush = StoreInst("$namePrefix.flush", fieldType, flushValue, gep)
        exit.instructions.add(exit.instructions.indexOfLast { it is PhiNode } + 1, flush)
        return true
    }

    private fun ownerBlock(inst: Instruction, loop: NaturalLoop): BasicBlock? =
        loop.blocks.firstOrNull { inst in it.instructions }

    private fun iteratedDominanceFrontier(
        defBlocks: Set<BasicBlock>,
        regionIds: Set<space.norb.llvm.structure.BasicBlockId>,
        dominance: FunctionDominanceInfo,
    ): Set<BasicBlock> {
        val result = linkedSetOf<BasicBlock>()
        val worklist = ArrayDeque(defBlocks)
        val seen = defBlocks.toHashSet()
        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()
            for (frontierId in dominance.dominanceFrontier[block.id].orEmpty()) {
                if (frontierId !in regionIds) continue
                val frontier = BasicBlock.fromId(frontierId) ?: continue
                if (result.add(frontier) && seen.add(frontier)) {
                    worklist.add(frontier)
                }
            }
        }
        return result
    }

    private fun collectUpdates(
        loop: NaturalLoop,
        owners: Map<Instruction, BasicBlock>,
    ): Map<FieldKey, List<CounterUpdate>> {
        val result = linkedMapOf<FieldKey, MutableList<CounterUpdate>>()
        for (block in loop.blocks) {
            for (inst in block.instructions) {
                val store = inst as? StoreInst ?: continue
                val add = store.value as? AddInst ?: continue
                val increment = add.rhs as? IntConstant ?: continue
                val load = add.lhs as? LoadInst ?: continue
                if (increment.value == 0L) continue
                if (load.loadedType != store.storedType || load.loadedType !is IntegerType) continue

                val storeKey = fieldKey(store.pointer) ?: continue
                val loadKey = fieldKey(load.pointer) ?: continue
                if (storeKey != loadKey) continue
                if (owners[load] != block || owners[add] != block) continue

                result.getOrPut(storeKey) { mutableListOf() }
                    .add(CounterUpdate(block, load, add, store, increment, load.loadedType))
            }
        }
        return result
    }

    private fun isSafeField(loop: NaturalLoop, key: FieldKey, updates: List<CounterUpdate>): Boolean {
        val updateInstructions = updates.flatMap { listOf(it.load, it.add, it.store) }.toSet()
        for (block in loop.blocks) {
            for (inst in block.instructions) {
                if (inst in updateInstructions) continue
                when (inst) {
                    is LoadInst -> if (mayAlias(key, inst.pointer)) return false
                    is StoreInst -> if (mayAlias(key, inst.pointer)) return false
                }
            }
        }
        return true
    }

    private fun rewriteField(loop: NaturalLoop, exit: BasicBlock, key: FieldKey, updates: List<CounterUpdate>) {
        val namePrefix = "${key.name}.loop.${loop.header.id}"
        val preheaderGep = GetElementPtrInst.create(
            "$namePrefix.addr",
            key.structType,
            key.base,
            listOf(IntConstant(0L, IntegerType.I32), IntConstant(key.field, IntegerType.I32)),
        )
        loop.preheader.instructions.add(loop.preheader.insertionIndexBeforeTerminator(), preheaderGep)

        val initialLoad = LoadInst("$namePrefix.init", updates.first().type, preheaderGep)
        loop.preheader.instructions.add(loop.preheader.insertionIndexBeforeTerminator(), initialLoad)

        val accumulator = PhiNode.createPlaceholder("$namePrefix.acc", updates.first().type)
        val headerInsertIndex = loop.header.instructions.indexOfLast { it is PhiNode } + 1
        loop.header.instructions.add(headerInsertIndex, accumulator)

        var current: Value = accumulator
        for (update in updates.sortedWith(compareBy({ instructionOrder(it.block, it.store) }))) {
            update.load.replaceAllUsesWith(current)
            current = update.add
            update.load.detachOperands()
            update.block.instructions.remove(update.load)
            update.store.detachOperands()
            update.block.instructions.remove(update.store)
        }

        accumulator.addIncomingMutable(initialLoad, loop.preheader)
        accumulator.addIncomingMutable(current, loop.latch)

        val exitStore = StoreInst("$namePrefix.flush", updates.first().type, accumulator, preheaderGep)
        val exitInsertIndex = exit.instructions.indexOfLast { it is PhiNode } + 1
        exit.instructions.add(exitInsertIndex, exitStore)
    }

    private fun fieldKey(value: Value): FieldKey? {
        val gep = value as? GetElementPtrInst ?: return null
        if (gep.elementType !is StructType) return null
        if (gep.indices.size != 2) return null
        val first = gep.indices[0] as? IntConstant ?: return null
        val second = gep.indices[1] as? IntConstant ?: return null
        if (first.value != 0L) return null
        return FieldKey(gep.elementType, gep.pointer, second.value)
    }

    private fun mayAlias(key: FieldKey, pointer: Value): Boolean {
        val other = fieldKey(pointer)
        if (other != null) {
            return key.base == other.base && key.field == other.field
        }
        return pointer == key.base
    }

    private fun singleHeaderExit(loop: NaturalLoop): BasicBlock? {
        val loopIds = loop.blocks.mapTo(linkedSetOf(), BasicBlock::id)
        val exits = loop.blocks
            .flatMap { block -> block.getSuccessors().filter { successor -> successor.id !in loopIds } }
            .distinct()
        if (exits.size != 1) return null
        val exit = exits.single()
        return exit.takeIf { loop.header.getSuccessors().contains(it) }
    }

    private fun isLoopInvariant(
        value: Value,
        loop: NaturalLoop,
        owners: Map<Instruction, BasicBlock>,
    ): Boolean {
        val owner = (value as? Instruction)?.let(owners::get) ?: return true
        return owner !in loop.blocks
    }

    private fun isStraightLineEveryIteration(
        loop: NaturalLoop,
        updates: List<CounterUpdate>,
        dominance: FunctionDominanceInfo,
    ): Boolean {
        val updateBlock = updates.first().block
        if (updates.any { it.block != updateBlock }) return false
        return dominates(updateBlock, loop.latch, dominance)
    }

    private fun instructionOrder(block: BasicBlock, instruction: Instruction): Int =
        block.instructions.indexOf(instruction).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private fun instructionOwners(blocks: Collection<BasicBlock>): Map<Instruction, BasicBlock> =
        blocks.flatMap { block -> block.instructions.map { instruction -> instruction to block } }.toMap()

    private fun BasicBlock.insertionIndexBeforeTerminator(): Int {
        val terminator = terminator ?: return instructions.size
        val index = instructions.indexOf(terminator)
        return if (index >= 0) index else instructions.indexOfFirst { it is TerminatorInst }
            .takeIf { it >= 0 }
            ?: instructions.size
    }

    private fun Instruction.detachOperands() {
        for (index in 0 until getNumOperands()) {
            ValueUseRegistry.unregisterUse(getOperand(index), this)
        }
    }

    private data class FieldKey(
        val structType: Type,
        val base: Value,
        val field: Long,
    ) {
        val name: String = "field${field}"
    }

    private data class CounterUpdate(
        val block: BasicBlock,
        val load: LoadInst,
        val add: AddInst,
        val store: StoreInst,
        val increment: IntConstant,
        val type: Type,
    )
}
