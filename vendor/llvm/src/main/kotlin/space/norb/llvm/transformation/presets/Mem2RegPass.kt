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
            for (candidate in candidates) {
                promoteAlloca(function, candidate, dominanceInfo)
            }
        }

        return module
    }

    private fun findPromotionCandidates(
        function: Function,
        useDefChain: UseDefChain,
        dominanceInfo: FunctionDominanceInfo
    ): List<PromotionCandidate> {
        val reachable = dominanceInfo.reachableBlocks
        val candidates = mutableListOf<PromotionCandidate>()

        for (block in function.basicBlocks) {
            if (block.id !in reachable) continue
            for (instruction in block.instructions) {
                if (instruction !is AllocaInst || instruction.name == null) continue

                val uses = useDefChain.getUses(instruction)
                if (uses.any { !isPromotableUse(it, instruction) }) continue

                val useBlocks = gatherUseBlocks(function, instruction)
                if (useBlocks.any { it !in reachable }) continue

                candidates.add(PromotionCandidate(instruction, gatherDefinitionBlocks(function, instruction)))
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

    private fun gatherUseBlocks(function: Function, alloca: AllocaInst): Set<BasicBlockId> {
        val blocks = linkedSetOf<BasicBlockId>()
        for (block in function.basicBlocks) {
            if (block.instructions.any { instruction ->
                    when (instruction) {
                        is LoadInst -> instruction.pointer == alloca
                        is StoreInst -> instruction.pointer == alloca
                        else -> false
                    }
                }
            ) {
                blocks.add(block.id)
            }
        }
        return blocks
    }

    private fun gatherDefinitionBlocks(function: Function, alloca: AllocaInst): Set<BasicBlockId> {
        val blocks = linkedSetOf<BasicBlockId>()
        for (block in function.basicBlocks) {
            if (block.instructions.any { instruction -> instruction is StoreInst && instruction.pointer == alloca }) {
                blocks.add(block.id)
            }
        }
        return blocks
    }

    private fun promoteAlloca(
        function: Function,
        candidate: PromotionCandidate,
        dominanceInfo: FunctionDominanceInfo
    ) {
        val alloca = candidate.alloca
        val phiBlocks = computePhiBlocks(candidate.definitionBlocks, dominanceInfo)

        val phiNodes = insertPhiPlaceholders(function, alloca, phiBlocks)
        val loadReplacements = linkedMapOf<LoadInst, Value>()
        val instructionsToRemove = linkedSetOf<Instruction>()
        val blockOrder = function.basicBlocks.withIndex().associate { it.value.id to it.index }

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

        // Apply every promoted load's replacement in a single sweep over the function. Doing one
        // full-function scan per load is O(loads * instructions), which is quadratic when a variable
        // is read many times (e.g. the `x` of a huge else-if ladder has one load per comparison).
        if (loadReplacements.isNotEmpty()) {
            for (block in function.basicBlocks) {
                for (instruction in block.instructions) {
                    for (index in 0 until instruction.getNumOperands()) {
                        val replacement = (instruction.getOperand(index) as? LoadInst)
                            ?.let { loadReplacements[it] } ?: continue
                        instruction.setOperand(index, replacement)
                    }
                }
            }
        }

        for (block in function.basicBlocks) {
            block.instructions.removeAll(instructionsToRemove)
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
