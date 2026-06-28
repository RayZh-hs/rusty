package rusty.opt.passes

// Promote hot counter fields across loops

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
                changed = runOnLoop(loop, domInfo) || changed
            }
        }

        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun runOnLoop(
        loop: NaturalLoop,
        dominance: FunctionDominanceInfo,
    ): Boolean {
        if (loop.blocks.any { block -> block.instructions.any { it is CallInst } }) return false
        val exit = singleHeaderExit(loop) ?: return false
        val owners = instructionOwners(loop.blocks + loop.preheader)

        val updates = collectUpdates(loop, owners)
        if (updates.isEmpty()) return false

        var changed = false
        for ((key, keyUpdates) in updates) {
            if (!isLoopInvariant(key.base, loop, owners)) continue
            if (!isStraightLineEveryIteration(loop, keyUpdates, dominance)) continue
            if (!isSafeField(loop, key, keyUpdates)) continue
            rewriteField(loop, exit, key, keyUpdates)
            changed = true
        }
        return changed
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
