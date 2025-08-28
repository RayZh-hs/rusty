//package rusty.semantic.visitors.companions
//
//import rusty.core.CompileError
//import rusty.core.utils.Slot
//import rusty.lexer.Token
//import rusty.parser.nodes.ExpressionNode
//import rusty.parser.nodes.TypeNode
//import rusty.semantic.support.Scope
//import rusty.semantic.support.SemanticType
//import rusty.semantic.support.SemanticValue
//import rusty.semantic.visitors.utils.ExpressionAnalyzer
//import rusty.semantic.visitors.utils.sequentialLookup
//
//class TypeResolverCompanion() {
//    val stepped = mutableSetOf<TypeNode>()
//    fun withStepTrace(type: TypeNode, block: () -> Any): Any {
//        if (type in stepped) throw CompileError("Cyclic type dependency detected with type '$type'")
//        stepped.add(type)
//        val result = block()
//        stepped.remove(type)
//        return result
//    }
//
//    fun resolveTypeForLeftValueExpression(node: ExpressionNode, scope: Scope): SemanticType {
//        // Simplified model: Only path, field, and index expressions can be valid lvalues, with the same processing logic as rvalues
//        // ! Addition: _ can be a lvalue
//        // ! TODO: Paths and fields need to be checked for mutability in assignments (eg. enum fields cannot be assigned to)
//        return when (node) {
//            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode,
//            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode,
//            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> resolveTypeForRightValueExpression(node, scope)
//            else -> throw CompileError("Not a valid left-value expression: '$node'")
//        }
//    }
//
//    fun resolveTypeForRightValueExpression(node: ExpressionNode, scope: Scope): SemanticType {
//        return when (node) {
//            is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode -> {
//                if (node.trailingExpression == null || node.statements.isNotEmpty())
//                    throw CompileError("Blocks used as expressions must have a single trailing expression").with(node)
//                resolveTypeForRightValueExpression(node.trailingExpression, scope)
//            }
//            is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode ->
//                throw CompileError("Match expressions have been removed from the spec").with(node)
//            is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode ->
//                resolveTypeForRightValueExpression(node.expression, scope)
//        is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> {
//                if (node.elseBranch == null)
//                    throw CompileError("Runtime if-expressions must have an else branch").with(node)
//                val condTypes = node.ifs.map { resolveTypeForRightValueExpression(it.condition.expression, scope) }
//                if (!condTypes.all { it == SemanticType.BoolType })
//                    throw CompileError("If conditions must be boolean").with(node)
//        val branchTypes = node.ifs.map { resolveTypeForRightValueExpression(it.then, scope) } +
//            resolveTypeForRightValueExpression(node.elseBranch, scope)
//        return unifyTypes(branchTypes) ?: throw CompileError("If branches must resolve to a common type").with(node)
//            }
//            is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode ->
//                throw CompileError("Loop expressions are not supported here").with(node)
//            is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode ->
//                throw CompileError("While expressions are not supported here").with(node)
//
//            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode -> when (node) {
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode -> SemanticType.I32Type
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode -> SemanticType.U32Type
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode -> SemanticType.ISizeType
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode -> SemanticType.USizeType
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode -> SemanticType.AnyIntType
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode -> SemanticType.AnySignedIntType
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode -> SemanticType.BoolType
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode -> SemanticType.CharType
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode ->
//                    SemanticType.ReferenceType(Slot(SemanticType.StringType), Slot(false))
//                is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode ->
//                    SemanticType.ReferenceType(Slot(SemanticType.CStringType), Slot(false))
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode ->
//                throw CompileError("Underscore is not allowed in expressions").with(node)
//
//            is ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode ->
//                throw CompileError("Tuple expressions have been removed").with(node)
//
//            is ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode -> {
//                val elems = node.elements.map { resolveTypeForRightValueExpression(it, scope) }
//                val elemType = unifyTypes(elems) ?: throw CompileError("Array elements must share a common type").with(node)
//                val repeatV = constRes.resolveConstExpression(node.repeat, scope)
//                val repeatU = ExpressionAnalyzer.tryImplicitCast(repeatV, SemanticType.USizeType) as SemanticValue.USizeValue
//                val length = (elems.size.toUInt() * repeatU.value)
//                SemanticType.ArrayType(Slot(elemType), Slot(SemanticValue.USizeValue(length)))
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode -> {
//                val structName = node.pathInExpressionNode.path[0].name
//                    ?: throw CompileError("Invalid token ${node.pathInExpressionNode.path[0].token} in struct expression").with(node)
//                val found = sequentialLookup(structName, scope) { it.typeST }
//                    ?: throw CompileError("Struct '$structName' not found").with(node)
//                val structSym = found.symbol as? rusty.semantic.support.SemanticSymbol.Struct
//                    ?: throw CompileError("Identifier '$structName' is not a struct").with(node)
//
//                // Optional: validate fields' types
//                node.fields.forEach { (fieldName, expr) ->
//                    if (!structSym.fields.containsKey(fieldName))
//                        throw CompileError("Struct '${structSym.identifier}' has no field named '$fieldName'").with(node)
//                    if (expr != null) {
//                        val got = resolveTypeForRightValueExpression(expr, scope)
//                        val want = structSym.fields[fieldName]!!.get()
//                        if (!areTypesCompatible(got, want))
//                            throw CompileError("Type mismatch for field '$fieldName': expected $want, got $got").with(node)
//                    }
//                }
//                structSym.definesType
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> {
//                val baseType = resolveTypeForRightValueExpression(node.base, scope)
//                val arrayType = when (baseType) {
//                    is SemanticType.ArrayType -> baseType
//                    is SemanticType.ReferenceType -> baseType.type.get() as? SemanticType.ArrayType
//                        ?: throw CompileError("Indexing requires an array type, got ${baseType}").with(node)
//                    else -> throw CompileError("Indexing requires an array type, got ${baseType}").with(node)
//                }
//                // Ensure index is usize-typed constant or expression (typing only)
//                val idxType = resolveTypeForRightValueExpression(node.index, scope)
//                if (idxType != SemanticType.USizeType && idxType != SemanticType.AnyIntType)
//                    throw CompileError("Array index must be usize or integer, got $idxType").with(node)
//                arrayType.elementType.get()
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> {
//                val baseType = resolveTypeForRightValueExpression(node.base, scope)
//                val structType = when (baseType) {
//                    is SemanticType.StructType -> baseType
//                    is SemanticType.ReferenceType -> baseType.type.get() as? SemanticType.StructType
//                        ?: throw CompileError("Field access requires a struct type, got ${baseType}").with(node)
//                    else -> throw CompileError("Field access requires a struct type, got ${baseType}").with(node)
//                }
//                val slot = structType.fields[node.field]
//                    ?: throw CompileError("Struct '${structType.identifier}' has no field '${node.field}'").with(node)
//                slot.get()
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
//                val path = node.pathInExpressionNode.path
//                when (path.size) {
//                    0 -> throw CompileError("Empty path").with(node)
//                    1 -> {
//                        val id = path[0].name ?: throw CompileError("Invalid token ${path[0].token} in path").with(node)
//                        // Prefer variables/constants
//                        sequentialLookup(id, scope) { it.variableST }?.let { ss ->
//                            return when (val sym = ss.symbol) {
//                                is rusty.semantic.support.SemanticSymbol.Variable -> sym.type.getOrNull()
//                                    ?: throw CompileError("Variable '$id' has no type assigned yet").with(node)
//                                is rusty.semantic.support.SemanticSymbol.Const ->
//                                    sym.type.getOrNull() ?: valueToType(sym.value.get())
//                                else -> throw CompileError("Identifier '$id' is not a value").with(node)
//                            }
//                        }
//                        // Then functions: path as function value returns its return type upon call only,
//                        // but bare function path as rvalue is not supported here.
//                        sequentialLookup(id, scope) { it.functionST }?.let { ss ->
//                            val f = ss.symbol as rusty.semantic.support.SemanticSymbol.Function
//                            return f.returnType.getOrNull() ?: throw CompileError("Function '$id' has no return type resolved yet").with(node)
//                        }
//                        // Finally, a plain type path isn't a value
//                        throw CompileError("Identifier '$id' not found as value").with(node)
//                    }
//                    2 -> {
//                        val typeName = path[0].name ?: throw CompileError("Invalid token ${path[0].token} in path").with(node)
//                        val key = path[1].name ?: throw CompileError("Invalid token ${path[1].token} in path").with(node)
//                        val typeSym = sequentialLookup(typeName, scope) { it.typeST }
//                            ?: throw CompileError("Type '$typeName' not found").with(node)
//                        return when (val sym = typeSym.symbol) {
//                            is rusty.semantic.support.SemanticSymbol.Struct -> {
//                                // Struct::CONST
//                                if (!sym.constants.containsKey(key))
//                                    throw CompileError("Constant '$key' not found in struct '$typeName'").with(node)
//                                val c = sym.constants[key]!!
//                                c.type.getOrNull() ?: valueToType(c.value.get())
//                            }
//                            is rusty.semantic.support.SemanticSymbol.Enum -> {
//                                // Enum::Variant as value of enum type
//                                if (!(sym.fields?.contains(key) ?: false))
//                                    throw CompileError("Enum variant '$key' not found in enum '$typeName'").with(node)
//                                sym.definesType
//                            }
//                            else -> throw CompileError("'$typeName' is not a struct or enum").with(node)
//                        }
//                    }
//                    else -> throw CompileError("Paths with more than 2 segments are not supported").with(node)
//                }
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode -> {
//                // Only support direct function calls: callee must be a single-segment path
//                val callee = node.callee
//                val funcName = (callee as? ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode)
//                    ?.pathInExpressionNode?.path?.singleOrNull()?.name
//                    ?: throw CompileError("Unsupported callee expression; only simple function paths are supported").with(node)
//                val found = sequentialLookup(funcName, scope) { it.functionST }
//                    ?: throw CompileError("Function '$funcName' not found").with(node)
//                val f = found.symbol as rusty.semantic.support.SemanticSymbol.Function
//                f.returnType.get()
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode -> {
//                val lt = resolveTypeForRightValueExpression(node.left, scope)
//                val rt = resolveTypeForRightValueExpression(node.right, scope)
//                inferBinaryType(lt, rt, node.op, node)
//            }
//            is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode -> {
//                val t = resolveTypeForRightValueExpression(node.expr, scope)
//                inferUnaryType(t, node.op, node)
//            }
//
//            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode ->
//                throw CompileError("Control-flow expressions are not first-class values").with(node)
//            is ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode ->
//                throw CompileError("Tuple types and indexing have been removed").with(node)
//        }
//    }
//
//    fun resolveTypeForPattern(node: ExpressionNode, scope: Scope): SemanticType {
//        throw CompileError("Pattern typing not implemented for ExpressionNode; use dedicated pattern typing").with(node)
//    }
//
//    private fun valueToType(v: SemanticValue): SemanticType = when (v) {
//        is SemanticValue.I32Value -> SemanticType.I32Type
//        is SemanticValue.U32Value -> SemanticType.U32Type
//        is SemanticValue.ISizeValue -> SemanticType.ISizeType
//        is SemanticValue.USizeValue -> SemanticType.USizeType
//        is SemanticValue.AnyIntValue -> SemanticType.AnyIntType
//        is SemanticValue.AnySignedIntValue -> SemanticType.AnySignedIntType
//        is SemanticValue.CharValue -> SemanticType.CharType
//        is SemanticValue.StringValue -> SemanticType.StringType
//        is SemanticValue.CStringValue -> SemanticType.CStringType
//        is SemanticValue.BoolValue -> SemanticType.BoolType
//        is SemanticValue.UnitValue -> SemanticType.UnitType
//        is SemanticValue.ArrayValue -> SemanticType.ArrayType(Slot(v.elementType), Slot(v.repeat))
//        is SemanticValue.StructValue -> v.type
//        is SemanticValue.EnumValue -> v.type
//        is SemanticValue.ReferenceValue -> SemanticType.ReferenceType(Slot(valueToType(v.referenced)), Slot(false))
//    }
//
//    private fun areTypesCompatible(a: SemanticType, b: SemanticType): Boolean {
//        if (a == b) return true
//        // Integer lattices
//        return when {
//            (a is SemanticType.AnyIntType && b is SemanticType.U32Type) -> true
//            (a is SemanticType.AnyIntType && b is SemanticType.I32Type) -> true
//            (a is SemanticType.AnyIntType && b is SemanticType.USizeType) -> true
//            (a is SemanticType.AnyIntType && b is SemanticType.ISizeType) -> true
//            (a is SemanticType.AnySignedIntType && (b is SemanticType.I32Type || b is SemanticType.ISizeType)) -> true
//            else -> false
//        }
//    }
//
//    private fun unifyTypes(types: List<SemanticType>): SemanticType? {
//        if (types.isEmpty()) return null
//        if (types.all { it == types.first() }) return types.first()
//        // Integer unification
//        val allInt = types.all { it is SemanticType.I32Type || it is SemanticType.U32Type || it is SemanticType.ISizeType || it is SemanticType.USizeType || it is SemanticType.AnyIntType || it is SemanticType.AnySignedIntType }
//        if (allInt) {
//            val hasSigned = types.any { it is SemanticType.I32Type || it is SemanticType.ISizeType || it is SemanticType.AnySignedIntType }
//            val hasUnsigned = types.any { it is SemanticType.U32Type || it is SemanticType.USizeType }
//            return when {
//                hasSigned && hasUnsigned -> SemanticType.AnyIntType
//                hasSigned -> SemanticType.AnySignedIntType
//                else -> SemanticType.AnyIntType
//            }
//        }
//        return null
//    }
//
//    private fun inferBinaryType(left: SemanticType, right: SemanticType, op: rusty.lexer.Token, node: ExpressionNode): SemanticType {
//        return when (op) {
//            // arithmetic
//            Token.O_PLUS, Token.O_MINUS, Token.O_STAR, Token.O_DIV, Token.O_PERCENT,
//            Token.O_AND, Token.O_OR, Token.O_BIT_XOR, Token.O_SLFT, Token.O_SRIT -> {
//                if (unifyTypes(listOf(left, right)) == null)
//                    throw CompileError("Operator '$op' requires integer operands; got $left and $right").with(node)
//                // Result type follows integer unification rules, prefer left when concrete and compatible
//                when (val uni = unifyTypes(listOf(left, right))) {
//                    is SemanticType.AnySignedIntType, is SemanticType.AnyIntType -> uni
//                    else -> left
//                }
//            }
//            // comparisons
//            Token.O_DOUBLE_EQ, Token.O_NEQ, Token.O_LANG, Token.O_LEQ, Token.O_RANG, Token.O_GEQ -> {
//                if (unifyTypes(listOf(left, right)) == null)
//                    throw CompileError("Comparison requires comparable operands; got $left and $right").with(node)
//                SemanticType.BoolType
//            }
//            // logical
//            Token.O_DOUBLE_AND, Token.O_DOUBLE_OR -> {
//                if (left != SemanticType.BoolType || right != SemanticType.BoolType)
//                    throw CompileError("Logical operator '$op' requires booleans").with(node)
//                SemanticType.BoolType
//            }
//            else -> throw CompileError("Unsupported binary operator '$op' for typing").with(node)
//        }
//    }
//
//    private fun inferUnaryType(inner: SemanticType, op: Token, node: ExpressionNode): SemanticType {
//        return when (op) {
//            Token.O_PLUS -> {
//                if (unifyTypes(listOf(inner)) == null && inner !is SemanticType.AnyIntType && inner !is SemanticType.AnySignedIntType
//                ) throw CompileError("Unary '+' requires integer operand; got $inner").with(node)
//                inner
//            }
//            Token.O_MINUS -> {
//                when (inner) {
//                    SemanticType.I32Type, SemanticType.ISizeType, SemanticType.AnySignedIntType -> inner
//                    SemanticType.AnyIntType -> SemanticType.AnySignedIntType
//                    else -> throw CompileError("Unary '-' requires signed integer; got $inner").with(node)
//                }
//            }
//            Token.O_NOT -> {
//                when (inner) {
//                    SemanticType.BoolType -> SemanticType.BoolType
//                    SemanticType.I32Type, SemanticType.ISizeType, SemanticType.U32Type, SemanticType.USizeType, SemanticType.AnyIntType, SemanticType.AnySignedIntType -> inner
//                    else -> throw CompileError("Unary '!' requires bool or integer; got $inner").with(node)
//                }
//            }
//            else -> throw CompileError("Unsupported unary operator '$op' for typing").with(node)
//        }
//    }
//}
