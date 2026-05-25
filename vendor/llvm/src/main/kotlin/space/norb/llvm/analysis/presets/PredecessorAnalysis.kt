package space.norb.llvm.analysis.presets

import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Module

class PredecessorMap(
    delegate: Map<BasicBlockId, List<BasicBlockId>> = emptyMap()
) : Map<BasicBlockId, List<BasicBlockId>> by delegate, AnalysisResult

object PredecessorAnalysis : Analysis<PredecessorMap>() {
    private class MutablePredecessorMap : MutableMap<BasicBlockId, MutableList<BasicBlockId>> by mutableMapOf() {
        fun toImmutable(): PredecessorMap {
            val immutableMap = this.mapValues { it.value.toList() }
            return PredecessorMap(immutableMap)
        }
    }

    override fun compute(
        module: Module,
        am: AnalysisManager
    ): PredecessorMap {
        val result = MutablePredecessorMap()
        for (function in module.functions) {
            for (block in function.basicBlocks) {
                val terminator =
                    block.terminator ?: throw IllegalStateException("Basic block ${block.id} has no terminator")
                for (successor in terminator.getSuccessors()) {
                    result.computeIfAbsent(successor.id) { mutableListOf() }.add(block.id)
                }
            }
        }
        return result.toImmutable()
    }
}