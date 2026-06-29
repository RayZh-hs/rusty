package rusty.asm.support

import rusty.core.RiscvTargetConfig
import rusty.asm.utils.*
import rusty.asm.hasBody
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.analysis.presets.UseDefAnalysis
import space.norb.llvm.analysis.presets.UseDefChain
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.types.VoidType
import space.norb.llvm.utils.computeLayout

object RegisterAllocator {
    // Each loop nesting level multiplies a use's spill weight by this factor, capped at
    // MAX_WEIGHTED_DEPTH levels so deeply nested code cannot overflow the Long accumulator.
    private const val LOOP_WEIGHT_BASE = 10L
    private const val MAX_WEIGHTED_DEPTH = 6

    // Upper bound on how many register candidates may be simultaneously live before the allocator
    // starts force-spilling some of them. Graph coloring needs an interference edge between every
    // pair of values live at the same program point, so an unbounded live set yields an O(N^2)
    // interference graph (pathologically, thousands of entry-block allocas that are all live from
    // entry to their single use). Capping the live set caps the graph's degree and therefore its
    // memory, keeping consumption comparable to linear scan. The cap only needs to exceed the
    // register count — at most that many values can be colored at any point anyway — and the values
    // it spills are chosen by furthest next use (see computeForcedSpills), so colorable values stay
    // in registers. Real functions never approach this many simultaneously-live values, so normal
    // allocation is unaffected; only pathological inputs trip the cap.
    private const val DEFAULT_MAX_LIVE_CANDIDATES = 64

    data class Config(
        val allocatableRegisters: List<Register> = defaultAllocatableRegisters,
        val registerBytes: Int = RiscvTargetConfig.REGISTER_BYTES,
        val maxLiveCandidates: Int = DEFAULT_MAX_LIVE_CANDIDATES,
    )

    val defaultAllocatableRegisters: List<Register> =
        callerSavedRegisters + calleeSavedRegisters - reservedScratchRegisters.toSet()

    fun allocate(
        module: Module,
        analysisManager: AnalysisManager = AnalysisManager(module),
        config: Config = Config(),
    ): Map<Function, Map<Value, SavableSlot>> {
        require(config.allocatableRegisters.isNotEmpty()) { "At least one allocatable register is required" }
        require(config.registerBytes > 0) { "Register size must be positive" }

        return module.functions
            .filter { it.hasBody() }
            .associateWithTo(linkedMapOf()) { allocateFunction(it, analysisManager, config) }
    }

