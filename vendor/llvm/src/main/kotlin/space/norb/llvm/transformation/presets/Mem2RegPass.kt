package space.norb.llvm.transformation.presets

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.FunctionDominanceInfo
import space.norb.llvm.analysis.presets.UseDefAnalysis
import space.norb.llvm.analysis.presets.UseDefChain
import space.norb.llvm.builder.BuilderUtils
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass

object Mem2RegPass : IRPass() {
    private data class PromotionCandidate(
        val alloca: AllocaInst,
        val definitionBlocks: Set<BasicBlockId>
    )

    override fun run(module: Module, am: AnalysisManager): Module {
        val useDefChain = am.get(UseDefAnalysis::class)
        val dominatorTree = am.get(DominatorTreeAnalysis::class)

        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            val dominanceInfo = dominatorTree.getFunctionInfo(function) ?: continue
            if (dominanceInfo.reachableBlocks.isEmpty()) continue

            val candidates = findPromotionCandidates(function, useDefChain, dominanceInfo)
            if (candidates.isEmpty()) continue

            // The block list is stable while promoting candidates (only instructions are added /
            // removed, never blocks), so compute the block order once.
            val blockOrder = function.basicBlocks.withIndex().associate { it.value.id to it.index }
            val candidateAllocas = candidates.mapTo(linkedSetOf()) { it.alloca }

            // Insert all phi placeholders up front, in candidate order, sharing one incrementally
            // maintained name set (so generated phi names match a per-alloca recompute exactly).
            // phiByBlock[b] lists the (alloca, phi) pairs whose phi node lives in block b.
            val phiByBlock = HashMap<BasicBlockId, MutableList<Pair<AllocaInst, PhiNode>>>()
            val usedNames = collectLocalNames(function)
            for (candidate in candidates) {
                val phiBlocks = computePhiBlocks(candidate.definitionBlocks, dominanceInfo)
                if (phiBlocks.isEmpty()) continue
                for (block in function.basicBlocks) {
                    if (block.id !in phiBlocks) continue
                    val insertionIndex = block.instructions.indexOfFirst { it !is PhiNode }
                        .let { if (it >= 0) it else block.instructions.size }
                    val phi = PhiNode.createPlaceholder(
                        nextPhiName(candidate.alloca.name ?: "mem2reg", usedNames),
                        candidate.alloca.allocatedType
                    )
                    block.instructions.add(insertionIndex, phi)
                    phiByBlock.getOrPut(block.id) { mutableListOf() }.add(candidate.alloca to phi)
                }
            }

            // Rename every promoted alloca in a SINGLE dominator-tree DFS, maintaining a value stack
            // per alloca (textbook SSA construction). The previous code walked the whole dominator
            // tree once per alloca — O(allocas * blocks) — which is quadratic for functions with many
            // allocas, e.g. a function with local variables inlined many times. The single walk
            // produces the identical phi operands and load replacements.
            val loadReplacements = LinkedHashMap<LoadInst, Value>()
            val instructionsToRemove = linkedSetOf<Instruction>()
            renameAllAllocas(
                function, candidateAllocas, phiByBlock,
                loadReplacements, instructionsToRemove, dominanceInfo, blockOrder
            )

            // Rewrite operands referencing a promoted load. Recorded replacement values are already
            // fully resolved (never themselves a promoted load), so a single pass suffices; iterate
            // the loads' registered users instead of scanning the whole function.
            if (loadReplacements.isNotEmpty()) {
                val affectedUsers = linkedSetOf<User>()
                for (load in loadReplacements.keys) affectedUsers.addAll(load.getUses())
                for (user in affectedUsers) {
                    for (index in 0 until user.getNumOperands()) {
                        val replacement = (user.getOperand(index) as? LoadInst)
                            ?.let { loadReplacements[it] } ?: continue
                        user.setOperand(index, replacement)
                    }
                }
            }
            if (instructionsToRemove.isNotEmpty()) {
                for (block in function.basicBlocks) {
                    block.instructions.removeAll(instructionsToRemove)
                }
            }
        }

