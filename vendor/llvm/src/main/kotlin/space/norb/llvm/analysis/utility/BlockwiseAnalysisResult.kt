package space.norb.llvm.analysis.utility

import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId

abstract class BlockwiseAnalysisResult : AnalysisResult {
    val blocksToUpdate = mutableSetOf<BasicBlockId>()
    abstract fun updateBlock(block: BasicBlock)

    fun scheduleUpdate(blockId: BasicBlockId) {
        blocksToUpdate.add(blockId)
    }

    fun update() {
        for (blockId in blocksToUpdate) {
            val block = BasicBlock.fromId(blockId)
            updateBlock(block!!)
        }
        blocksToUpdate.clear()
    }
}