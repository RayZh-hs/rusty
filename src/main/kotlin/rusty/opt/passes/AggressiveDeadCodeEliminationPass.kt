package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.ValueUseRegistry
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import java.util.IdentityHashMap

/**
 * Mark-and-sweep dead code elimination.
 *
 * Algorithm: collect the live set from observable instructions (terminators, stores, calls), then walk
 * operands backwards. Whatever is never reached is dead and removed.
 *
 * Example — a self-referential phi cycle whose only consumers are each other:
 *     x = phi [x0, entry], [y, latch]
 *     y = phi [x,  header]            // x and y feed nothing else
 *   both are deleted.
 *
 * Note: unlike use-list DCE (which only drops instructions with zero uses), this breaks dead cycles.
 * Mem2Reg routinely creates them for loop-carried variables reassigned-before-use.
 */
object AggressiveDeadCodeEliminationPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        for (function in module.functions) {
            if (function.basicBlocks.isEmpty()) continue

            val live: MutableSet<Instruction> =
                java.util.Collections.newSetFromMap(IdentityHashMap<Instruction, Boolean>())
            val worklist = ArrayDeque<Instruction>()

            for (block in function.basicBlocks) {
                for (instruction in block.instructions) {
                    if (isObservable(instruction) && live.add(instruction)) {
                        worklist.addLast(instruction)
                    }
                }
            }

            while (worklist.isNotEmpty()) {
                val instruction = worklist.removeLast()
                for (operand in instruction.getOperandsList()) {
                    if (operand is Instruction && live.add(operand)) {
                        worklist.addLast(operand)
                    }
                }
            }

            for (block in function.basicBlocks) {
                val dead = block.instructions.filter { it !in live }
                if (dead.isEmpty()) continue
                for (instruction in dead) detachOperands(instruction)
                block.instructions.removeAll(dead.toHashSet())
            }
        }
        return module
    }

    /** Instructions whose results may be observed by program execution. These seed the live set. */
    private fun isObservable(instruction: Instruction): Boolean =
        when (instruction) {
            is TerminatorInst -> true
            is StoreInst -> true
            is CallInst -> true
            else -> false
        }

    private fun detachOperands(instruction: Instruction) {
        for (index in 0 until instruction.getNumOperands()) {
            ValueUseRegistry.unregisterUse(instruction.getOperand(index), instruction)
        }
    }
}