    fun allocateFunction(
        function: Function,
        analysisManager: AnalysisManager = AnalysisManager(function.module),
        config: Config = Config(),
    ): Map<Value, SavableSlot> {
        require(config.allocatableRegisters.isNotEmpty()) { "At least one allocatable register is required" }
        require(config.registerBytes > 0) { "Register size must be positive" }

        val candidates = collectCandidates(function)
        val forcedStackSlots = linkedMapOf<Value, SavableSlot.Stack>()
        val registerCandidates = LinkedHashSet<Value>()
        var nextStackSlotId = 0

        fun nextStackSlot(): SavableSlot.Stack {
            return SavableSlot.Stack(nextStackSlotId++)
        }

        for (value in candidates) {
            if (value.sizeInBytes(function, config.registerBytes) > config.registerBytes) {
                forcedStackSlots[value] = nextStackSlot()
            } else {
                registerCandidates.add(value)
            }
        }

        val useDef = analysisManager.get(UseDefAnalysis::class)
        val blockLiveness = computeBlockLiveness(function, useDef)
        val loopDepth = computeLoopDepth(function, analysisManager)
        val spillWeight = computeSpillWeights(function, useDef, loopDepth)

        // Cap peak register pressure before building the graph. Values evicted here are spilled
        // directly to the stack and excluded from the interference graph, keeping its size bounded.
        val forcedSpills = computeForcedSpills(function, registerCandidates, useDef, blockLiveness, config)
        for (value in forcedSpills) {
            forcedStackSlots[value] = nextStackSlot()
        }
        val graphCandidates =
            if (forcedSpills.isEmpty()) registerCandidates
            else registerCandidates.filterTo(LinkedHashSet()) { it !in forcedSpills }

        val interference =
            buildInterferenceGraph(function, graphCandidates, useDef, blockLiveness, loopDepth, forcedSpills)

        // Stage 1 coalescing: merge each phi with copy-related operands so they share a register and the
        // phi-resolution move disappears (see RegallocCoalescing). Coloring then runs on the merged graph
        // of representatives, and every original value inherits its representative's slot.
        val moves = collectMovePairs(function, graphCandidates)
        val graphOrder = HashMap<Value, Int>()
        for ((index, value) in interference.graph.keys.withIndex()) graphOrder[value] = index
        val coalescing = RegallocCoalescing.run(
            interference.graph,
            moves,
            config.allocatableRegisters.size,
        ) { graphOrder[it] ?: Int.MAX_VALUE }

        // Project the per-value spill weights and call-crossing bias onto representatives: a merged node's
        // spill cost is the sum of its members' costs, and it prefers callee-saved registers if any member
        // is live across a loop call.
        val representativeOf = coalescing.representativeOf
        val mergedSpillWeight = HashMap<Value, Long>()
        val mergedCallCrossing = LinkedHashSet<Value>()
        for ((value, representative) in representativeOf) {
            mergedSpillWeight[representative] = (mergedSpillWeight[representative] ?: 0L) + (spillWeight[value] ?: 0L)
            if (value in interference.callCrossing) mergedCallCrossing.add(representative)
        }

        val colored = colorGraph(
            coalescing.mergedGraph,
            mergedSpillWeight,
            mergedCallCrossing,
            config.allocatableRegisters,
            ::nextStackSlot,
        )

        return candidates.associateWithTo(linkedMapOf()) { value ->
            forcedStackSlots[value] ?: colored.getValue(representativeOf.getValue(value))
        }
    }

    /**
     * Copy-related node pairs for coalescing. In SSA the only true value-to-value copies are phi operands:
     * a phi `p = phi [v, B], ...` is a copy of `v` along the edge from `B`, so (p, v) is a coalescing
     * candidate. Operands that are not register candidates (constants, globals, force-spilled values) are
     * skipped — they are never in the interference graph and cannot share a register with the phi.
     */
    private fun collectMovePairs(function: Function, graphCandidates: Set<Value>): List<Pair<Value, Value>> {
        val moves = mutableListOf<Pair<Value, Value>>()
        for (block in function.basicBlocks) {
            for (phi in block.instructions.filterIsInstance<PhiNode>()) {
                if (phi !in graphCandidates) continue
                for ((incomingValue, _) in phi.incomingValues) {
                    if (incomingValue in graphCandidates) moves.add(phi to incomingValue)
                }
            }
        }
        return moves
    }

    /**
     * Frequency-weighted spill cost. Each definition and use of a value contributes
     * `LOOP_WEIGHT_BASE^loopDepth(block)` so that a value touched inside a hot loop is far
     * more expensive to spill than one touched only in straight-line code. Without this the
     * allocator spilled loop-resident values whenever a colder value happened to have a higher
     * static use count, paying a load+store every iteration.
     */
    private fun computeSpillWeights(
        function: Function,
        useDef: UseDefChain,
        loopDepth: Map<BasicBlock, Int>,
    ): Map<Value, Long> {
        val weights = linkedMapOf<Value, Long>()

        fun add(value: Value, amount: Long) {
            weights[value] = (weights[value] ?: 0L) + amount
        }

        for (block in function.basicBlocks) {
            val blockWeight = loopWeight(loopDepth[block] ?: 0)
            for (instruction in block.instructions) {
                // The definition site itself costs a store if the value is spilled.
                add(instruction, blockWeight)
                // Every operand is a use of the value that defines it at this program point.
                for (operand in useDef.getDefs(instruction)) {
                    add(operand, blockWeight)
                }
            }
        }

        return weights
    }

