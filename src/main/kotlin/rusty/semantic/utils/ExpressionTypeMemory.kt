package rusty.semantic.utils

import rusty.parser.nodes.ExpressionNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticType

/**
 * Lightweight wrapper around Context.expressionTypeMemory to provide
 * a clear API for getting/setting cached expression types.
 */
class ExpressionTypeMemory(private val context: Context) {
    private val memory = context.expressionTypeMemory

    /**
     * Retrieve a cached type for [expression], computing and caching it via [computeFunc]
     * when not present.
     */
    fun getOrComputeType(expression: ExpressionNode, computeFunc: (ExpressionNode) -> SemanticType): SemanticType {
        return memory.recall(expression) { computeFunc(expression) }
    }

    /** Forcefully set/overwrite the cached type for [expression]. */
    fun setType(expression: ExpressionNode, type: SemanticType) {
        memory.overwrite(expression, type)
    }

    /** Clear all cached expression types. */
    fun clear() {
        memory.clear()
    }
}