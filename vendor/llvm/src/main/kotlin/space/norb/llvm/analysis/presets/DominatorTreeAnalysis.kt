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
    val treeChildren: Map<BasicBlockId, Set<BasicBlockId>>,
    // Dominator-tree Euler tour intervals: [enterTime, exitTime] of each block in a DFS of the
    // dominator tree. `a` dominates `b` iff a's interval encloses b's enter time. This makes
    // dominance an O(1) query instead of an O(tree-depth) idom-chain walk, which is what turns
    // per-edge back-edge detection in the loop passes from O(n^2) to O(n) on deep CFGs.
    val enterTime: Map<BasicBlockId, Int> = emptyMap(),
    val exitTime: Map<BasicBlockId, Int> = emptyMap(),
) {
    fun dominates(dominator: BasicBlockId, dominated: BasicBlockId): Boolean {
        val enter = enterTime[dominator] ?: return false
        val exit = exitTime[dominator] ?: return false
        val target = enterTime[dominated] ?: return false
        return enter <= target && target <= exit
    }
}

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

            // Immediate dominators via Lengauer-Tarjan. The earlier Cooper-Harvey-Kennedy formulation
            // is near-linear on typical CFGs but degrades to O(n^2) on a single high-fan-in merge fed
            // by many deep predecessors (the canonical huge else-if ladder joining one block): each
            // predecessor contributes an O(depth) idom-chain finger walk. Lengauer-Tarjan stays
            // near-linear even on that shape. The immediate-dominator relation is unique, so the
            // result — and therefore all downstream output — is identical; only the running time
            // changes. This dominance recompute happens once per dominance-dependent pass, so the
            // speedup compounds across the whole pipeline.
            val idom = computeImmediateDominators(function.entryBlock!!, reachableBlocks, predecessors)

            // Build the maps below in the original reverse-postorder so dominator-tree child sets keep
            // their previous iteration order (downstream consumers that do not re-sort stay unchanged).
            val reversePostorder = computePostorder(function.entryBlock!!).asReversed()

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

            // Euler-tour enter/exit times over the dominator tree, for O(1) dominance queries.
            // Iterative DFS to survive deep dominator trees (long CFG chains).
            val enterTime = HashMap<BasicBlockId, Int>(reachableBlocks.size * 2)
            val exitTime = HashMap<BasicBlockId, Int>(reachableBlocks.size * 2)
            run {
                var timer = 0
                val nodes = ArrayDeque<BasicBlockId>()
                val iterators = ArrayDeque<Iterator<BasicBlockId>>()
                enterTime[entryId] = timer++
                nodes.addLast(entryId)
                iterators.addLast(treeChildren.getValue(entryId).iterator())
                while (nodes.isNotEmpty()) {
                    val iterator = iterators.last()
                    if (iterator.hasNext()) {
                        val child = iterator.next()
                        enterTime[child] = timer++
                        nodes.addLast(child)
                        iterators.addLast(treeChildren.getValue(child).iterator())
                    } else {
                        exitTime[nodes.last()] = timer++
                        nodes.removeLast()
                        iterators.removeLast()
                    }
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
                treeChildren = treeChildren.mapValues { it.value.toSet() },
                enterTime = enterTime,
                exitTime = exitTime,
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

    /**
     * Immediate dominators of the blocks reachable from [entry], computed with the Lengauer-Tarjan
     * algorithm (simple, path-compression variant). Returns a map from every reachable block to its
     * immediate dominator, with [entry] mapped to itself. Near-linear even on degenerate
     * high-fan-in-deep-predecessor CFGs where the iterative finger-walk approach is quadratic.
     */
    private fun computeImmediateDominators(
        entry: BasicBlock,
        reachableBlocks: Set<BasicBlockId>,
        predecessors: PredecessorMap,
    ): HashMap<BasicBlockId, BasicBlockId> {
        val size = reachableBlocks.size
        val num = HashMap<BasicBlockId, Int>(size * 2)
        val byNum = arrayOfNulls<BasicBlockId>(size + 1)
        val parent = IntArray(size + 1)
        val semi = IntArray(size + 1)
        val label = IntArray(size + 1)
        val ancestor = IntArray(size + 1)
        val idomNum = IntArray(size + 1)

        // Depth-first preorder numbering (1-based), iterative to survive very long CFG chains.
        var n = 0
        run {
            n++
            num[entry.id] = n
            byNum[n] = entry.id
            parent[n] = 0
            semi[n] = n
            label[n] = n
            val nodeStack = ArrayDeque<Int>()
            val iterStack = ArrayDeque<Iterator<BasicBlock>>()
            nodeStack.addLast(n)
            iterStack.addLast(entry.getSuccessors().iterator())
            while (nodeStack.isNotEmpty()) {
                val iterator = iterStack.last()
                if (iterator.hasNext()) {
                    val successor = iterator.next()
                    if (successor.id in reachableBlocks && successor.id !in num) {
                        n++
                        num[successor.id] = n
                        byNum[n] = successor.id
                        parent[n] = nodeStack.last()
                        semi[n] = n
                        label[n] = n
                        nodeStack.addLast(n)
                        iterStack.addLast(successor.getSuccessors().iterator())
                    }
                } else {
                    nodeStack.removeLast()
                    iterStack.removeLast()
                }
            }
        }

        // eval(v) returns the vertex with the minimum semidominator on the path from v to the root of
        // its DSU forest, applying iterative path compression (recursion would overflow on long chains).
        fun compress(v: Int) {
            val path = ArrayList<Int>()
            var x = v
            while (ancestor[ancestor[x]] != 0) {
                path.add(x)
                x = ancestor[x]
            }
            for (index in path.indices.reversed()) {
                val node = path[index]
                val anc = ancestor[node]
                if (semi[label[anc]] < semi[label[node]]) label[node] = label[anc]
                ancestor[node] = ancestor[anc]
            }
        }
        fun eval(v: Int): Int {
            if (ancestor[v] == 0) return v
            compress(v)
            return label[v]
        }

        val buckets = Array(size + 1) { mutableListOf<Int>() }

        for (i in n downTo 2) {
            val blockId = byNum[i]!!
            for (predId in predecessors[blockId].orEmpty()) {
                val predNum = num[predId] ?: continue
                val candidate = semi[eval(predNum)]
                if (candidate < semi[i]) semi[i] = candidate
            }
            buckets[semi[i]].add(i)
            ancestor[i] = parent[i]
            for (v in buckets[parent[i]]) {
                val u = eval(v)
                idomNum[v] = if (semi[u] < semi[v]) u else parent[i]
            }
            buckets[parent[i]].clear()
        }
        for (i in 2..n) {
            if (idomNum[i] != semi[i]) idomNum[i] = idomNum[idomNum[i]]
        }

        val result = HashMap<BasicBlockId, BasicBlockId>(size * 2)
        result[entry.id] = entry.id
        for (i in 2..n) {
            result[byNum[i]!!] = byNum[idomNum[i]]!!
        }
        return result
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
