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
            // The block list is stable while promoting candidates (only instructions are added /
            // removed, never blocks), so compute the block order once instead of per candidate.
            val blockOrder = function.basicBlocks.withIndex().associate { it.value.id to it.index }
            val instructionsToRemove = linkedSetOf<Instruction>()
            for (candidate in candidates) {
                promoteAlloca(function, candidate, dominanceInfo, blockOrder, instructionsToRemove)
            }
            // Remove all promoted loads/stores/allocas in a single sweep over the function rather
            // than one sweep per candidate (which was O(candidates * functionSize)).
            if (instructionsToRemove.isNotEmpty()) {
                for (block in function.basicBlocks) {
                    block.instructions.removeAll(instructionsToRemove)
                }
            }
        }

        return module
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

    private fun promoteAlloca(
        function: Function,
        candidate: PromotionCandidate,
        dominanceInfo: FunctionDominanceInfo,
        blockOrder: Map<BasicBlockId, Int>,
        instructionsToRemove: MutableSet<Instruction>
    ) {
        val alloca = candidate.alloca
        val phiBlocks = computePhiBlocks(candidate.definitionBlocks, dominanceInfo)

        val phiNodes = insertPhiPlaceholders(function, alloca, phiBlocks)
        val loadReplacements = linkedMapOf<LoadInst, Value>()

        renamePromotedValue(
            blockId = function.entryBlock!!.id,
            currentValue = null,
            alloca = alloca,
            phiNodes = phiNodes,
            loadReplacements = loadReplacements,
            instructionsToRemove = instructionsToRemove,
            dominanceInfo = dominanceInfo,
            blockOrder = blockOrder
        )

        // Rewrite every operand that references a promoted load. The instructions carrying such
        // operands are exactly the registered users of those loads, so iterate them via the use
        // registry instead of scanning the whole function (which was O(loads * instructions),
        // quadratic when a variable is read many times). The per-operand replacement is the same
        // single-level rewrite as the previous full-function sweep.
        if (loadReplacements.isNotEmpty()) {
            val affectedUsers = linkedSetOf<User>()
            for (load in loadReplacements.keys) {
                affectedUsers.addAll(load.getUses())
            }
            for (user in affectedUsers) {
                for (index in 0 until user.getNumOperands()) {
                    val replacement = (user.getOperand(index) as? LoadInst)
                        ?.let { loadReplacements[it] } ?: continue
                    user.setOperand(index, replacement)
                }
            }
        }
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

    private fun insertPhiPlaceholders(
        function: Function,
        alloca: AllocaInst,
        phiBlocks: Set<BasicBlockId>
    ): Map<BasicBlockId, PhiNode> {
        if (phiBlocks.isEmpty()) return emptyMap()

        val placeholders = linkedMapOf<BasicBlockId, PhiNode>()
        val usedNames = collectLocalNames(function)
        for (block in function.basicBlocks) {
            if (block.id !in phiBlocks) continue

            val insertionIndex = block.instructions.indexOfFirst { it !is PhiNode }
                .let { if (it >= 0) it else block.instructions.size }
            val phi = PhiNode.createPlaceholder(nextPhiName(alloca.name ?: "mem2reg", usedNames), alloca.allocatedType)
            block.instructions.add(insertionIndex, phi)
            placeholders[block.id] = phi
        }

        return placeholders
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

    private fun renamePromotedValue(
        blockId: BasicBlockId,
        currentValue: Value?,
        alloca: AllocaInst,
        phiNodes: Map<BasicBlockId, PhiNode>,
        loadReplacements: MutableMap<LoadInst, Value>,
        instructionsToRemove: MutableSet<Instruction>,
        dominanceInfo: FunctionDominanceInfo,
        blockOrder: Map<BasicBlockId, Int>
    ) {
        data class RenameFrame(val blockId: BasicBlockId, val incomingValue: Value?)

        val stack = ArrayDeque<RenameFrame>()
        stack.addLast(RenameFrame(blockId, currentValue))

        while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            val block = BasicBlock.fromId(frame.blockId) ?: continue
            var value = phiNodes[frame.blockId] ?: frame.incomingValue

            for (instruction in block.instructions.toList()) {
                when (instruction) {
                    alloca -> instructionsToRemove.add(instruction)
                    is LoadInst -> if (instruction.pointer == alloca) {
                        val replacement = value ?: BuilderUtils.createZeroValue(alloca.allocatedType)
                        loadReplacements[instruction] = replacement
                        instructionsToRemove.add(instruction)
                    }
                    is StoreInst -> if (instruction.pointer == alloca) {
                        value = loadReplacements[instruction.value as? LoadInst] ?: instruction.value
                        instructionsToRemove.add(instruction)
                    }
                }
            }

            for (successor in block.getSuccessors()) {
                val phi = phiNodes[successor.id] ?: continue
                val incoming = value ?: BuilderUtils.createZeroValue(alloca.allocatedType)
                phi.addIncomingMutable(incoming, block)
            }

            val children = dominanceInfo.treeChildren[frame.blockId]
                .orEmpty()
                .sortedByDescending { blockOrder[it] ?: Int.MAX_VALUE }
            for (child in children) {
                stack.addLast(RenameFrame(child, value))
            }
        }
    }
}
