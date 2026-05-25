package space.norb.llvm.transformation.presets

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.utils.Renamer

object CFGSimplifyPass : IRPass() {
    private fun BasicBlock.devour(other: BasicBlock) {
        val nextTerminator = requireNotNull(other.terminator) {
            "Cannot merge basic block ${other.name} without a terminator"
        }

        // 1. Resolve phi nodes in the devoured block.
        // Since 'other' has only one predecessor (this), each phi should have an incoming value from 'this'.
        val phiReplacements = mutableMapOf<PhiNode, Value>()
        for (inst in other.instructions.filterIsInstance<PhiNode>()) {
            val incomingValue = inst.getIncomingValueForBlock(this)
                ?: inst.incomingValues.singleOrNull()?.first
                ?: error(
                    "Phi node ${inst.name} in block ${other.name} does not have an incoming value from its predecessor ${this.name}"
                )
            phiReplacements[inst] = incomingValue
        }

        // Replace all uses of phi nodes from 'other' with their incoming values
        for (bb in function.basicBlocks) {
            for (instruction in bb.instructions) {
                for ((phi, replacement) in phiReplacements) {
                    instruction.replaceUsesOfWith(phi, replacement)
                }
            }
        }

        // 2. Update phi nodes in successors of 'other' to reference 'this' instead of 'other'
        for (successor in nextTerminator.getSuccessors()) {
            for (instruction in successor.instructions) {
                instruction.replaceUsesOfWith(other, this)
            }
        }

        val mergedInstructions = this.instructions
            .filterNot { it is TerminatorInst }
            .toMutableList()
        mergedInstructions.add(CommentAttachment(name = Renamer.another(), comment = "Collapsed block ${other.name}"))
        mergedInstructions.addAll(other.instructions.filterNot { it is TerminatorInst || it is PhiNode })
        mergedInstructions.add(nextTerminator)

        this.instructions.clear()
        this.instructions.addAll(mergedInstructions)
        this.terminator = nextTerminator
    }

    override fun run(module: Module, am: AnalysisManager): Module {
        // 1. Run dead code elimination to remove unreachable blocks.
        DeadCodeEliminationPass.run(module, am)
        DeadCodeEliminationPass.updateAnalysisManager(am)
        // 2. Now all blocks except the entry blocks have predecessors. Perform basic block merging.
        val predecessorMap = am.get(PredecessorAnalysis::class)
        for (function in module.functions) {
            val removedBlocks = mutableListOf<BasicBlockId>()
            for (block in function.basicBlocks) {
                if (removedBlocks.contains(block.id)) continue
                var terminator = block.terminator!!
                while (terminator is BranchInst && terminator.isUnconditional()) {
                    val dest = terminator.getDestination() as BasicBlock
                    if (dest.id == block.id) break // Avoid infinite loop on self-loop
                    if (predecessorMap[dest.id]!!.size <= 1) {
                        // This means that the destination block is only reachable from this block
                        removedBlocks.add(dest.id)
                        terminator = dest.terminator!!
                        block.devour(dest)
                    } else break
                }
            }
            // Remove the devoured blocks from the function's basic block list
            function.basicBlocks.removeIf { removedBlocks.contains(it.id) }
        }
        return module
    }
}
