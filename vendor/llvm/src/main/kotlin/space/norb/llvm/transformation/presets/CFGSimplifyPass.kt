package space.norb.llvm.transformation.presets

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.binary.AndInst
import space.norb.llvm.instructions.binary.OrInst
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.VoidType
import space.norb.llvm.utils.Renamer
import space.norb.llvm.values.constants.IntConstant

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

        // Replace all uses of phi nodes from 'other' with their incoming values. Rewrite exactly the
        // registered users of each phi rather than scanning the whole (post-inline, possibly huge)
        // function, which would make CFGSimplify O(devours * functionSize).
        for ((phi, replacement) in phiReplacements) {
            phi.replaceAllUsesWith(replacement)
        }

        // 2. Update phi nodes in successors of 'other' to reference 'this' instead of 'other'
        for (successor in nextTerminator.getSuccessors()) {
            for (instruction in successor.instructions) {
                instruction.replaceUsesOfWith(other, this)
            }
        }

        // Merge in place: drop this block's terminator, then append the collapse marker and 'other's
        // body. Rebuilding the full instruction list per devour is O(thisSize), which makes
        // collapsing a chain of blocks into one O(chainLength^2); appending is O(otherSize).
        val oldTerminator = this.terminator
        if (this.instructions.lastOrNull() === oldTerminator && oldTerminator is TerminatorInst) {
            this.instructions.removeAt(this.instructions.size - 1)
        } else {
            this.instructions.removeAll { it is TerminatorInst }
        }
        this.instructions.add(CommentAttachment(name = Renamer.another(), comment = "Collapsed block ${other.name}"))
        other.instructions.filterTo(this.instructions) { it !is TerminatorInst && it !is PhiNode }
        this.instructions.add(nextTerminator)
        this.terminator = nextTerminator
    }

    override fun run(module: Module, am: AnalysisManager): Module {
        // 0. Flatten short-circuit branch cascades (|| / && chains → or/and i1).
        flattenBranchCascades(module, am)
        // 1. Run dead code elimination to remove unreachable blocks.
        DeadCodeEliminationPass.run(module, am)
        DeadCodeEliminationPass.updateAnalysisManager(am)
        // 2. Now all blocks except the entry blocks have predecessors. Perform basic block merging.
        val predecessorMap = am.get(PredecessorAnalysis::class)
        for (function in module.functions) {
            // Use a set for membership tests: with a list, both the `contains` guard below and the
            // final removeIf are O(removed) per block, i.e. O(blocks * removed) overall.
            val removedBlocks = hashSetOf<BasicBlockId>()
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

    /**
     * Flattens short-circuit branch cascades into straight-line `or i1` / `and i1` chains.
     *
     * Detects the diamond pattern generated by `||` and `&&`:
     *   entry: br i1 %c, %empty, %compute   (or reversed)
     *   empty: br label %merge              (single-pred forwarding block, no computation)
     *   compute: %v = ...; br label %merge  (side-effect-free, single pred)
     *   merge: %r = phi i1 [true, %empty], [%v, %compute]
     *
     * Replaces with:
     *   %v = ... (hoisted)
     *   %r = or i1 %c, %v   (when true-edge is the short-circuit with constant true)
     *   %r = and i1 %c, %v  (when false-edge is the short-circuit with constant false)
     *
     * Iterates to fixpoint since flattening one link exposes the next in a chain.
     */
    private fun flattenBranchCascades(module: Module, am: AnalysisManager) {
        for (function in module.functions) {
            if (function.isDeclaration) continue
            var changed = true
            while (changed) {
                changed = false
                val predecessorMap = buildPredecessorMap(function.basicBlocks)
                for (block in function.basicBlocks.toList()) {
                    if (flattenOneDiamond(block, function, predecessorMap)) {
                        changed = true
                        break // restart — block list mutated
                    }
                }
            }
        }
        am.invalidateAllIn(PredecessorAnalysis::class)
    }

    private fun flattenOneDiamond(
        block: BasicBlock,
        function: space.norb.llvm.structure.Function,
        predecessorMap: Map<BasicBlockId, List<BasicBlockId>>,
    ): Boolean {
        val branch = block.terminator as? BranchInst ?: return false
        if (!branch.isConditional()) return false

        val cond = branch.getCondition() ?: return false
        val trueBlock = branch.getTrueDestination() as BasicBlock
        val falseBlock = branch.getFalseDestination() as BasicBlock

        // Try: true-edge is the short-circuit (empty) arm
        if (tryFlatten(block, cond, trueBlock, falseBlock, isShortOnTrue = true, function, predecessorMap))
            return true
        // Try: false-edge is the short-circuit (empty) arm
        if (tryFlatten(block, cond, falseBlock, trueBlock, isShortOnTrue = false, function, predecessorMap))
            return true
        return false
    }

    private fun tryFlatten(
        entryBlock: BasicBlock,
        cond: Value,
        shortBlock: BasicBlock,
        computeBlock: BasicBlock,
        isShortOnTrue: Boolean,
        function: space.norb.llvm.structure.Function,
        predecessorMap: Map<BasicBlockId, List<BasicBlockId>>,
    ): Boolean {
        // shortBlock must be an empty forwarding block: single predecessor, only an unconditional branch
        if ((predecessorMap[shortBlock.id]?.size ?: 0) != 1) return false
        val shortTerminator = shortBlock.terminator as? BranchInst ?: return false
        if (!shortTerminator.isUnconditional()) return false
        if (shortBlock.instructions.any { it !is BranchInst }) return false

        val mergeBlock = shortTerminator.getDestination() as BasicBlock

        // computeBlock must also go to the same merge block with single predecessor
        if ((predecessorMap[computeBlock.id]?.size ?: 0) != 1) return false
        val computeTerminator = computeBlock.terminator as? BranchInst ?: return false
        if (!computeTerminator.isUnconditional()) return false
        if (computeTerminator.getDestination() !== mergeBlock) return false

        // All instructions in computeBlock must be side-effect-free and hoistable
        val computeInstructions = computeBlock.instructions.filter { it !is BranchInst }
        if (!computeInstructions.all { isSideEffectFree(it) }) return false
        if (!computeInstructions.all { inst ->
            inst.getOperandsList().all { op -> isAvailableIn(op, entryBlock, computeBlock) }
        }) return false

        // Find the i1 phi in the merge block that matches our pattern
        val i1Type = IntegerType(1)
        for (phi in mergeBlock.instructions.filterIsInstance<PhiNode>()) {
            if (phi.type != i1Type) continue
            if (phi.incomingValues.size != 2) continue

            val fromShort = phi.getIncomingValueForBlock(shortBlock) ?: continue
            val fromCompute = phi.getIncomingValueForBlock(computeBlock) ?: continue
            val shortConst = fromShort as? IntConstant ?: continue

            // Match: true-edge empty with constant true → or i1 %cond, %v
            //        false-edge empty with constant false → and i1 %cond, %v
            val replacement: Instruction = when {
                isShortOnTrue && shortConst.value == 1L ->
                    OrInst.create(Renamer.another(phi.name ?: "or"), i1Type, cond, fromCompute)
                !isShortOnTrue && shortConst.value == 0L ->
                    AndInst.create(Renamer.another(phi.name ?: "and"), i1Type, cond, fromCompute)
                else -> continue
            }

            // Apply transformation:
            // 1. Hoist compute instructions into entryBlock before its terminator
            val terminatorIndex = entryBlock.instructions.indexOfLast { it is TerminatorInst }
            for ((i, inst) in computeInstructions.withIndex()) {
                entryBlock.instructions.add(terminatorIndex + i, inst)
            }
            // 2. Insert the or/and before the terminator
            entryBlock.instructions.add(terminatorIndex + computeInstructions.size, replacement)

            // 3. Replace all uses of phi with the new or/and
            phi.replaceAllUsesWith(replacement)

            // 4. Replace entryBlock's conditional branch with unconditional to merge
            entryBlock.instructions.removeIf { it is BranchInst }
            val newBranch = BranchInst.createUnconditional("br", VoidType, mergeBlock)
            entryBlock.instructions.add(newBranch)
            entryBlock.terminator = newBranch

            // 5. Remove phi from mergeBlock
            mergeBlock.instructions.remove(phi)

            // 6. Remove dead blocks
            function.basicBlocks.remove(shortBlock)
            function.basicBlocks.remove(computeBlock)

            // 7. Fix remaining phis in mergeBlock that referenced the removed blocks
            for (remainingPhi in mergeBlock.instructions.filterIsInstance<PhiNode>()) {
                val valFromShort = remainingPhi.getIncomingValueForBlock(shortBlock)
                val valFromCompute = remainingPhi.getIncomingValueForBlock(computeBlock)
                remainingPhi.removeIncomingForBlock(shortBlock)
                remainingPhi.removeIncomingForBlock(computeBlock)
                val passThroughValue = valFromCompute ?: valFromShort ?: continue
                remainingPhi.addIncomingMutable(passThroughValue, entryBlock)
            }

            return true
        }
        return false
    }

    private fun isSideEffectFree(instruction: Instruction): Boolean =
        instruction !is space.norb.llvm.instructions.memory.StoreInst
            && instruction !is space.norb.llvm.instructions.other.CallInst
            && instruction !is TerminatorInst

    private fun isAvailableIn(value: Value, entryBlock: BasicBlock, computeBlock: BasicBlock): Boolean {
        if (value is IntConstant) return true
        if (value is space.norb.llvm.values.constants.NullPointerConstant) return true
        if (value is space.norb.llvm.structure.Function) return true
        if (value is space.norb.llvm.values.globals.GlobalVariable) return true
        if (value is BasicBlock) return true
        // An instruction is available if it's NOT defined in the compute block
        if (value is Instruction) return value !in computeBlock.instructions
        return true
    }

    private fun buildPredecessorMap(blocks: List<BasicBlock>): Map<BasicBlockId, List<BasicBlockId>> {
        val preds = blocks.associate { it.id to mutableListOf<BasicBlockId>() }.toMutableMap()
        for (block in blocks) {
            for (succ in block.getSuccessors()) {
                preds.getOrPut(succ.id) { mutableListOf() }.add(block.id)
            }
        }
        return preds
    }
}
