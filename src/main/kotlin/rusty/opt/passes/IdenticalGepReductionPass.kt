package rusty.opt.passes

// Folds identical GetElementPtr instructions into a single instruction, to be run as a cleanup pass after Mem2Reg.

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.core.ValueUseRegistry
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass

object IdenticalGepReductionPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        var changed = false
        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            changed = runOnFunction(function) || changed
        }
        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun runOnFunction(function: Function): Boolean {
        var changed = false
        val available = linkedMapOf<GepKey, GetElementPtrInst>()

        for (block in function.basicBlocks) {
            available.clear()
            val iterator = block.instructions.listIterator()
            while (iterator.hasNext()) {
                val inst = iterator.next()
                if (inst !is GetElementPtrInst) continue

                val key = GepKey(inst.elementType, inst.pointer, inst.indices, inst.isInBounds)
                val existing = available[key]
                if (existing == null) {
                    available[key] = inst
                    continue
                }

                inst.replaceAllUsesWith(existing)
                inst.detachOperands()
                iterator.remove()
                changed = true
            }
        }

        return changed
    }

    private data class GepKey(
        val elementType: Any,
        val pointer: Value,
        val indices: List<Value>,
        val inBounds: Boolean,
    )

    private fun User.detachOperands() {
        for (index in 0 until getNumOperands()) {
            ValueUseRegistry.unregisterUse(getOperand(index), this)
        }
    }
}
