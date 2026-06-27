package rusty.asm.support

import rusty.core.RiscvTargetConfig
import rusty.asm.utils.*
import rusty.asm.hasBody
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.UseDefAnalysis
import space.norb.llvm.analysis.presets.UseDefChain
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.types.VoidType
import space.norb.llvm.utils.computeLayout

object RegisterAllocator {
    data class Config(
        val allocatableRegisters: List<Register> = defaultAllocatableRegisters,
        val registerBytes: Int = RiscvTargetConfig.REGISTER_BYTES,
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
        val graph = buildInterferenceGraph(function, registerCandidates, useDef, blockLiveness)
        val colored = colorGraph(graph, useDef, config.allocatableRegisters, ::nextStackSlot)

        return candidates.associateWithTo(linkedMapOf()) { value ->
            forcedStackSlots[value] ?: colored.getValue(value)
        }
    }

    private fun collectCandidates(function: Function): LinkedHashSet<Value> {
        val values = LinkedHashSet<Value>()
        function.parameters.filterTo(values) { it.type != VoidType && it.hasUses() }
        for (block in function.basicBlocks) {
            block.instructions.filterTo(values) { it.type != VoidType && it.hasUses() }
        }
        return values
    }

    private fun buildInterferenceGraph(
        function: Function,
        candidates: Set<Value>,
        useDef: UseDefChain,
        blockLiveness: BlockLiveness,
    ): MutableMap<Value, MutableSet<Value>> {
        val graph = candidates.associateWithTo(linkedMapOf<Value, MutableSet<Value>>()) { linkedSetOf() }
        val trackedValues = trackedValues(function)

        fun isTrackedValue(value: Value): Boolean = value in trackedValues

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
            var currentLive = LinkedHashSet<Value>(blockLiveness.liveOut[block].orEmpty())

            for (instruction in block.instructions.asReversed()) {
                if (instruction in candidates) {
                    // A definition interferes with every candidate still live after it
                    currentLive.filter { it in candidates }.forEach { connect(instruction, it) }
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

        return graph
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
        useDef: UseDefChain,
        registers: List<Register>,
        nextStackSlot: () -> SavableSlot.Stack,
    ): Map<Value, SavableSlot> {
        if (graph.isEmpty()) return emptyMap()

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

        // Initialize work set and a basic spill cost from use counts.
        val remaining = linkedSetOf<Value>()
        val spillCost = mutableMapOf<Value, Int>()
        val currentDegree = mutableMapOf<Value, Int>()
        for (value in values) {
            remaining.add(value)
            val uses = useDef.getUses(value).size
            spillCost[value] = if (uses > 0) uses else 1
            currentDegree[value] = graph[value].orEmpty().size
        }

        val selectStack = mutableListOf<Value>()

        fun removeFromRemaining(value: Value) {
            if (!remaining.remove(value)) return
            for (neighbor in graph[value].orEmpty()) {
                if (neighbor in remaining) {
                    currentDegree[neighbor] = (currentDegree[neighbor] ?: 0) - 1
                }
            }
        }

        // Simplify the graph. Prefer low-degree nodes; when forced, pick a cheap spill.
        while (remaining.isNotEmpty()) {
            var selected: Value? = null
            var selectedDegree = Int.MAX_VALUE
            var selectedCost = 0
            var selectedOrder = Int.MAX_VALUE

            for (value in remaining) {
                val degree = currentDegree[value] ?: 0
                if (degree >= registers.size) continue

                val cost = spillCost[value] ?: 1
                val order = stableOrder[value] ?: Int.MAX_VALUE
                val betterDegree = degree < selectedDegree
                val betterCost = degree == selectedDegree && cost > selectedCost
                val betterOrder = degree == selectedDegree && cost == selectedCost && order < selectedOrder

                if (selected == null || betterDegree || betterCost || betterOrder) {
                    selected = value
                    selectedDegree = degree
                    selectedCost = cost
                    selectedOrder = order
                }
            }

            if (selected == null) {
                var spillCandidate: Value? = null
                var spillCandidateDegree = 1
                var spillCandidateCost = 1
                var spillCandidateOrder = Int.MAX_VALUE

                for (value in remaining) {
                    val degree = (currentDegree[value] ?: 0).coerceAtLeast(1)
                    val cost = spillCost[value] ?: 1
                    val order = stableOrder[value] ?: Int.MAX_VALUE

                    val betterRatio = cost.toLong() * spillCandidateDegree < spillCandidateCost.toLong() * degree
                    val sameRatio = cost.toLong() * spillCandidateDegree == spillCandidateCost.toLong() * degree
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

            var availableRegister: Register? = null
            for (register in registers) {
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