    private fun loopWeight(depth: Int): Long {
        var weight = 1L
        repeat(depth.coerceIn(0, MAX_WEIGHTED_DEPTH)) { weight *= LOOP_WEIGHT_BASE }
        return weight
    }

    /**
     * Loop nesting depth per basic block, derived from natural loops (back edges whose target
     * dominates their source). Nested loops accumulate, so an inner-loop block ends up with a
     * higher depth than the enclosing loop.
     */
    private fun computeLoopDepth(
        function: Function,
        analysisManager: AnalysisManager,
    ): Map<BasicBlock, Int> {
        val depth = function.basicBlocks.associateWithTo(linkedMapOf()) { 0 }
        if (function.basicBlocks.isEmpty()) return depth

        val dominance = analysisManager.get(DominatorTreeAnalysis::class).getFunctionInfo(function) ?: return depth
        val predecessors = analysisManager.get(PredecessorAnalysis::class)
        val blockById = function.basicBlocks.associateBy { it.id }

        for (latch in function.basicBlocks) {
            for (header in latch.getSuccessors()) {
                // A back edge: control flows from latch to a header that dominates it. The dominator
                // tree provides this in O(1); walking the idom chain made back-edge detection O(n^2)
                // on deep CFGs such as long else-if ladders.
                if (!dominance.dominates(header.id, latch.id)) continue

                val loopBlocks = linkedSetOf(header, latch)
                val worklist = ArrayDeque<BasicBlock>()
                worklist.add(latch)
                while (worklist.isNotEmpty()) {
                    val block = worklist.removeFirst()
                    for (predId in predecessors[block.id].orEmpty()) {
                        val pred = blockById[predId] ?: continue
                        if (loopBlocks.add(pred)) worklist.add(pred)
                    }
                }

                for (block in loopBlocks) depth[block] = (depth[block] ?: 0) + 1
            }
        }

        return depth
    }

    private fun collectCandidates(function: Function): LinkedHashSet<Value> {
        val values = LinkedHashSet<Value>()
        function.parameters.filterTo(values) { it.type != VoidType && it.hasUses() }
        for (block in function.basicBlocks) {
            block.instructions.filterTo(values) { it.type != VoidType && it.hasUses() }
        }
        return values
    }

    private data class InterferenceResult(
        val graph: MutableMap<Value, MutableSet<Value>>,
        val callCrossing: Set<Value>,
    )

