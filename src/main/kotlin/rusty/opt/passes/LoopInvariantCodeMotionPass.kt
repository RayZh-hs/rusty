package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.FunctionDominanceInfo
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.FCmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass

/**
 * Conservative LICM for natural loops that already have a single preheader.
 *
 * The pass hoists side-effect-free instructions whose operands are loop-invariant
 * into the preheader, but only when the instruction's block dominates every loop
 * exit. That avoids introducing new work on paths where the original instruction
 * might not execute.
 */
object LoopInvariantCodeMotionPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        val predecessors = am.get(PredecessorAnalysis::class)
        val dominators = am.get(DominatorTreeAnalysis::class)
        var changed = false

        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            val domInfo = dominators.getFunctionInfo(function) ?: continue
            val blockById = function.basicBlocks.associateBy { it.id }
            val loops = function.basicBlocks.flatMap { latch ->
                latch.getSuccessors()
                    .filter { header -> dominates(header, latch, domInfo) }
                    .mapNotNull { header ->
                        discoverLoop(header, latch, predecessors, blockById)
                    }
            }

            for (loop in loops.distinctBy { it.header.id to it.blocks.map(BasicBlock::id).sorted() }) {
                val preheader = findSinglePreheader(loop, predecessors, blockById) ?: continue
                changed = hoistLoop(loop, preheader, domInfo) || changed
            }
        }

        if (changed) {
            am.invalidateAll()
        } else {
            am.invalidateNone()
        }
        return module
    }

    private fun discoverLoop(
        header: BasicBlock,
        latch: BasicBlock,
        predecessors: Map<ULong, List<ULong>>,
        blockById: Map<ULong, BasicBlock>,
    ): Loop? {
        val loopBlocks = linkedSetOf(header, latch)
        val worklist = ArrayDeque<BasicBlock>()
        worklist.add(latch)

        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()
            for (predId in predecessors[block.id].orEmpty()) {
                val pred = blockById[predId] ?: continue
                if (loopBlocks.add(pred)) {
                    worklist.add(pred)
                }
            }
        }

        return Loop(header, loopBlocks)
    }

    private fun findSinglePreheader(
        loop: Loop,
        predecessors: Map<ULong, List<ULong>>,
        blockById: Map<ULong, BasicBlock>,
    ): BasicBlock? {
        val loopIds = loop.blocks.mapTo(linkedSetOf(), BasicBlock::id)
        val outsidePreds = predecessors[loop.header.id]
            .orEmpty()
            .filter { it !in loopIds }
            .distinct()
        if (outsidePreds.size != 1) return null

        val preheader = blockById[outsidePreds.single()] ?: return null
        return preheader.takeIf {
            it.terminator?.getSuccessors()?.count { successor -> successor == loop.header } == 1
        }
    }

    private fun hoistLoop(
        loop: Loop,
        preheader: BasicBlock,
        dominance: FunctionDominanceInfo,
    ): Boolean {
        val loopIds = loop.blocks.mapTo(linkedSetOf(), BasicBlock::id)
        val exits = loop.blocks
            .flatMap { block -> block.getSuccessors().filter { it.id !in loopIds } }
            .distinct()
        if (exits.isEmpty()) return false

        var changed = false
        var movedInIteration: Boolean
        do {
            movedInIteration = false
            val instructionOwners = buildInstructionOwners(loop.blocks + preheader)
            for (block in loop.blocks) {
                val iterator = block.instructions.listIterator()
                while (iterator.hasNext()) {
                    val inst = iterator.next()
                    if (!inst.isHoistable(loopIds, block, exits, dominance, instructionOwners)) continue

                    iterator.remove()
                    preheader.instructions.add(preheader.insertionIndexBeforeTerminator(), inst)
                    movedInIteration = true
                    changed = true
                }
            }
        } while (movedInIteration)

        return changed
    }

    private fun buildInstructionOwners(blocks: Collection<BasicBlock>): Map<Instruction, BasicBlock> =
        blocks.flatMap { block -> block.instructions.map { instruction -> instruction to block } }.toMap()

    private fun BasicBlock.insertionIndexBeforeTerminator(): Int {
        val terminator = terminator ?: return instructions.size
        val index = instructions.indexOf(terminator)
        return if (index >= 0) index else instructions.size
    }

    private fun Instruction.isHoistable(
        loopIds: Set<ULong>,
        block: BasicBlock,
        exits: List<BasicBlock>,
        dominance: FunctionDominanceInfo,
        instructionOwners: Map<Instruction, BasicBlock>,
    ): Boolean {
        if (!isPureComputableInstruction()) return false
        if (!exits.all { exit -> dominates(block, exit, dominance) }) return false

        return getOperandsList().all { operand -> operand.isLoopInvariant(loopIds, instructionOwners) }
    }

    private fun Instruction.isPureComputableInstruction(): Boolean =
        when (this) {
            is PhiNode -> false
            is BinaryInst -> true
            is CastInst -> true
            is GetElementPtrInst -> true
            is ICmpInst -> true
            is FCmpInst -> true
            is MemoryInst -> false
            is OtherInst -> isPure()
            else -> false
        }

    private fun Value.isLoopInvariant(
        loopIds: Set<ULong>,
        instructionOwners: Map<Instruction, BasicBlock>,
    ): Boolean {
        val owner = (this as? Instruction)?.let(instructionOwners::get)
        return owner == null || owner.id !in loopIds
    }

    private fun dominates(
        dominator: BasicBlock,
        dominated: BasicBlock,
        dominance: FunctionDominanceInfo,
    ): Boolean = dominance.dominates(dominator.id, dominated.id)

    private data class Loop(
        val header: BasicBlock,
        val blocks: Set<BasicBlock>,
    )
}
