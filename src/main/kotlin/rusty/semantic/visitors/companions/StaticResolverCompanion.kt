package rusty.semantic.visitors.companions

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.parser.nodes.ASTNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.parser.nodes.TypeNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticValue
import rusty.semantic.support.commonKClass
import rusty.semantic.support.commonSemanticType
import rusty.semantic.visitors.utils.ExpressionAnalyzer
import rusty.semantic.visitors.utils.sequentialLookup

class StaticResolverCompanion(val ctx: Context) {
    val stepping = mutableSetOf<ASTNode>()
    private fun <R> withTrace(node: ASTNode?, block: () -> R): R {
        if (node in stepping) {
            throw CompileError("Cyclic definition for '${node}'")
        }
        if (node != null)   stepping.add(node)
        val result = block()
        if (node != null)   stepping.remove(node)
        return result
    }

    fun resolveConstItem(identifier: String, scope: Scope): SemanticValue {
        val symAndScope = sequentialLookup(identifier, scope, {it.variableST})
            ?: throw CompileError("Constant '${identifier}' not found").with(scope).with(scope)
        when (val symbol = symAndScope.symbol) {
            is SemanticSymbol.Const -> {
                if (symbol.value.isReady())
                    return symbol.value.get()
                return withTrace(symbol.definedAt) {
                    val definitionAST = symbol.definedAt as? ItemNode.ConstItemNode
                        ?: throw CompileError("Constant '${symbol}' is not properly defined").with(scope)
                    if (definitionAST.expressionNode == null)
                        throw CompileError("Constant '${symbol}' has no value").with(scope)
                    else {
                        val evaluatedResult = resolveConstExpression(definitionAST.expressionNode, scope)
                        if (!symbol.type.isReady()) {
                            symbol.type.set(resolveTypeNode(definitionAST.typeNode, scope))
                        }
                        val castResult = try {
                            ExpressionAnalyzer.tryImplicitCast(evaluatedResult, symbol.type.get())
                        } catch (e: CompileError) {
                            throw CompileError("Constant '${symbol}' value type mismatch: ${e.message}").with(definitionAST)
                        }
                        symbol.value.set(castResult)
                        castResult
                    }
                }
            }
            else -> throw CompileError("Identifier '${identifier}' is not a constant")
        }
    }

