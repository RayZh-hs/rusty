package rusty.opt.passes.utils

import space.norb.llvm.analysis.presets.PredecessorMap
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.BasicBlockId

internal data class NaturalLoop(
    val header: BasicBlock,
    val latch: BasicBlock,
    val preheader: BasicBlock,
    val blocks: Set<BasicBlock>,
)

internal fun findSimpleNaturalLoops(
    function: Function,
    predecessors: PredecessorMap,
    immediateDominators: Map<BasicBlockId, BasicBlockId?>,
): List<NaturalLoop> {
    val blockById = function.basicBlocks.associateBy { it.id }
    val loops = mutableListOf<NaturalLoop>()

    for (latch in function.basicBlocks) {
        for (header in latch.getSuccessors()) {
            if (!dominates(header, latch, immediateDominators)) continue
            val loopBlocks = discoverLoop(header, latch, predecessors, blockById)
            val preheader = findSinglePreheader(header, loopBlocks, predecessors, blockById) ?: continue
            loops.add(NaturalLoop(header, latch, preheader, loopBlocks))
        }
    }

    return loops.distinctBy { loop ->
        loop.header.id to loop.latch.id to loop.blocks.map(BasicBlock::id).sorted()
    }
}

internal fun dominates(
    dominator: BasicBlock,
    dominated: BasicBlock,
    immediateDominators: Map<BasicBlockId, BasicBlockId?>,
): Boolean {
    if (dominator == dominated) return true
    var cursor: BasicBlockId? = dominated.id
    while (cursor != null) {
        cursor = immediateDominators[cursor]
        if (cursor == dominator.id) return true
    }
    return false
}

private fun discoverLoop(
    header: BasicBlock,
    latch: BasicBlock,
    predecessors: PredecessorMap,
    blockById: Map<BasicBlockId, BasicBlock>,
): Set<BasicBlock> {
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

    return loopBlocks
}

private fun findSinglePreheader(
    header: BasicBlock,
    loopBlocks: Set<BasicBlock>,
    predecessors: PredecessorMap,
    blockById: Map<BasicBlockId, BasicBlock>,
): BasicBlock? {
    val loopIds = loopBlocks.mapTo(linkedSetOf(), BasicBlock::id)
    val outsidePreds = predecessors[header.id]
        .orEmpty()
        .filter { it !in loopIds }
        .distinct()
    if (outsidePreds.size != 1) return null

    val preheader = blockById[outsidePreds.single()] ?: return null
    return preheader.takeIf {
        it.terminator?.getSuccessors()?.count { successor -> successor == header } == 1
    }
}
