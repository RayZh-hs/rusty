package rusty.semantic.visitors.companions

import rusty.core.CompileError
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticValueNode
import rusty.semantic.support.Symbol
import rusty.semantic.support.commonKClass
import rusty.semantic.visitors.utils.sequentialLookup

// This companion scans the AST and resolves constants to their values.
// It is called on every const expression by the core ConstValueResolver to resolve constants.
// Later on, it is used to evaluate const expressions at compile time. (lookup and evaluate, which is its default behavior).
class ConstValueResolverCompanion(val ctx: Context) {
    val stepped = mutableSetOf<Symbol.Const>()
    fun withStepTrace(symbol: Symbol.Const, block: () -> SemanticValueNode): SemanticValueNode {
        if (symbol in stepped) {
            throw CompileError("Cyclic constant definition for '${symbol}'").with(ctx)
        }
        stepped.add(symbol)
        val result = block()
        stepped.remove(symbol)
        return result
    }

    fun invalidResolve(): SemanticValueNode {
        throw CompileError("Constant resolution landed in invalid state").with(ctx)
    }

    fun resolveConst(identifier: String, scope: Scope): SemanticValueNode {
        val symAndScope = sequentialLookup(identifier, scope, {it.variableConstantST})
            ?: throw CompileError("Constant '$identifier' not found").with(scope).with(scope)
        when (symAndScope.symbol) {
            is Symbol.Const -> {
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

    fun resolveExpression(node: ExpressionNode, scope: Scope): SemanticValueNode {
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
                if (conditions.commonKClass() != SemanticValueNode.BoolValue::class)
                    throw CompileError("If conditions must be boolean").with(node)
                allValues.commonKClass() ?: throw CompileError("If branches must have the same type").with(node)
                // Evaluate the if expression at compile time
                for (i in conditions.indices) {
                    val cond = conditions[i] as SemanticValueNode.BoolValue
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
                        -> SemanticValueNode.I32Value(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode
                        -> SemanticValueNode.U32Value(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode
                        -> SemanticValueNode.ISizeValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode
                        -> SemanticValueNode.USizeValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode
                        -> SemanticValueNode.AnyIntValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode
                        -> SemanticValueNode.AnySignedIntValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode
                        -> SemanticValueNode.BoolValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode
                        -> SemanticValueNode.CharValue(value = node.value)
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode
                        -> SemanticValueNode.ReferenceValue(SemanticValueNode.StringValue(value = node.value))
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode
                        -> SemanticValueNode.ReferenceValue(SemanticValueNode.CStringValue(value = node.value))
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
                        val findStruct = sequentialLookup(identifier, scope, {it.structEnumST})
                            ?: throw CompileError("Struct/Enum '$identifier' not found for path").with(node)
                        when (findStruct.symbol) {
                            is Symbol.Struct -> {
                                if (!findStruct.symbol.constants.containsKey(key))
                                    throw CompileError("Constant '$key' not found in constant keys for struct $findStruct").with(node)
                                return resolveConst(key, findStruct.scope)
                            }
                            is Symbol.Enum -> {
                                if (!findStruct.symbol.elements.get().contains(key))
                                    throw CompileError("Enum element '$key' not found in enum elements for enum $findStruct").with(node)
//                                return SemanticValueNode.EnumValue(findStruct., key)
                                TODO()
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