package rusty.asm.support

import rusty.core.RiscvTargetConfig
import rusty.asm.utils.*
import rusty.asm.containsCall
import rusty.asm.hasBody
import rusty.asm.instructionsIncludingTerminator
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.InstructionLivenessAnalysis
import space.norb.llvm.analysis.presets.InstructionLivenessLookup
import space.norb.llvm.analysis.presets.BlockLivenessAnalysis
import space.norb.llvm.analysis.presets.BlockLivenessLookup
import space.norb.llvm.analysis.presets.UseDefAnalysis
import space.norb.llvm.analysis.presets.UseDefChain
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
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
        val linearScanThreshold: Int = 512,
        val instructionLivenessThreshold: Int = 2_000,
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

        val parameters = function.parameters.toSet()
        val keepParametersOnStack = function.containsCall()
        for (value in candidates) {
            if ((value in parameters && keepParametersOnStack) ||
                value.sizeInBytes(function, config.registerBytes) > config.registerBytes
            ) {
                forcedStackSlots[value] = nextStackSlot()
            } else {
                registerCandidates.add(value)
            }
        }

        val useDef = analysisManager.get(UseDefAnalysis::class)
        val instructionCount = function.basicBlocks.sumOf { it.instructionsIncludingTerminator().count() }
        val useLinearScan = registerCandidates.size > config.linearScanThreshold ||
            instructionCount > config.instructionLivenessThreshold

        val colored = if (useLinearScan) {
            val blockLiveness = analysisManager.get(BlockLivenessAnalysis::class)
            linearScanAllocate(function, registerCandidates, useDef, blockLiveness, config, ::nextStackSlot)
        } else {
            val instructionLiveness = analysisManager.get(InstructionLivenessAnalysis::class)
            val graph = buildInterferenceGraph(function, registerCandidates, instructionLiveness)
            colorGraph(graph, useDef, config.allocatableRegisters, ::nextStackSlot)
        }

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
        instructionLiveness: InstructionLivenessLookup,
    ): MutableMap<Value, MutableSet<Value>> {
        val graph = candidates.associateWithTo(linkedMapOf<Value, MutableSet<Value>>()) { linkedSetOf() }

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
            for (instruction in block.instructions) {
                if (instruction in candidates) {
                    val (_, liveOut) = instructionLiveness.ofInstruction(instruction)
                    // A definition interferes with every candidate still live after it
                    liveOut.filter { it in candidates }.forEach { connect(instruction, it) }
                }
            }
        }

        return graph
    }

    private data class LiveInterval(
        val value: Value,
        val start: Int,
        val end: Int,
        val order: Int,
        var register: Register? = null,
    )

    private fun linearScanAllocate(
        function: Function,
        candidates: LinkedHashSet<Value>,
        useDef: UseDefChain,
        blockLiveness: BlockLivenessLookup,
        config: Config,
        nextStackSlot: () -> SavableSlot.Stack,
    ): Map<Value, SavableSlot> {
        if (candidates.isEmpty()) return emptyMap()

        val positions = linkedMapOf<Instruction, Int>()
        val blockFirstPosition = linkedMapOf<BasicBlock, Int>()
        val blockLastPosition = linkedMapOf<BasicBlock, Int>()
        var nextPosition = 1
        for (block in function.basicBlocks) {
            val firstPosition = nextPosition
            var lastPosition = nextPosition
            for (instruction in block.instructionsIncludingTerminator()) {
                positions[instruction] = nextPosition
                lastPosition = nextPosition
                nextPosition += 1
            }
            blockFirstPosition[block] = firstPosition
            blockLastPosition[block] = lastPosition
        }

        val functionEnd = nextPosition
        val parameters = function.parameters.toSet()
        val stableOrder = candidates.withIndex().associate { (index, value) -> value to index }

        val intervals = candidates.map { value ->
            var start = if (value in parameters) 0 else (positions[value as? Instruction] ?: 0)
            var end = start

            for (user in useDef.getUses(value)) {
                val userPosition = positions[user as? Instruction] ?: functionEnd
                end = maxOf(end, userPosition)

                if (user is PhiNode) {
                    for ((incomingValue, incomingBlock) in user.incomingValues) {
                        if (incomingValue == value) {
                            end = maxOf(end, blockLastPosition[incomingBlock] ?: userPosition)
                        }
                    }
                }
            }

            for (block in function.basicBlocks) {
                val (liveIn, liveOut) = blockLiveness.ofBlock(block)
                if (value in liveIn) {
                    start = minOf(start, blockFirstPosition.getValue(block))
                    end = maxOf(end, blockFirstPosition.getValue(block))
                }
                if (value in liveOut) {
                    start = minOf(start, blockFirstPosition.getValue(block))
                    end = maxOf(end, blockLastPosition.getValue(block))
                }
            }

            if (start > end) start = end

            LiveInterval(
                value = value,
                start = start,
                end = end,
                order = stableOrder.getValue(value),
            )
        }.sortedWith(compareBy<LiveInterval> { it.start }.thenBy { it.end }.thenBy { it.order })

        val registers = linearScanRegisterOrder(function, config.allocatableRegisters)
        if (registers.isEmpty()) {
            return candidates.associateWithTo(linkedMapOf()) { nextStackSlot() }
        }

        val allocation = linkedMapOf<Value, SavableSlot>()
        val active = mutableListOf<LiveInterval>()

        fun sortActive() {
            active.sortWith(compareBy<LiveInterval> { it.end }.thenBy { it.order })
        }

        fun expireOldIntervals(start: Int) {
            active.removeAll { it.end < start }
            sortActive()
        }

        fun firstFreeRegister(): Register {
            val used = active.mapNotNullTo(mutableSetOf()) { it.register }
            return registers.first { it !in used }
        }

        for (interval in intervals) {
            expireOldIntervals(interval.start)

            if (active.size == registers.size) {
                val spill = active.maxWith(compareBy<LiveInterval> { it.end }.thenByDescending { it.order })
                if (spill.end > interval.end) {
                    val register = spill.register ?: firstFreeRegister()
                    allocation[spill.value] = nextStackSlot()
                    active.remove(spill)
                    interval.register = register
                    allocation[interval.value] = SavableSlot.Register(register)
                    active.add(interval)
                    sortActive()
                } else {
                    allocation[interval.value] = nextStackSlot()
                }
            } else {
                val register = firstFreeRegister()
                interval.register = register
                allocation[interval.value] = SavableSlot.Register(register)
                active.add(interval)
                sortActive()
            }
        }

        for (value in candidates) {
            allocation.putIfAbsent(value, nextStackSlot())
        }
        return allocation
    }

    private fun linearScanRegisterOrder(function: Function, registers: List<Register>): List<Register> {
        if (!function.containsCall()) return registers

        val calleeSaved = calleeSavedRegisters.toSet()
        return registers.filter { it in calleeSaved }
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