    private fun buildInterferenceGraph(
        function: Function,
        candidates: Set<Value>,
        useDef: UseDefChain,
        blockLiveness: BlockLiveness,
        loopDepth: Map<BasicBlock, Int>,
        forcedSpills: Set<Value>,
    ): InterferenceResult {
        val graph = candidates.associateWithTo(linkedMapOf<Value, MutableSet<Value>>()) { linkedSetOf() }
        val callCrossing = LinkedHashSet<Value>()
        val trackedValues = trackedValues(function)

        // Force-spilled values occupy a stack slot, never a register, so they create no interference
        // and need not be tracked. Dropping them keeps the live set (and thus the graph) bounded.
        fun isTrackedValue(value: Value): Boolean = value in trackedValues && value !in forcedSpills

        fun connect(lhs: Value, rhs: Value) {
            if (lhs != rhs) {
                graph.getOrPut(lhs) { linkedSetOf() }.add(rhs)
                graph.getOrPut(rhs) { linkedSetOf() }.add(lhs)
            }
        }

        fun connectAll(liveValues: Set<Value>) {
            val values = liveValues.filter { it in candidates }
            // Any values live together at the same program point cannot share a register
            for (index in values.indices) {
                for (otherIndex in index + 1 until values.size) {
                    connect(values[index], values[otherIndex])
                }
            }
        }

        // Parameters are all materialized from ABI argument locations at function entry.
        // Even if their later live ranges do not overlap, assigning two used parameters
        // to the same register would make eager entry moves overwrite one input value.
        connectAll(function.parameters.toSet())

        for (block in function.basicBlocks) {
            // Only calls inside loops make the caller-saved spill catastrophic: a value live
            // across such a call is stored and reloaded on every iteration. A call in
            // straight-line code (including a recursive self-call, which is not a CFG loop)
            // spills at most once, and forcing those values into callee-saved registers would
            // add a prologue/epilogue save to every invocation — a net loss for leaf-heavy
            // recursion. So we only bias values that cross a call within a loop.
            val callInLoop = (loopDepth[block] ?: 0) > 0
            var currentLive = blockLiveness.liveOut[block].orEmpty()
                .filterTo(LinkedHashSet()) { it !in forcedSpills }

            for (instruction in block.instructions.asReversed()) {
                if (instruction in candidates) {
                    // A definition interferes with every candidate still live after it
                    currentLive.filter { it in candidates }.forEach { connect(instruction, it) }
                }

                if (instruction is CallInst && callInLoop) {
                    // Anything live across the call (its live-out, minus the call result) must
                    // survive a clobber of every caller-saved register. Bias such values toward
                    // callee-saved registers so they are preserved by one prologue save instead
                    // of a store/reload around every loop iteration.
                    for (value in currentLive) {
                        if (value != instruction && value in candidates) callCrossing.add(value)
                    }
                }

                val liveIn = LinkedHashSet<Value>()
                if (instruction !is PhiNode) {
                    for (operand in useDef.getDefs(instruction)) {
                        if (isTrackedValue(operand)) {
                            liveIn.add(operand)
                        }
                    }
                }

                if (isTrackedValue(instruction)) {
                    for (value in currentLive) {
                        if (value != instruction) {
                            liveIn.add(value)
                        }
                    }
                } else {
                    liveIn.addAll(currentLive)
                }

                currentLive = liveIn
            }
        }

        return InterferenceResult(graph, callCrossing)
    }

    /**
     * Decides which register candidates to pre-spill so that no program point keeps more than
     * [Config.maxLiveCandidates] register candidates simultaneously live. This bounds the degree of
     * the interference graph the colorer builds and therefore its memory: without it a function
     * with a very large simultaneously-live set (e.g. thousands of entry-block allocas all live
     * from entry to their single use) produces an O(N^2) graph that exhausts the heap.
     *
     * Liveness here is computed gradually — a single backward walk that only ever holds one live
     * set in memory, never a per-instruction map. Whenever the live set exceeds the cap the value
     * whose next use is furthest in the future is evicted (Bélády's rule, the same furthest-use
     * heuristic that lets linear scan spill well). Spilling the value needed last frees a register
     * for the longest stretch, so the survivors are exactly the ones worth keeping — short-lived
     * temporaries stay in registers instead of being needlessly spilled, keeping both the spill
     * count and the emitted code comparable to linear scan.
     */
    private fun computeForcedSpills(
        function: Function,
        candidates: Set<Value>,
        useDef: UseDefChain,
        blockLiveness: BlockLiveness,
        config: Config,
    ): Set<Value> {
        val cap = maxOf(config.maxLiveCandidates, config.allocatableRegisters.size)
        val forced = LinkedHashSet<Value>()
        if (candidates.size <= cap) return forced

        // Forward instruction numbering. A larger position means "used later", i.e. further away
        // from any earlier program point — exactly the ordering Bélády eviction needs.
        val position = HashMap<Value, Int>()
        var nextPosition = 0
        for (block in function.basicBlocks) {
            for (instruction in block.instructions) position[instruction] = nextPosition++
        }
        val beyondFunction = nextPosition

        // nextUse[v] = position of the nearest use of v at or after the current backward frontier.
        // A value that is live-out of the current block is used only in a successor, so its next use
        // is treated as beyond this block (maximally far, hence preferred for eviction here).
        val nextUse = HashMap<Value, Int>()

        fun evictDownTo(live: LinkedHashSet<Value>) {
            while (live.size > cap) {
                var victim: Value? = null
                var victimUse = Int.MIN_VALUE
                for (value in live) {
                    val use = nextUse[value] ?: beyondFunction
                    if (use > victimUse) {
                        victim = value
                        victimUse = use
                    }
                }
                val chosen = victim ?: break
                live.remove(chosen)
                nextUse.remove(chosen)
                forced.add(chosen)
            }
        }

        for (block in function.basicBlocks) {
            val live = LinkedHashSet<Value>()
            for (value in blockLiveness.liveOut[block].orEmpty()) {
                if (value in candidates && value !in forced) {
                    live.add(value)
                    nextUse[value] = beyondFunction
                }
            }
            evictDownTo(live)

            for (instruction in block.instructions.asReversed()) {
                if (instruction in candidates) {
                    live.remove(instruction)
                    nextUse.remove(instruction)
                }
                if (instruction !is PhiNode) {
                    val usePosition = position[instruction] ?: 0
                    for (operand in useDef.getDefs(instruction)) {
                        if (operand in candidates && operand !in forced) {
                            live.add(operand)
                            nextUse[operand] = usePosition
                        }
                    }
                }
                evictDownTo(live)
            }
        }

        return forced
    }

