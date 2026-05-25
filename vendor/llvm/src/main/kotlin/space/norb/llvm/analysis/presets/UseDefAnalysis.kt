package space.norb.llvm.analysis.presets

import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.structure.Module
import space.norb.llvm.values.constants.ArrayConstant
import space.norb.llvm.values.globals.GlobalVariable

data class UseDefNode(
    val uses: List<User> = emptyList(),
    val defs: List<Value> = emptyList()
)

class UseDefChain(
    private val delegate: Map<Value, UseDefNode> = emptyMap()
) : Map<Value, UseDefNode> by delegate, AnalysisResult {
    fun getUses(value: Value): List<User> = delegate[value]?.uses ?: emptyList()

    fun getDefs(value: Value): List<Value> = delegate[value]?.defs ?: emptyList()

    fun hasUses(value: Value): Boolean = getUses(value).isNotEmpty()

    fun hasDefs(value: Value): Boolean = getDefs(value).isNotEmpty()
}

object UseDefAnalysis : Analysis<UseDefChain>() {
    private class MutableUseDefNode {
        val uses = mutableListOf<User>()
        val defs = mutableListOf<Value>()

        fun toImmutable(): UseDefNode = UseDefNode(uses.toList(), defs.toList())
    }

    private class MutableUseDefChain {
        private val nodes = linkedMapOf<Value, MutableUseDefNode>()

        fun ensure(value: Value): MutableUseDefNode = nodes.getOrPut(value) { MutableUseDefNode() }

        fun addUse(value: Value, user: User) {
            ensure(value).uses.add(user)
        }

        fun addDef(user: Value, value: Value) {
            ensure(user).defs.add(value)
        }

        fun toImmutable(): UseDefChain = UseDefChain(nodes.mapValues { it.value.toImmutable() })
    }

    override fun compute(module: Module, am: AnalysisManager): UseDefChain {
        val result = MutableUseDefChain()
        val visited = mutableSetOf<Value>()

        fun visit(value: Value) {
            result.ensure(value)
            if (!visited.add(value)) return

            when (value) {
                is GlobalVariable -> value.initializer?.let(::visit)
                is ArrayConstant -> value.elements.forEach(::visit)
            }

            if (value is User) {
                for (operand in value.getOperandsList()) {
                    result.addDef(value, operand)
                    result.addUse(operand, value)
                    visit(operand)
                }
            }
        }

        module.globalVariables.forEach(::visit)
        for (function in module.functions) {
            visit(function)
            function.parameters.forEach(::visit)
            for (block in function.basicBlocks) {
                visit(block)
                block.instructions.forEach(::visit)
                block.terminator?.let(::visit)
            }
        }

        return result.toImmutable()
    }
}