        return module
    }

    private fun renameAllAllocas(
        function: Function,
        candidateAllocas: Set<AllocaInst>,
        phiByBlock: Map<BasicBlockId, List<Pair<AllocaInst, PhiNode>>>,
        loadReplacements: MutableMap<LoadInst, Value>,
        instructionsToRemove: MutableSet<Instruction>,
        dominanceInfo: FunctionDominanceInfo,
        blockOrder: Map<BasicBlockId, Int>,
    ) {
        val valueStacks = HashMap<AllocaInst, ArrayDeque<Value>>()
        fun current(alloca: AllocaInst): Value? = valueStacks[alloca]?.lastOrNull()

        class Frame(val children: ArrayDeque<BasicBlockId>, val undo: List<AllocaInst>)

        // Visit a block: define its phis as the current value of their allocas, rewrite the block's
        // loads/stores of promoted allocas, feed successors' phis, and queue dominator-tree children.
        // Returns the list of stack pushes to undo when the block's subtree is finished.
        fun enter(blockId: BasicBlockId): Frame {
            val block = BasicBlock.fromId(blockId) ?: return Frame(ArrayDeque(), emptyList())
            val undo = mutableListOf<AllocaInst>()
            phiByBlock[blockId]?.forEach { (alloca, phi) ->
                valueStacks.getOrPut(alloca) { ArrayDeque() }.addLast(phi)
                undo.add(alloca)
            }
            for (instruction in block.instructions.toList()) {
                when (instruction) {
                    is AllocaInst -> if (instruction in candidateAllocas) instructionsToRemove.add(instruction)
                    is LoadInst -> {
                        val alloca = instruction.pointer as? AllocaInst
                        if (alloca != null && alloca in candidateAllocas) {
                            loadReplacements[instruction] = current(alloca) ?: BuilderUtils.createZeroValue(alloca.allocatedType)
                            instructionsToRemove.add(instruction)
                        }
                    }
                    is StoreInst -> {
                        val alloca = instruction.pointer as? AllocaInst
                        if (alloca != null && alloca in candidateAllocas) {
                            val newValue = (instruction.value as? LoadInst)?.let { loadReplacements[it] } ?: instruction.value
                            valueStacks.getOrPut(alloca) { ArrayDeque() }.addLast(newValue)
                            undo.add(alloca)
                            instructionsToRemove.add(instruction)
                        }
                    }
                }
            }
            for (successor in block.getSuccessors()) {
                phiByBlock[successor.id]?.forEach { (alloca, phi) ->
                    phi.addIncomingMutable(current(alloca) ?: BuilderUtils.createZeroValue(alloca.allocatedType), block)
                }
            }
            val children = dominanceInfo.treeChildren[blockId].orEmpty().sortedBy { blockOrder[it] ?: Int.MAX_VALUE }
            return Frame(ArrayDeque(children), undo)
        }

        val stack = ArrayDeque<Frame>()
        stack.addLast(enter(function.entryBlock!!.id))
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (frame.children.isNotEmpty()) {
                stack.addLast(enter(frame.children.removeFirst()))
            } else {
                for (alloca in frame.undo) valueStacks[alloca]?.removeLast()
                stack.removeLast()
            }
        }
    }

    // Per-alloca block sets gathered in a single pass over the function. Computing these per alloca
    // (the previous gatherUseBlocks/gatherDefinitionBlocks) is O(allocas * instructions), which is
    // quadratic for functions with many allocas (e.g. a heavily-inlined caller).
    private class AllocaBlockIndex {
        val useBlocks = HashMap<AllocaInst, LinkedHashSet<BasicBlockId>>()
        val defBlocks = HashMap<AllocaInst, LinkedHashSet<BasicBlockId>>()
    }

    private fun buildAllocaBlockIndex(function: Function): AllocaBlockIndex {
        val index = AllocaBlockIndex()
        for (block in function.basicBlocks) {
            for (instruction in block.instructions) {
                when (instruction) {
                    is LoadInst -> (instruction.pointer as? AllocaInst)?.let {
                        index.useBlocks.getOrPut(it) { linkedSetOf() }.add(block.id)
                    }
                    is StoreInst -> (instruction.pointer as? AllocaInst)?.let {
                        index.useBlocks.getOrPut(it) { linkedSetOf() }.add(block.id)
                        index.defBlocks.getOrPut(it) { linkedSetOf() }.add(block.id)
                    }
                    else -> {}
                }
            }
        }
        return index
    }

    private fun findPromotionCandidates(
        function: Function,
        useDefChain: UseDefChain,
        dominanceInfo: FunctionDominanceInfo
    ): List<PromotionCandidate> {
        val reachable = dominanceInfo.reachableBlocks
        val candidates = mutableListOf<PromotionCandidate>()
        val blockIndex = buildAllocaBlockIndex(function)

        for (block in function.basicBlocks) {
            if (block.id !in reachable) continue
            for (instruction in block.instructions) {
                if (instruction !is AllocaInst || instruction.name == null) continue

                val uses = useDefChain.getUses(instruction)
                if (uses.any { !isPromotableUse(it, instruction) }) continue

                val useBlocks = blockIndex.useBlocks[instruction].orEmpty()
                if (useBlocks.any { it !in reachable }) continue

                candidates.add(PromotionCandidate(instruction, blockIndex.defBlocks[instruction].orEmpty()))
            }
        }

        return candidates
    }

    private fun isPromotableUse(user: User, alloca: AllocaInst): Boolean = when (user) {
        is LoadInst -> user.pointer == alloca && countOperandUses(user, alloca) == 1
        is StoreInst -> user.pointer == alloca && countOperandUses(user, alloca) == 1
        else -> false
    }

    private fun countOperandUses(user: User, value: Value): Int {
        var count = 0
        for (index in 0 until user.getNumOperands()) {
            if (user.getOperand(index) == value) {
                count++
            }
        }
        return count
    }

    private fun computePhiBlocks(
        definitionBlocks: Set<BasicBlockId>,
        dominanceInfo: FunctionDominanceInfo
    ): Set<BasicBlockId> {
        val phiBlocks = linkedSetOf<BasicBlockId>()
        val worklist = ArrayDeque(definitionBlocks.toList())

        while (worklist.isNotEmpty()) {
            val blockId = worklist.removeFirst()
            for (frontierBlock in dominanceInfo.dominanceFrontier[blockId].orEmpty()) {
                if (!phiBlocks.add(frontierBlock)) continue
                if (frontierBlock !in definitionBlocks) {
                    worklist.add(frontierBlock)
                }
            }
        }

        return phiBlocks
    }

    private fun collectLocalNames(function: Function): MutableSet<String> {
        val names = linkedSetOf<String>()
        function.parameters.mapNotNullTo(names) { it.name }
        for (block in function.basicBlocks) {
            block.instructions.mapNotNullTo(names) { it.name }
        }
        return names
    }

    private fun nextPhiName(baseName: String, usedNames: MutableSet<String>): String {
        var index = 0
        while (true) {
            val candidate = if (index == 0) "$baseName.phi" else "$baseName.phi.$index"
            if (usedNames.add(candidate)) {
                return candidate
            }
            index++
        }
    }

}