    private data class BlockLiveness(
        val liveIn: Map<BasicBlock, Set<Value>>,
        val liveOut: Map<BasicBlock, Set<Value>>,
    )

    private fun computeBlockLiveness(function: Function, useDef: UseDefChain): BlockLiveness {
        val liveIn = linkedMapOf<BasicBlock, Set<Value>>()
        val liveOut = linkedMapOf<BasicBlock, Set<Value>>()
        val blockUses = linkedMapOf<BasicBlock, Set<Value>>()
        val blockDefs = linkedMapOf<BasicBlock, Set<Value>>()
        val phiDefs = linkedMapOf<BasicBlock, Set<Value>>()
        val phiUses = linkedMapOf<BasicBlock, MutableMap<BasicBlock, MutableSet<Value>>>()
        val trackedValues = trackedValues(function)

        fun isTrackedValue(value: Value): Boolean = value in trackedValues

        for (block in function.basicBlocks) {
            liveIn[block] = emptySet()
            liveOut[block] = emptySet()

            val uses = LinkedHashSet<Value>()
            val defs = LinkedHashSet<Value>()
            val blockPhiDefs = LinkedHashSet<Value>()

            for (instruction in block.instructions) {
                if (instruction is PhiNode) {
                    if (isTrackedValue(instruction) && useDef.hasUses(instruction)) {
                        defs.add(instruction)
                        blockPhiDefs.add(instruction)
                    }
                    for ((incomingValue, incomingBlock) in instruction.incomingValues) {
                        if (!isTrackedValue(incomingValue)) continue
                        phiUses
                            .getOrPut(incomingBlock) { linkedMapOf() }
                            .getOrPut(block) { LinkedHashSet() }
                            .add(incomingValue)
                    }
                    continue
                }

                for (operand in useDef.getDefs(instruction)) {
                    if (isTrackedValue(operand) && operand !in defs) {
                        uses.add(operand)
                    }
                }

                if (isTrackedValue(instruction) && useDef.hasUses(instruction)) {
                    defs.add(instruction)
                }
            }

            blockUses[block] = uses
            blockDefs[block] = defs
            phiDefs[block] = blockPhiDefs
        }

        var changed: Boolean
        do {
            changed = false
            for (block in function.basicBlocks.asReversed()) {
                val oldLiveIn = liveIn[block].orEmpty()
                val oldLiveOut = liveOut[block].orEmpty()

                val newLiveOut = LinkedHashSet<Value>()
                for (successor in block.getSuccessors()) {
                    val successorLiveIn = liveIn[successor].orEmpty()
                    val successorPhiDefs = phiDefs[successor].orEmpty()
                    val edgePhiUses = phiUses[block]?.get(successor).orEmpty()

                    if (successorPhiDefs.isEmpty()) {
                        newLiveOut.addAll(successorLiveIn)
                    } else {
                        for (value in successorLiveIn) {
                            if (value !in successorPhiDefs) {
                                newLiveOut.add(value)
                            }
                        }
                    }

                    newLiveOut.addAll(edgePhiUses)
                }

                val newLiveIn = LinkedHashSet<Value>()
                newLiveIn.addAll(blockUses[block].orEmpty())
                for (value in newLiveOut) {
                    if (value !in blockDefs[block].orEmpty()) {
                        newLiveIn.add(value)
                    }
                }

                if (oldLiveIn != newLiveIn || oldLiveOut != newLiveOut) {
                    liveIn[block] = if (newLiveIn.isEmpty()) emptySet() else newLiveIn
                    liveOut[block] = if (newLiveOut.isEmpty()) emptySet() else newLiveOut
                    changed = true
                }
            }
        } while (changed)

        return BlockLiveness(liveIn, liveOut)
    }

