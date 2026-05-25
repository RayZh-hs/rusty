package space.norb.llvm.analysis.presets

import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module

class InstructionLivenessLookup : AnalysisResult {
    val liveInTable = mutableMapOf<Instruction, Set<Value>>()
    val liveOutTable = mutableMapOf<Instruction, Set<Value>>()

    fun ofInstruction(instruction: Instruction): Pair<Set<Value>, Set<Value>> {
        val liveIn = liveInTable[instruction] ?: emptySet()
        val liveOut = liveOutTable[instruction] ?: emptySet()
        return liveIn to liveOut
    }
}

object InstructionLivenessAnalysis : Analysis<InstructionLivenessLookup>() {
    override fun compute(module: Module, am: AnalysisManager): InstructionLivenessLookup {
        val blockLiveness = am.get(BlockLivenessAnalysis::class)
        val useDefChain = am.get(UseDefAnalysis::class)
        val lookup = InstructionLivenessLookup()

        for (func in module.functions) {
            computeFunction(lookup, func, blockLiveness, useDefChain)
        }

        return lookup
    }

    private fun computeFunction(
        lookup: InstructionLivenessLookup,
        func: Function,
        blockLiveness: BlockLivenessLookup,
        udc: UseDefChain
    ) {
        val trackedValues = LinkedHashSet<Value>().apply {
            addAll(func.parameters)
            for (block in func.basicBlocks) {
                addAll(block.instructions)
            }
        }

        fun isTrackedValue(value: Value): Boolean = value in trackedValues

        for (block in func.basicBlocks) {
            var currentLive = LinkedHashSet<Value>(blockLiveness.liveOutTable[block.id].orEmpty())

            for (instruction in block.instructions.asReversed()) {
                val liveOut = if (currentLive.isEmpty()) emptySet() else LinkedHashSet(currentLive)
                val liveIn = LinkedHashSet<Value>()

                if (instruction !is PhiNode) {
                    for (operand in udc.getDefs(instruction)) {
                        if (isTrackedValue(operand)) {
                            liveIn.add(operand)
                        }
                    }
                }

                if (isTrackedValue(instruction)) {
                    for (value in liveOut) {
                        if (value != instruction) {
                            liveIn.add(value)
                        }
                    }
                } else {
                    liveIn.addAll(liveOut)
                }

                lookup.liveOutTable[instruction] = liveOut
                lookup.liveInTable[instruction] = if (liveIn.isEmpty()) emptySet() else liveIn
                currentLive = liveIn
            }
        }
    }
}
