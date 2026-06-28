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

            // Immediate dominators via Cooper-Harvey-Kennedy ("A Simple, Fast Dominance Algorithm").
            // The previous formulation materialized a full dominator *set* per block and refined it
            // by intersection to a fixpoint, which is O(n^2)-O(n^3) on long CFG chains (e.g. very
            // large else-if ladders) and is recomputed by every dominance-dependent pass. CHK works
            // directly on immediate dominators using reverse-postorder numbering, giving near-linear
            // behavior in practice.
            val postorder = computePostorder(function.entryBlock!!)
            // Higher number == visited later in postorder == closer to the entry.
            val postNumber = HashMap<BasicBlockId, Int>(postorder.size)
            postorder.forEachIndexed { index, id -> postNumber[id] = index }
            val reversePostorder = postorder.asReversed()

            val idom = HashMap<BasicBlockId, BasicBlockId?>(reachableBlocks.size)
            idom[entryId] = entryId

            fun intersect(a: BasicBlockId, b: BasicBlockId): BasicBlockId {
                var finger1 = a
                var finger2 = b
                while (finger1 != finger2) {
                    while ((postNumber[finger1] ?: -1) < (postNumber[finger2] ?: -1)) {
                        finger1 = idom.getValue(finger1)!!
                    }
                    while ((postNumber[finger2] ?: -1) < (postNumber[finger1] ?: -1)) {
                        finger2 = idom.getValue(finger2)!!
                    }
                }
                return finger1
            }

            var changed = true
            while (changed) {
                changed = false
                for (blockId in reversePostorder) {
                    if (blockId == entryId) continue

                    val preds = predecessors[blockId]
                        .orEmpty()
                        .filter { it in reachableBlocks }

                    // Combine only predecessors whose idom is already known this far.
                    var newIdom: BasicBlockId? = null
                    for (pred in preds) {
                        if (idom[pred] == null) continue
                        newIdom = if (newIdom == null) pred else intersect(pred, newIdom)
                    }

                    if (newIdom != null && idom[blockId] != newIdom) {
                        idom[blockId] = newIdom
                        changed = true
                    }
                }
            }

            val immediateDominators = linkedMapOf<BasicBlockId, BasicBlockId?>()
            immediateDominators[entryId] = null
            for (blockId in reversePostorder) {
                if (blockId == entryId) continue
                immediateDominators[blockId] = idom[blockId]
                    ?: error("No immediate dominator found for reachable block $blockId")
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
                        // Walks of distinct predecessors share their upper portion up to idom(blockId).
                        // Once a runner already records blockId in its frontier, every ancestor up to
                        // that idom was populated by the earlier walk, so we can stop. Without this the
                        // computation is O(n^2) on a single merge with many deep predecessors (e.g. a
                        // huge else-if ladder joining to one block) even though the result is O(n).
                        if (!dominanceFrontier.getValue(runner).add(blockId)) break
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

    /**
     * Depth-first postorder of the blocks reachable from [entry] (successor-children appended before
     * their parent). Reversing it yields a reverse-postorder ordering suitable for the CHK dominance
     * fixpoint. Iterative to avoid stack overflow on very long CFG chains.
     */
    private fun computePostorder(entry: BasicBlock): List<BasicBlockId> {
        val postorder = ArrayList<BasicBlockId>()
        val visited = HashSet<BasicBlockId>()
        // Each stack frame tracks the block and an index into its successors.
        val stack = ArrayDeque<Pair<BasicBlock, Iterator<BasicBlock>>>()
        visited.add(entry.id)
        stack.addLast(entry to entry.getSuccessors().iterator())
        while (stack.isNotEmpty()) {
            val (block, successors) = stack.last()
            if (successors.hasNext()) {
                val next = successors.next()
                if (visited.add(next.id)) {
                    stack.addLast(next to next.getSuccessors().iterator())
                }
            } else {
                postorder.add(block.id)
                stack.removeLast()
            }
        }
        return postorder
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