    private fun trackedValues(function: Function): Set<Value> =
        LinkedHashSet<Value>().apply {
            addAll(function.parameters)
            for (block in function.basicBlocks) {
                addAll(block.instructions)
            }
        }

    private fun colorGraph(
        graph: Map<Value, Set<Value>>,
        spillWeight: Map<Value, Long>,
        callCrossing: Set<Value>,
        registers: List<Register>,
        nextStackSlot: () -> SavableSlot.Stack,
    ): Map<Value, SavableSlot> {
        if (graph.isEmpty()) return emptyMap()

        // Registers a value would rather avoid given how it is used. A value live across a call
        // prefers callee-saved registers (preserved for free across the call); everything else
        // prefers caller-saved registers so we do not needlessly grow the prologue save set.
        val allocatable = registers.toSet()
        val callerFirst = (callerSavedRegisters + calleeSavedRegisters).filter { it in allocatable }
        val calleeFirst = (calleeSavedRegisters + callerSavedRegisters).filter { it in allocatable }

        // Record stable order so equal-cost choices do not depend on hash iteration.
        // Just to make output deterministic to simplify debugging. Does not affect correctness.
        val values = mutableListOf<Value>()
        val stableOrder = mutableMapOf<Value, Int>()
        var nextOrder = 0
        for (value in graph.keys) {
            values.add(value)
            stableOrder[value] = nextOrder
            nextOrder += 1
        }

        // Initialize work set and a loop-frequency-weighted spill cost.
        val remaining = linkedSetOf<Value>()
        val spillCost = mutableMapOf<Value, Long>()
        val currentDegree = mutableMapOf<Value, Int>()
        for (value in values) {
            remaining.add(value)
            spillCost[value] = (spillWeight[value] ?: 0L).coerceAtLeast(1L)
            currentDegree[value] = graph[value].orEmpty().size
        }

        val selectStack = mutableListOf<Value>()

        // Lazy priority queue over the currently-simplifiable nodes (degree < registers.size),
        // ordered by the exact same key the linear scan used to pick a node: degree ascending, then
        // spill cost descending, then stable order ascending. Degrees only ever decrease during
        // simplification, so a node, once below the register count, stays simplifiable; a decrease
        // just pushes a fresher (lower-degree) entry and the stale one is skipped on poll. This makes
        // the simplify phase O((V + E) log V) instead of O(V^2) while selecting an identical node
        // every step, so the resulting coloring (and emitted asm) is unchanged.
        val k = registers.size
        // An entry snapshots the node's degree at enqueue time, so the queue key is immutable (the
        // heap invariant holds even as degrees change). Tie-break exactly as the linear scan did:
        // degree ascending, then spill cost descending, then stable order ascending.
        class SimplifyEntry(val value: Value, val degree: Int)
        val simplifyQueue = java.util.PriorityQueue<SimplifyEntry>(Comparator { a, b ->
            if (a.degree != b.degree) return@Comparator a.degree - b.degree
            val ca = spillCost[a.value] ?: 1L
            val cb = spillCost[b.value] ?: 1L
            if (ca != cb) return@Comparator if (ca > cb) -1 else 1
            (stableOrder[a.value] ?: Int.MAX_VALUE).compareTo(stableOrder[b.value] ?: Int.MAX_VALUE)
        })
        fun enqueueIfSimplifiable(value: Value) {
            val degree = currentDegree[value] ?: 0
            if (degree >= k) return
            simplifyQueue.add(SimplifyEntry(value, degree))
        }

        fun removeFromRemaining(value: Value) {
            if (!remaining.remove(value)) return
            for (neighbor in graph[value].orEmpty()) {
                if (neighbor in remaining) {
                    val degree = (currentDegree[neighbor] ?: 0) - 1
                    currentDegree[neighbor] = degree
                    if (degree < k) enqueueIfSimplifiable(neighbor)
                }
            }
        }

        for (value in remaining) enqueueIfSimplifiable(value)

        // Simplify the graph. Prefer low-degree nodes; when forced, pick a cheap spill.
        while (remaining.isNotEmpty()) {
            var selected: Value? = null

            // Pop the best simplifiable node. A node may have several stale entries (one per degree
            // it passed through) and entries for already-removed nodes; skip any whose snapshot degree
            // no longer matches the live degree, or that are gone / no longer below the register count.
            // Since degrees only decrease, the freshest (lowest-degree) entry sorts first.
            while (simplifyQueue.isNotEmpty()) {
                val candidate = simplifyQueue.peek()
                val liveDegree = currentDegree[candidate.value] ?: 0
                if (candidate.value !in remaining || candidate.degree != liveDegree || liveDegree >= k) {
                    simplifyQueue.poll()
                    continue
                }
                selected = simplifyQueue.poll().value
                break
            }

            if (selected == null) {
                var spillCandidate: Value? = null
                var spillCandidateDegree = 1
                var spillCandidateCost = 1L
                var spillCandidateOrder = Int.MAX_VALUE

                for (value in remaining) {
                    val degree = (currentDegree[value] ?: 0).coerceAtLeast(1)
                    val cost = spillCost[value] ?: 1L
                    val order = stableOrder[value] ?: Int.MAX_VALUE

                    val betterRatio = cost * spillCandidateDegree < spillCandidateCost * degree
                    val sameRatio = cost * spillCandidateDegree == spillCandidateCost * degree
                    val betterDegree = sameRatio && degree > spillCandidateDegree
                    val betterOrder = sameRatio && degree == spillCandidateDegree && order < spillCandidateOrder

                    if (spillCandidate == null || betterRatio || betterDegree || betterOrder) {
                        spillCandidate = value
                        spillCandidateDegree = degree
                        spillCandidateCost = cost
                        spillCandidateOrder = order
                    }
                }

                selected = spillCandidate
            }

            if (selected == null) break
            removeFromRemaining(selected)
            selectStack.add(selected)
        }

        // Rebuild the graph in reverse simplify order and assign actual locations.
        val colored = linkedMapOf<Value, SavableSlot>()

        for (index in selectStack.size - 1 downTo 0) {
            val value = selectStack[index]
            val unavailable = mutableSetOf<Register>()
            for (neighbor in graph[value].orEmpty()) {
                val neighborSlot = colored[neighbor]
                if (neighborSlot is SavableSlot.Register) {
                    unavailable.add(neighborSlot.physical)
                }
            }

            val preferenceOrder = if (value in callCrossing) calleeFirst else callerFirst
            var availableRegister: Register? = null
            for (register in preferenceOrder) {
                if (register !in unavailable) {
                    availableRegister = register
                    break
                }
            }

            if (availableRegister != null) {
                colored[value] = SavableSlot.Register(availableRegister)
            } else {
                colored[value] = nextStackSlot()
            }
        }

        // Return allocation in the same order as the input graph.
        val result = linkedMapOf<Value, SavableSlot>()
        for (value in values) {
            result[value] = colored.getValue(value)
        }
        return result
    }

    private fun Value.sizeInBytes(function: Function, registerBytes: Int): Int {
        val layout = type.computeLayout(function.module, pointerWidthBits = registerBytes * 8)
        return layout.sizeInBytes.toIntExact("Size of ${getIdentifier()}")
    }

    private fun Long.toIntExact(description: String): Int {
        require(this <= Int.MAX_VALUE) { "$description is too large: $this bytes" }
        return toInt()
    }
}
