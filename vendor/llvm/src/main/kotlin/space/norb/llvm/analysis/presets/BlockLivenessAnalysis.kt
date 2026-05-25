package space.norb.llvm.analysis.presets

import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module

class BlockLivenessLookup : AnalysisResult {
    val liveInTable = mutableMapOf<BasicBlockId, Set<Value>>()
    val liveOutTable = mutableMapOf<BasicBlockId, Set<Value>>()

    fun ofBlock(blockId: BasicBlockId): Pair<Set<Value>, Set<Value>> {
        val liveIn = liveInTable[blockId] ?: emptySet()
        val liveOut = liveOutTable[blockId] ?: emptySet()
        return liveIn to liveOut
    }

    fun ofBlock(block: BasicBlock) = ofBlock(block.id)
}

object BlockLivenessAnalysis : Analysis<BlockLivenessLookup>() {
    override fun compute(module: Module, am: AnalysisManager): BlockLivenessLookup {
        val useDefChain = am.get(UseDefAnalysis::class)
        val lookup = BlockLivenessLookup()
        for (func in module.functions) {
            computeFunction(lookup, func, useDefChain)
        }
        return lookup
    }

    private fun computeFunction(lookup: BlockLivenessLookup, func: Function, udc: UseDefChain) {
        val blockLiveInTable = lookup.liveInTable
        val blockLiveOutTable = lookup.liveOutTable
        val blockUseTable = HashMap<BasicBlockId, Set<Value>>(func.basicBlocks.size)
        val blockDefTable = HashMap<BasicBlockId, Set<Value>>(func.basicBlocks.size)
        val successorTable = HashMap<BasicBlockId, List<BasicBlock>>(func.basicBlocks.size)
        val phiDefTable = HashMap<BasicBlockId, Set<Value>>(func.basicBlocks.size)
        val phiUseTable = HashMap<BasicBlockId, MutableMap<BasicBlockId, MutableSet<Value>>>(func.basicBlocks.size)
        val trackedValues = LinkedHashSet<Value>().apply {
            addAll(func.parameters)
            for (block in func.basicBlocks) {
                addAll(block.instructions)
            }
        }

        fun isTrackedValue(value: Value): Boolean = value in trackedValues

        // Initialize live-in and live-out sets for each block
        for (block in func.basicBlocks) {
            blockLiveInTable[block.id] = emptySet()
            blockLiveOutTable[block.id] = emptySet()

            val blockUses = LinkedHashSet<Value>()
            val blockDefs = LinkedHashSet<Value>()
            val phiDefs = LinkedHashSet<Value>()

            for (instruction in block.instructions) {
                if (instruction is PhiNode) {
                    if (isTrackedValue(instruction) && udc.hasUses(instruction)) {
                        blockDefs.add(instruction)
                        phiDefs.add(instruction)
                    }
                    for ((incomingValue, incomingBlock) in instruction.incomingValues) {
                        if (!isTrackedValue(incomingValue)) continue
                        phiUseTable
                            .getOrPut(incomingBlock.id) { HashMap() }
                            .getOrPut(block.id) { LinkedHashSet() }
                            .add(incomingValue)
                    }
                    continue
                }

                for (operand in udc.getDefs(instruction)) {
                    if (isTrackedValue(operand) && operand !in blockDefs) {
                        blockUses.add(operand)
                    }
                }

                if (isTrackedValue(instruction) && udc.hasUses(instruction)) {
                    blockDefs.add(instruction)
                }
            }

            blockUseTable[block.id] = blockUses
            blockDefTable[block.id] = blockDefs
            phiDefTable[block.id] = phiDefs
            successorTable[block.id] = block.getSuccessors()
        }

        var changed: Boolean
        do {
            changed = false
            for (index in func.basicBlocks.indices.reversed()) {
                val block = func.basicBlocks[index]
                val oldLiveIn = blockLiveInTable[block.id] ?: emptySet()
                val oldLiveOut = blockLiveOutTable[block.id] ?: emptySet()

                // Compute live-out as the union of live-in of successors
                val newLiveOut = LinkedHashSet<Value>()
                for (successor in successorTable[block.id].orEmpty()) {
                    val successorId = successor.id
                    val successorLiveIn = blockLiveInTable[successorId].orEmpty()
                    val phiDefs = phiDefTable[successorId].orEmpty()
                    val phiUses = phiUseTable[block.id]?.get(successorId).orEmpty()

                    if (phiDefs.isEmpty()) {
                        newLiveOut.addAll(successorLiveIn)
                    } else {
                        for (value in successorLiveIn) {
                            if (value !in phiDefs) {
                                newLiveOut.add(value)
                            }
                        }
                    }

                    newLiveOut.addAll(phiUses)
                }

                val newLiveIn = LinkedHashSet<Value>()
                newLiveIn.addAll(blockUseTable[block.id].orEmpty())
                for (value in newLiveOut) {
                    if (value !in blockDefTable[block.id].orEmpty()) {
                        newLiveIn.add(value)
                    }
                }

                if (oldLiveIn != newLiveIn || oldLiveOut != newLiveOut) {
                    blockLiveInTable[block.id] = if (newLiveIn.isEmpty()) emptySet() else newLiveIn
                    blockLiveOutTable[block.id] = if (newLiveOut.isEmpty()) emptySet() else newLiveOut
                    changed = true
                }
            }
        } while (changed)
    }
}
