package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.FunctionDominanceInfo
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.PointerType

object PointerSlotForwardingPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        var changed = false
        val dominatorTree = am.get(DominatorTreeAnalysis::class)
        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            val dominanceInfo = dominatorTree.getFunctionInfo(function) ?: continue
            changed = runOnFunction(function, dominanceInfo) || changed
        }
        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun runOnFunction(function: Function, dominanceInfo: FunctionDominanceInfo): Boolean {
        var changed = false
        val allocas = function.basicBlocks
            .flatMap { block -> block.instructions }
            .filterIsInstance<AllocaInst>()
        for (alloca in allocas) {
            if (alloca.allocatedType !is PointerType) continue
            val uses = alloca.getUses()
            val stores: List<StoreInst> = uses.filterIsInstance<StoreInst>().filter { store -> store.pointer == alloca }
            if (stores.size != 1) continue
            val storedValue = stores.single().value
            if (storedValue == alloca) continue
            val loads: List<LoadInst> = uses.filterIsInstance<LoadInst>().filter { load -> load.pointer == alloca }
            if (loads.isEmpty()) continue
            if (uses.any { user -> user !in stores && user !in loads }) continue
            if (loads.any { load -> !dominates(stores.single(), load, dominanceInfo) }) continue

            for (load in loads) {
                replaceAllUses(function, load, storedValue)
            }
            val loadsToRemove = loads.toSet()
            for (block in function.basicBlocks) {
                block.instructions.removeAll(loadsToRemove)
                block.instructions.remove(stores.single())
                block.instructions.remove(alloca)
            }
            changed = true
        }
        return changed
    }

    private fun dominates(
        dominator: Instruction,
        dominated: Instruction,
        dominanceInfo: FunctionDominanceInfo,
    ): Boolean {
        val dominatorBlock = dominator.getParent() as? BasicBlock ?: return false
        val dominatedBlock = dominated.getParent() as? BasicBlock ?: return false
        if (dominatorBlock === dominatedBlock) {
            val instructions = dominatorBlock.instructions
            return instructions.indexOf(dominator) <= instructions.indexOf(dominated)
        }

        var current: BasicBlockId? = dominatedBlock.id
        while (current != null) {
            if (current == dominatorBlock.id) return true
            current = dominanceInfo.immediateDominators[current]
        }
        return false
    }

    private fun replaceAllUses(function: Function, oldValue: Value, newValue: Value) {
        for (block in function.basicBlocks) {
            for (instruction in block.instructions) {
                if (instruction == oldValue) continue
                replaceUses(instruction, oldValue, newValue)
            }
            block.terminator?.let { replaceUses(it, oldValue, newValue) }
        }
    }

    private fun replaceUses(user: User, oldValue: Value, newValue: Value) {
        for (index in 0 until user.getNumOperands()) {
            if (user.getOperand(index) == oldValue) {
                user.setOperand(index, newValue)
            }
        }
    }
}
