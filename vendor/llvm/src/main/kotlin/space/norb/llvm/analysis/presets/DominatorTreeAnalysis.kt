package space.norb.llvm.analysis.presets

import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.FunctionId
import space.norb.llvm.structure.Module

data class FunctionDominanceInfo(
    val reachableBlocks: Set<BasicBlockId>,
    val immediateDominators: Map<BasicBlockId, BasicBlockId?>,
    val dominanceFrontier: Map<BasicBlockId, Set<BasicBlockId>>,
    val treeChildren: Map<BasicBlockId, Set<BasicBlockId>>
)

class DominatorTreeResult(
    private val functionInfo: Map<FunctionId, FunctionDominanceInfo>,
    private val blockToFunction: Map<BasicBlockId, FunctionId>
) : AnalysisResult {
    fun getFunctionInfo(function: Function): FunctionDominanceInfo? = functionInfo[function.id]

    fun getImmediateDominator(block: BasicBlock): BasicBlock? =
        blockToFunction[block.id]
            ?.let(functionInfo::get)
            ?.immediateDominators
            ?.get(block.id)
            ?.let(BasicBlock::fromId)

    fun getDominanceFrontier(block: BasicBlock): Set<BasicBlock> =
        blockToFunction[block.id]
            ?.let(functionInfo::get)
            ?.dominanceFrontier
            ?.get(block.id)
            ?.mapNotNull(BasicBlock::fromId)
            ?.toSet()
            ?: emptySet()

    fun getChildren(block: BasicBlock): Set<BasicBlock> =
        blockToFunction[block.id]
            ?.let(functionInfo::get)
            ?.treeChildren
            ?.get(block.id)
            ?.mapNotNull(BasicBlock::fromId)
            ?.toSet()
            ?: emptySet()
}

@Analysis.Requires(PredecessorAnalysis::class)
object DominatorTreeAnalysis : Analysis<DominatorTreeResult>() {
    override fun compute(module: Module, am: AnalysisManager): DominatorTreeResult {
        val predecessors = am.get(PredecessorAnalysis::class)
        val functionInfo = linkedMapOf<FunctionId, FunctionDominanceInfo>()
        val blockToFunction = linkedMapOf<BasicBlockId, FunctionId>()

        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue

            val reachableBlocks = collectReachableBlocks(function)
            reachableBlocks.forEach { blockToFunction[it] = function.id }

            if (reachableBlocks.isEmpty()) {
                functionInfo[function.id] = FunctionDominanceInfo(
                    reachableBlocks = emptySet(),
                    immediateDominators = emptyMap(),
                    dominanceFrontier = emptyMap(),
                    treeChildren = emptyMap()
                )
                continue
            }

            val entryId = function.entryBlock!!.id
            val dominators = mutableMapOf<BasicBlockId, Set<BasicBlockId>>()
            for (blockId in reachableBlocks) {
                dominators[blockId] = if (blockId == entryId) setOf(entryId) else reachableBlocks
            }

            var changed = true
            while (changed) {
                changed = false
                for (block in function.basicBlocks) {
                    val blockId = block.id
                    if (blockId == entryId || blockId !in reachableBlocks) continue

                    val preds = predecessors[blockId]
                        .orEmpty()
                        .filter { it in reachableBlocks }

                    val newDominators = if (preds.isEmpty()) {
                        setOf(blockId)
                    } else {
                        preds
                            .map { dominators.getValue(it) }
                            .reduce(Set<BasicBlockId>::intersect) + blockId
                    }

                    if (newDominators != dominators[blockId]) {
                        dominators[blockId] = newDominators
                        changed = true
                    }
                }
            }

            val immediateDominators = linkedMapOf<BasicBlockId, BasicBlockId?>()
            immediateDominators[entryId] = null
            for (block in function.basicBlocks) {
                val blockId = block.id
                if (blockId == entryId || blockId !in reachableBlocks) continue

                val strictDominators = dominators.getValue(blockId) - blockId
                val immediateDominator = strictDominators.maxByOrNull { dominators.getValue(it).size }
                    ?: error("No immediate dominator found for block ${block.name}")
                immediateDominators[blockId] = immediateDominator
            }

            val treeChildren = reachableBlocks.associateWith { linkedSetOf<BasicBlockId>() }.toMutableMap()
            for ((blockId, idom) in immediateDominators) {
                if (idom != null) {
                    treeChildren.getValue(idom).add(blockId)
                }
            }

            val dominanceFrontier = reachableBlocks.associateWith { linkedSetOf<BasicBlockId>() }.toMutableMap()
            for (blockId in reachableBlocks) {
                val preds = predecessors[blockId]
                    .orEmpty()
                    .filter { it in reachableBlocks }
                if (preds.size < 2) continue

                val immediateDominator = immediateDominators[blockId]
                for (pred in preds) {
                    var runner: BasicBlockId? = pred
                    while (runner != null && runner != immediateDominator) {
                        dominanceFrontier.getValue(runner).add(blockId)
                        runner = immediateDominators[runner]
                    }
                }
            }

            functionInfo[function.id] = FunctionDominanceInfo(
                reachableBlocks = reachableBlocks,
                immediateDominators = immediateDominators,
                dominanceFrontier = dominanceFrontier.mapValues { it.value.toSet() },
                treeChildren = treeChildren.mapValues { it.value.toSet() }
            )
        }

        return DominatorTreeResult(functionInfo, blockToFunction)
    }

    private fun collectReachableBlocks(function: Function): Set<BasicBlockId> {
        val entry = function.entryBlock ?: return emptySet()
        val reachable = linkedSetOf<BasicBlockId>()
        val worklist = ArrayDeque<BasicBlock>()
        worklist.add(entry)

        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()
            if (!reachable.add(block.id)) continue
            for (successor in block.getSuccessors()) {
                worklist.add(successor)
            }
        }

        return reachable
    }
}
