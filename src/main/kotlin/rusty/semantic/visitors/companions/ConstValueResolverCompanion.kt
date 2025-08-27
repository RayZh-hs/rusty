package rusty.semantic.visitors.companions

import rusty.core.CompileError
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticValue
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.commonKClass
import rusty.semantic.visitors.utils.sequentialLookup

// This companion scans the AST and resolves constants to their values.
// It is called on every const expression by the core ConstValueResolver to resolve constants.
// Later on, it is used to evaluate const expressions at compile time. (lookup and evaluate, which is its default behavior).
class ConstValueResolverCompanion(val ctx: Context) {
    val stepped = mutableSetOf<SemanticSymbol.Const>()
    fun withStepTrace(symbol: SemanticSymbol.Const, block: () -> SemanticValue): SemanticValue {
        if (symbol in stepped) {
            throw CompileError("Cyclic constant definition for '${symbol}'").with(ctx)
        }
        stepped.add(symbol)
        val result = block()
        stepped.remove(symbol)
        return result
    }

    fun invalidResolve(): SemanticValue {
        throw CompileError("Constant resolution landed in invalid state").with(ctx)
    }

    fun resolveConst(identifier: String, scope: Scope): SemanticValue {
        val symAndScope = sequentialLookup(identifier, scope, {it.variableConstantST})
            ?: throw CompileError("Constant '$identifier' not found").with(scope).with(scope)
        when (symAndScope.symbol) {
            is SemanticSymbol.Const -> {
                if (symAndScope.symbol.value.isReady())
                    return symAndScope.symbol.value.get()
                return withStepTrace(symAndScope.symbol) {
                    val definitionAST = symAndScope.symbol.definedAt as? ItemNode.ConstItemNode
                        ?: throw CompileError("Constant '${symAndScope.symbol}' is not properly defined").with(scope)
                    if (definitionAST.expressionNode == null)
                        throw CompileError("Constant '${symAndScope.symbol}' has no value").with(scope)
                    else
                        resolveExpression(definitionAST.expressionNode, symAndScope.scope)
                }
            }
            else -> throw CompileError("Identifier '$identifier' is not a constant").with(ctx)
        }
    }

    fun resolveExpression(node: ExpressionNode, scope: Scope): SemanticValue {
        when (node) {
            is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode -> {
                // We only handle the case where the block expression contains a single trailing expression
                if (node.trailingExpression == null || node.statements.isNotEmpty())
                    throw CompileError("Constant expressions must be a single expression").with(node)
                return resolveExpression(node.trailingExpression, scope)
            }
            is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode -> {
                throw CompileError("Match expressions have been removed from the spec").with(node)
            }
            is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode -> {
                return resolveExpression(node.expression, scope)
            }
            is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> {
                if (node.elseBranch == null)
                    throw CompileError("Evaluation ifs must have an else branch").with(node)
                // Assert that all branches have the same type
                val conditions = node.ifs.map { resolveExpression(it.condition.expression, scope) }
                val values = node.ifs.map { resolveExpression(it.then, scope) }
                val elseValue = resolveExpression(node.elseBranch, scope)
                val allValues = values + elseValue
                if (conditions.commonKClass() != SemanticValue.BoolValue::class)
                    throw CompileError("If conditions must be boolean").with(node)
                allValues.commonKClass() ?: throw CompileError("If branches must have the same type").with(node)
                // Evaluate the if expression at compile time
                for (i in conditions.indices) {
                    val cond = conditions[i] as SemanticValue.BoolValue
                    if (cond.value)
                        return values[i]
                }
                return elseValue
            }
            is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode -> {
                throw CompileError("Loop expressions are not allowed in constant expressions").with(node)
            }
            is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode -> {
                throw CompileError("While expressions are not allowed in constant expressions").with(node)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode -> {
                return when (node) {
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode
                        -> SemanticValue.I32Value(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode
                        -> SemanticValue.U32Value(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode
                        -> SemanticValue.ISizeValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode
                        -> SemanticValue.USizeValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode
                        -> SemanticValue.AnyIntValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode
                        -> SemanticValue.AnySignedIntValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode
                        -> SemanticValue.BoolValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode
                        -> SemanticValue.CharValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode
                        -> SemanticValue.ReferenceValue(SemanticValue.StringValue(value = node.value))
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode
                        -> SemanticValue.ReferenceValue(SemanticValue.CStringValue(value = node.value))
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                val path = node.pathInExpressionNode.path
                when (path.size) {
                    0 -> throw CompileError("Empty constant path").with(node)
                    1 -> {
                        val identifier = path[0].name ?: throw CompileError("Invalid token ${path[0].token} in constant path").with(node)
                        return resolveConst(identifier, scope)
                    }
                    2 -> {
                        // resolve the first part of the path as a struct/enum
                        val identifier = path[0].name ?: throw CompileError("Invalid token ${path[0].token} in constant path").with(node)
                        val key = path[1].name ?: throw CompileError("Invalid token ${path[1].token} in constant path").with(node)
                        val found = sequentialLookup(identifier, scope, {it.structEnumST})
                            ?: throw CompileError("Struct/Enum '$identifier' not found for path").with(node)
                        when (found.symbol) {
                            is SemanticSymbol.Struct -> {
                                if (!(found.symbol.fields.containsKey(key)))
                                    throw CompileError("Constant '$key' not found in constant keys for struct $found").with(node)
                                return resolveConst(key, found.scope)
                            }
                            is SemanticSymbol.Enum -> {
                                if (!(found.symbol.fields?.contains(key) ?: throw CompileError("Enum '${found.symbol}' has undefined elements").with(node)))
                                    throw CompileError("Enum element '$key' not found in enum elements for enum $found").with(node)
                                return SemanticValue.EnumValue(found.symbol.definesType, key)
                            }
                            else -> throw CompileError("Identifier '$identifier' is not a struct or enum for path").with(node)
                        }
                    }
                }
            }
            else -> TODO()
        }
        TODO()
    }
}