    fun resolveConstExpression(node: ExpressionNode, scope: Scope): SemanticValue {
        return withTrace(node) {
            when (node) {
                is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode -> {
                    // We only handle the case where the block expression contains a single trailing expression
                    if (node.trailingExpression == null || node.statements.isNotEmpty())
                        throw CompileError("Constant expressions must be a single expression").with(node)
                    resolveConstExpression(node.trailingExpression, scope)
                }
                is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode -> {
                    throw CompileError("Match expressions have been removed from the spec").with(node)
                }
                is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode -> {
                    resolveConstExpression(node.expression, scope)
                }
                is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> {
                    if (node.elseBranch == null)
                        throw CompileError("Evaluation ifs must have an else branch").with(node)
                    // Assert that all branches have the same type
                    val conditions = node.ifs.map { resolveConstExpression(it.condition.expression, scope) }
                    val values = node.ifs.map { resolveConstExpression(it.then, scope) }
                    val elseValue = resolveConstExpression(node.elseBranch, scope)
                    val allValues = values + elseValue
                    if (conditions.commonKClass() != SemanticValue.BoolValue::class)
                        throw CompileError("If conditions must be boolean").with(node)
                    allValues.commonKClass() ?: throw CompileError("If branches must have the same type").with(node)
                    // Evaluate the if expression at compile time
                    for (i in conditions.indices) {
                        val cond = conditions[i] as SemanticValue.BoolValue
                        if (cond.value)
                            values[i]
                    }
                    elseValue
                }
                is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode -> {
                    throw CompileError("Loop expressions are not allowed in constant expressions").with(node)
                }
                is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode -> {
                    throw CompileError("While expressions are not allowed in constant expressions").with(node)
                }
                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode -> {
                    when (node) {
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
                            -> SemanticValue.ReferenceValue(SemanticType.RefStrType, SemanticValue.StringValue(value = node.value))
                        is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode
                            -> SemanticValue.ReferenceValue(SemanticType.RefCStrType, SemanticValue.CStringValue(value = node.value))
                    }
                }
                is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                    val path = node.pathInExpressionNode.path
                    when (path.size) {
                        0 -> throw CompileError("Empty constant path").with(node)
                        1 -> {
                            val identifier = path[0].name ?: throw CompileError("Invalid token ${path[0].token} in constant path").with(node)
                            resolveConstItem(identifier, scope)
                        }
                        2 -> {
                            // resolve the first part of the path as a struct/enum
                            val identifier = path[0].name ?: throw CompileError("Invalid token ${path[0].token} in constant path").with(node)
                            val key = path[1].name ?: throw CompileError("Invalid token ${path[1].token} in constant path").with(node)
                            val found = sequentialLookup(identifier, scope, {it.typeST})
                                ?: throw CompileError("Struct/Enum '$identifier' not found for path").with(node)
                            when (found.symbol) {
                                is SemanticSymbol.Struct -> {
                                    if (!(found.symbol.fields.containsKey(key)))
                                        throw CompileError("Constant '$key' not found in constant keys for struct $found").with(node)
                                    resolveConstItem(key, found.scope)
                                }
                                is SemanticSymbol.Enum -> {
                                    if (!(found.symbol.fields?.contains(key) ?: throw CompileError("Enum '${found.symbol}' has undefined elements").with(node)))
                                        throw CompileError("Enum element '$key' not found in enum elements for enum $found").with(node)
                                    SemanticValue.EnumValue(found.symbol.definesType, key)
                                }
                                else -> throw CompileError("Identifier '$identifier' is not a struct or enum for path").with(node)
                            }
                        }
                        else -> throw CompileError("Constant paths with more than 2 segments are not supported").with(node)
                    }
                }
                is ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode -> {
                    throw CompileError("Underscore expressions are not allowed in constant expressions").with(node)
                }
                is ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode -> {
                    val structName = node.pathInExpressionNode.path[0].name
                        ?: throw CompileError("Invalid token ${node.pathInExpressionNode.path[0].token} in struct expression").with(node)
                    val struct = sequentialLookup(structName, scope, {it.typeST})
                        ?: throw CompileError("Struct '${structName}' not found").with(node)
                    when (struct.symbol) {
                        is SemanticSymbol.Struct -> {
                            val structType = struct.symbol.definesType
                            if (node.fields.size != struct.symbol.fields.size)
                                throw CompileError("Struct '${struct.symbol}' expects ${struct.symbol.fields.size} fields, but got ${node.fields.size}").with(node)
                            val fieldValues = mutableMapOf<String, SemanticValue>()
                            for ((fieldName, expr) in node.fields) {
                                if (!struct.symbol.fields.containsKey(fieldName))
                                    throw CompileError("Struct '${struct.symbol}' has no field named '$fieldName'").with(node)
                                if (expr == null)
                                    throw CompileError("Field '$fieldName' in struct '${struct.symbol}' has no value").with(node)
                                val value = resolveConstExpression(expr, scope)
                                fieldValues[fieldName] = value
                            }
                            SemanticValue.StructValue(structType, fieldValues)
                        }
                        else -> throw CompileError("Identifier '$structName' is not a struct").with(node)
                    }
                }
                is ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode -> {
                    val elements = node.elements.map { expr ->
                        resolveConstExpression(expr, scope)
                    }
                    val repeat = resolveConstExpression(node.repeat, scope)
                    val castRepeat = ExpressionAnalyzer.tryImplicitCast(repeat, SemanticType.USizeType) as SemanticValue.USizeValue
                    val elementType = elements.commonSemanticType()
                        ?: throw CompileError("Array elements must have the same type").with(node)
                    val arrayType = SemanticType.ArrayType(Slot(elementType), Slot(castRepeat))
                    SemanticValue.ArrayValue(arrayType, elementType, elements, castRepeat)
                }
                is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode -> {
                    throw CompileError("Function calls are not allowed in constant expressions").with(node)
                }
                is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode -> {
                    throw CompileError("Control flow expressions are not allowed in constant expressions").with(node)
                }
                is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> {
                    when (val base = resolveConstExpression(node.base, scope)) {
                        is SemanticValue.StructValue -> {
                            if (!base.fields.containsKey(node.field))
                                throw CompileError("Struct of type '${base.type}' has no field named '${node.field}'").with(node)
                            base.fields[node.field]!!
                        }

                        else -> throw CompileError("Only struct values can have fields, got '${base::class}'").with(node)
                    }
                }
                is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> {
                    when (val base = resolveConstExpression(node.base, scope)) {
                        is SemanticValue.ArrayValue -> {
                            val index = resolveConstExpression(node.index, scope)
                            val castIndex = ExpressionAnalyzer.tryImplicitCast(index, SemanticType.USizeType) as SemanticValue.USizeValue
                            if (castIndex.value >= base.elements.size.toUInt())
                                throw CompileError("Array index out of bounds: ${castIndex.value} >= ${base.elements.size}").with(node)
                            base.elements[castIndex.value.toInt()]
                        }
                        else -> throw CompileError("Only array values can be indexed, got '${base::class}'").with(node)
                    }
                }
                is ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode -> {
                    val left = resolveConstExpression(node.left, scope)
                    val right = resolveConstExpression(node.right, scope)
                    ExpressionAnalyzer.tryBinaryOperate(left, right, node.op)
                }
                is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode -> {
                    val value = resolveConstExpression(node.expr, scope)
                    ExpressionAnalyzer.tryUnaryOperate(value, node.op)
                }

                else -> throw CompileError("Unsupported expression type '${node::class}' in constant expression").with(node)
            }
        }
    }

    fun resolveTypeNode(node: TypeNode, scope: Scope): SemanticType {
        return when (node) {
            is TypeNode.ArrayType -> {
                val length = resolveConstExpression(node.length, scope)
                val castLength = ExpressionAnalyzer.tryImplicitCast(length, SemanticType.USizeType) as SemanticValue.USizeValue
                SemanticType.ArrayType(
                    elementType = Slot(resolveTypeNode(node.type, scope)),
                    length = Slot(castLength)
                )
            }

            is TypeNode.ReferenceType -> {
                SemanticType.ReferenceType(
                    isMutable = Slot(node.isMut),
                    type = Slot(resolveTypeNode(node.type, scope))
                )
            }

            is TypeNode.InferredType -> throw CompileError("Inferred type has been removed: '$node'")
            is TypeNode.SliceType -> throw CompileError("Slice type has been removed: '$node'")
            is TypeNode.TupleType -> throw CompileError("Tuple type has been removed: '$node'")
            is TypeNode.NeverType -> throw CompileError("Never type has been removed: '$node'")

            is TypeNode.TypePath -> {
                val seg = node.pathSegmentNode
                val name = seg.name
                if (name != null) {
                    val found = sequentialLookup(name, scope) { it.typeST }
                        ?: throw CompileError("Unknown type '$name'").with(node)
                    when (val sym = found.symbol) {
                        is SemanticSymbol.Struct -> sym.definesType
                        is SemanticSymbol.Enum -> sym.definesType
                        is SemanticSymbol.BuiltinType -> {
                            // Check for fundamental types
                            return sym.type
                        }
                        else -> throw CompileError("Identifier '$name' is not a type").with(node)
                    }
                }
                throw CompileError("Unsupported type path segment '${seg.token}'").with(node)
            }
        }
    }

    // For function patterns, we can adopt a vastly simplified approach for pattern matching and refutability checking
    fun resolveFunctionPattern(node: PatternNode, _scope: Scope): SemanticType {
        if (node.patternNodes.size != 1)
            throw CompileError("Function parameter patterns must be a single pattern").with(node)
        return when (val activeNode = node.patternNodes[0]) {
            is SupportingPatternNode.WildcardPatternNode -> SemanticType.WildcardType
            is SupportingPatternNode.IdentifierPatternNode -> SemanticType.WildcardType
            else -> throw CompileError("Unsupported pattern type '${activeNode::class}' in function parameter").with(node)
        }
    }
}