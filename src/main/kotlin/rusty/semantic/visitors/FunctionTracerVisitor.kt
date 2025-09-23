package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.core.utils.toSlot
import rusty.lexer.Token
import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.PathInExpressionNode
import rusty.parser.nodes.StatementNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticValue
import rusty.semantic.visitors.bases.SimpleVisitorBase
import rusty.semantic.visitors.companions.ScopedVariableMaintainerCompanion
import rusty.semantic.visitors.companions.SelfResolverCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion
import rusty.semantic.visitors.utils.ExpressionAnalyzer
import rusty.semantic.visitors.utils.ProgressiveTypeInferrer
import rusty.semantic.visitors.utils.ProgressiveTypeInferrer.Companion.inferCommonType
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern
import rusty.semantic.visitors.utils.isClassMethod
import rusty.semantic.visitors.utils.sequentialLookup
import java.util.Stack

class FunctionTracerVisitor(ctx: Context): SimpleVisitorBase(ctx) {
    val selfResolver = SelfResolverCompanion()
    val scopedVarMaintainer = ScopedVariableMaintainerCompanion(ctx)
    val staticResolver = StaticResolverCompanion(ctx, selfResolver)

    val funcReturnResolvers = Stack<ProgressiveTypeInferrer>()
    val funcRepeatResolvers = Stack<ProgressiveTypeInferrer>()

    fun currentScope() = scopedVarMaintainer.currentScope()
    fun resolveType(node: TypeNode): SemanticType = staticResolver.resolveTypeNode(node, currentScope())
    fun resolveConstant(node: ExpressionNode): SemanticValue = staticResolver.resolveConstExpression(node, currentScope())

    override fun visitCrate(node: CrateNode) {
        scopedVarMaintainer.withNextScope {
            super.visitCrate(node)
        }
    }

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        scopedVarMaintainer.withNextScope {
            super.visitInherentImplItem(node)
        }
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        scopedVarMaintainer.withNextScope {
            super.visitTraitImplItem(node)
        }
    }

    override fun visitTraitItem(node: ItemNode.TraitItemNode) {
        scopedVarMaintainer.withNextScope {
            super.visitTraitItem(node)
        }
    }

    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        val funSymbol = currentScope().functionST.resolve(node.identifier) as? SemanticSymbol.Function
            ?: throw CompileError("Unresolved function symbol: ${node.identifier}").with(node).at(node.pointer)
        scopedVarMaintainer.withNextScope {
            // Parameters (and optional self) are already declared with concrete types in phase-4
            if (node.withBlockExpressionNode != null) {
                val returnType = funSymbol.returnType.get()
                funcReturnResolvers.push(ProgressiveTypeInferrer(returnType))
                val blockType = resolveExpression(node.withBlockExpressionNode)
                // match block type to the function return type
                try {
                    ExpressionAnalyzer.tryImplicitCast(blockType, returnType)
                } catch (e: CompileError) {
                    throw CompileError("Function body type $blockType does not match function return type $returnType")
                        .with(node.withBlockExpressionNode)
                        .at(node.withBlockExpressionNode.pointer)
                        .with(e)
                }
            }
        }
    }

    fun resolveLeftValueExpression(node: ExpressionNode, skipIdMut: Boolean = false): SemanticType {
        return when(node) {
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                // lookup where this path comes from
                if (node.pathInExpressionNode.path.size != 1)
                    throw CompileError("Left-value path must be one segment long: $node")
                        .with(node).at(node.pointer)
                val segment = node.pathInExpressionNode.path.first()
                when (segment.token) {
                    Token.I_IDENTIFIER -> {
                        // must be a variable
                        // a const would have been invalidated at declaration
                        val symbol = scopedVarMaintainer.resolveVariable(segment.name!!)
                            ?: throw CompileError("Unresolved variable: ${segment.name}")
                                .with(node).at(node.pointer)
                        val type = symbol.type.get()
                        if (!symbol.mutable.get() && !skipIdMut)
                            throw CompileError("Cannot assign to immutable variable: ${segment.name}")
                                .with(node).at(node.pointer)
                        type
                    }

                    Token.K_SELF -> {
                        val symbol = scopedVarMaintainer.resolveVariable("self")
                            ?: throw CompileError("Unresolved self instance")
                                .with(node).at(node.pointer)
                        if (!symbol.mutable.get())
                            throw CompileError("Cannot assign to immutable self instance")
                                .with(node).at(node.pointer)
                        symbol.type.get()
                    }

                    else -> throw CompileError("Unsupported left-value path segment: $segment")
                        .with(node).at(node.pointer)
                }
            }

            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode ->
                resolveFieldAccess(node, ::resolveLeftValueExpression)

            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> {
                // validate that the index is usize
                val indexType = resolveExpression(node.index)
                try {
                    ExpressionAnalyzer.tryImplicitCast(indexType, SemanticType.USizeType)
                } catch (e: CompileError) {
                    throw e.with(node).at(node.pointer)
                }
                val baseType = resolveLeftValueExpression(node.base, skipIdMut = true)
                when (baseType) {
                    is SemanticType.ArrayType -> {
                        // need to check mutability of the array itself
                        resolveLeftValueExpression(node.base, skipIdMut = false)
                        return baseType.elementType.get()
                    }

                    is SemanticType.ReferenceType -> {
                        // perform auto-dereference
                        var derefBaseType = baseType
                        while (derefBaseType is SemanticType.ReferenceType) {
                            if (!derefBaseType.isMutable.get())
                                throw CompileError("Cannot assign to element of immutable reference type: $baseType")
                                    .with(node).at(node.pointer)
                            derefBaseType = derefBaseType.type.get()
                        }
                        when (derefBaseType) {
                            is SemanticType.ArrayType -> derefBaseType.elementType.get()
                            else -> throw CompileError("Type '$baseType' does not support indexing")
                                .with(node).at(node.pointer)
                        }
                    }

                    else -> throw CompileError("Type '$baseType' does not support indexing")
                        .with(node).at(node.pointer)
                }
            }

            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode -> {
                val exprType = resolveLeftValueExpression(node.expr, skipIdMut = true)
                if (exprType !is SemanticType.ReferenceType)
                    throw CompileError("Cannot dereference non-reference type: $exprType")
                        .with(node).at(node.pointer)
                if (!exprType.isMutable.get())
                    throw CompileError("Cannot assign to dereferenced immutable reference type: $exprType")
                        .with(node).at(node.pointer)
                exprType.type.get()
            }

            else -> throw CompileError("Unsupported left-value expression: $node").with(node).at(node.pointer)
        }
    }

    fun resolveExpression(node: ExpressionNode): SemanticType {
        when (node) {
            is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode -> return resolveBlockExpression(node)
            is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> {
                val conditions = node.ifs.map { it.condition.expression }
                conditions.ensureAllIsOfType(SemanticType.BoolType)
                val inf = ProgressiveTypeInferrer(
                    if (node.elseBranch == null) SemanticType.UnitType else SemanticType.WildcardType
                )
                node.ifs.forEach { branchNode ->
                    val branchType = resolveExpression(branchNode.then)
                    inf.register(branchType)
                }
                node.elseBranch?.let {
                    val branchType = resolveExpression(it)
                    inf.register(branchType)
                }
                return inf.type
            }
            is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode -> {
                return resolveBlockExpression(node.expression)
            }
            is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode -> {
                funcRepeatResolvers.push(ProgressiveTypeInferrer(SemanticType.WildcardType)).afterWhich {
                    resolveBlockExpression(node.expression)
                }
                return funcRepeatResolvers.pop().type.let {
                    if (it == SemanticType.WildcardType) SemanticType.UnitType else it
                }
            }
            is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode -> {
                node.condition.expression.ensureIsOfType(SemanticType.BoolType, "While loop condition")
                funcRepeatResolvers.push(ProgressiveTypeInferrer(SemanticType.WildcardType)).afterWhich {
                    resolveBlockExpression(node.expression)
                }
                return funcRepeatResolvers.pop().type.let {
                    if (it == SemanticType.WildcardType) SemanticType.UnitType else it
                }
            }
            is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode ->
                throw CompileError("Match expressions have been removed from the language")
                    .with(node).at(node.pointer)

            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode -> {
                return when (node) {
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode -> SemanticType.BoolType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode -> SemanticType.CharType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode -> SemanticType.RefStrType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode -> SemanticType.RefCStrType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode -> SemanticType.I32Type
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode -> SemanticType.U32Type
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode -> SemanticType.ISizeType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode -> SemanticType.USizeType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode -> SemanticType.AnyIntType
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode -> SemanticType.AnySignedIntType
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> {
                return resolveFieldAccess(node, ::resolveExpression)
            }
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                return resolvePathAccess(node)
            }
            is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode -> {
                return resolveCallExpression(node)
            }
            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> {
                node.index.ensureIsOfType(SemanticType.USizeType, "Index expression")
                var baseType = resolveExpression(node.base)
                while (baseType is SemanticType.ReferenceType) {
                    baseType = baseType.type.get()
                }
                if (baseType !is SemanticType.ArrayType)
                    throw CompileError("Type '$baseType' does not support indexing")
                        .with(node).at(node.pointer)
                return baseType.elementType.get()
            }
            is ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode -> {
                try {
                    val from = resolveExpression(node.expr)
                    val to = resolveType(node.targetType)
                    return ExpressionAnalyzer.tryExplicitCast(from, to)
                } catch (e: CompileError) {
                    throw CompileError("Invalid type cast").with(node).at(node.pointer).with(e)
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode -> {
                val repeatValue = resolveConstant(node.repeat)
                if (!ExpressionAnalyzer.canImplicitlyCast(repeatValue.type, SemanticType.USizeType))
                    throw CompileError("Array repeat count must be of type usize, got: ${repeatValue.type}")
                        .with(node).at(node.pointer)
                val castRepeatValue = ExpressionAnalyzer.tryImplicitCast(repeatValue, SemanticType.USizeType)
                val originalLength = node.elements.size.toUInt()
                val trueLength = (castRepeatValue as SemanticValue.USizeValue).value * originalLength
                val types = node.elements.map { resolveExpression(it) }
                val inferred = types.inferCommonType(start = SemanticType.WildcardType)
                return SemanticType.ArrayType(
                    elementType = inferred.toSlot(),
                    length = (SemanticValue.USizeValue(trueLength)).toSlot(),
                )
            }
            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode -> {
                val returnType = if (node.expr == null) {
                    SemanticType.UnitType
                } else {
                    resolveExpression(node.expr)
                }
                if (funcReturnResolvers.isEmpty())
                    throw CompileError("Return expression used outside of function")
                        .with(node).at(node.pointer)
                val shouldRet = funcReturnResolvers.peek()
                shouldRet.register(returnType)
                return SemanticType.NeverType
            }
            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode -> {
                if (funcRepeatResolvers.isEmpty())
                    throw CompileError("Break expression used outside of loop")
                        .with(node).at(node.pointer)
                val breakType = if (node.expr == null) {
                    SemanticType.UnitType
                } else {
                    resolveExpression(node.expr)
                }
                val shouldRet = funcRepeatResolvers.peek()
                shouldRet.register(breakType)
                return if (node.expr == null) {
                    SemanticType.NeverType
                } else {
                    resolveExpression(node.expr)
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode -> {
                if (funcRepeatResolvers.isEmpty())
                    throw CompileError("Continue expression used outside of loop")
                        .with(node).at(node.pointer)
                return SemanticType.NeverType
            }
            is ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode -> {
                val rightType = resolveExpression(node.right)
                if (node.op.isAssignmentVariant()) {
                    val leftHandle = resolveLeftValueExpression(node.left)
                    ExpressionAnalyzer.tryImplicitCast(rightType, leftHandle)
                    // All assignment variants return unit
                    return SemanticType.UnitType
                } else {
                    val leftType = resolveExpression(node.left)
                    return runCatching {
                        ExpressionAnalyzer.tryBinaryOperate(leftType, rightType, node.op)
                    }.getOrElse {
                        throw CompileError("Cannot apply operator '${node.op}' to types '$leftType' and '$rightType'")
                            .with(node).at(node.pointer).with(it)
                    }
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode -> {
                val exprType = resolveExpression(node.expr)
                return runCatching {
                    ExpressionAnalyzer.tryUnaryOperate(exprType, node.op)
                }.getOrElse {
                    throw CompileError("Cannot apply operator '${node.op}' to type '$exprType'")
                        .with(node).at(node.pointer).with(it)
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.ReferenceExpressionNode -> {
                val exprType = resolveExpression(node.expr)
                return SemanticType.ReferenceType(
                    type = exprType.toSlot(),
                    isMutable = node.isMut.toSlot(),
                )
            }
            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode -> {
                val exprType = resolveExpression(node.expr)
                if (exprType !is SemanticType.ReferenceType)
                    throw CompileError("Cannot dereference non-reference type: $exprType")
                        .with(node).at(node.pointer)
                return exprType.type.get()
            }
            is ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode -> {
                val structType = resolvePathInExpressionNode(node.pathInExpressionNode)
                if (structType !is SemanticType.StructType)
                    throw CompileError("Type '$structType' is not a struct type")
                        .with(node).at(node.pointer)
                val structSymbol = sequentialLookup(structType.identifier, currentScope(), {it.typeST})?.symbol
                    ?: throw CompileError("Unresolved struct type: ${structType.identifier}")
                        .with(node).at(node.pointer)
                if (structSymbol !is SemanticSymbol.Struct)
                    throw CompileError("Type '$structType' is not a struct type")
                        .with(node).at(node.pointer)
                // check fields
                val providedFields = node.fields.associate { field ->
                    if (field.expressionNode != null)   field.identifier to resolveExpression(field.expressionNode)
                    else runCatching {
                        field.identifier to lookupVarType(field.identifier)
                    }.getOrElse {
                        throw CompileError("Unresolved variable for struct field shorthand: ${field.identifier}")
                            .with(field).at(node.pointer)
                    }
                }
                val expectedFields = structSymbol.fields.mapValues { (_, value) -> value.get() }
                ensureTypeMapsMatch(expectedFields, providedFields, node)
                return structType
            }
            is ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode -> {
                return SemanticType.WildcardType
            }
            is ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode,
            is ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode
                -> throw CompileError("Tuples have been removed from the language")
                .with(node).at(node.pointer)
        }
    }

    fun resolveBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode): SemanticType {
        return scopedVarMaintainer.withNextScope {
            // iterate through all statements
            for (stmt in node.statements) {
                when (stmt) {
                    is StatementNode.ExpressionStatementNode -> resolveExpression(stmt.expression)
                    is StatementNode.LetStatementNode -> {
                        val expectedType = when (stmt.typeNode) {
                            null -> SemanticType.WildcardType
                            else -> resolveType(stmt.typeNode)
                        }
                        if (stmt.expressionNode != null) {
                            val exprType = resolveExpression(stmt.expressionNode)
                            try {
                                ExpressionAnalyzer.tryImplicitCast(exprType, expectedType)
                            } catch (e: CompileError) {
                                throw CompileError("Let binding expression type $exprType does not match expected type $expectedType")
                                    .with(stmt.expressionNode).at(stmt.expressionNode.pointer).with(e)
                            }
                        }
                        val symbols = extractSymbolsFromTypedPattern(
                            stmt.patternNode, expectedType, currentScope())
                        for (sym in symbols) {
                            scopedVarMaintainer.declare(sym)
                        }
                    }
                    is StatementNode.ItemStatementNode -> visit(stmt.item)
                    is StatementNode.NullStatementNode -> {}
                }
            }
            // handle trailing expression
            when (node.trailingExpression) {
                null -> {
                    fun isReturnStatement(stmt: StatementNode): Boolean {
                        if (stmt !is StatementNode.ExpressionStatementNode)
                            return false
                        val expr = stmt.expression
                        return expr is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode
                    }

                    // special case: if the last statement is a return, then the block type is never type
                    if (node.statements.isNotEmpty() && isReturnStatement(node.statements.last()))
                        SemanticType.NeverType
                    else
                        SemanticType.UnitType
                }
                else -> resolveExpression(node.trailingExpression)
            }
        }
    }

    private fun resolveFieldAccess(node: ExpressionNode, recursiveResolveFunc: (ExpressionNode) -> SemanticType): SemanticType {
        if (node !is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode)
            throw IllegalStateException("Expected field access expression, got: $node")
        // For L-value checks: recursiveResolveFunc will handle mutability at the base expression (single struct object or reference)
        val baseType = recursiveResolveFunc(node.base)
        return resolveFieldAccessForType(baseType, node.field, node, recursiveResolveFunc)
    }

    private fun resolveFieldAccessForType(baseType: SemanticType, field: String, node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode, recursiveResolveFunc: (ExpressionNode) -> SemanticType): SemanticType {
        val builtinResult = ExpressionAnalyzer.resolveBuiltinMethod(baseType, field)
        if (builtinResult != null) return builtinResult
        return when (baseType) {
            is SemanticType.StructType -> {
                val symbolAndScope = sequentialLookup(baseType.identifier, currentScope(), {it.typeST})
                    ?: throw CompileError("Unresolved struct type: ${baseType.identifier}")
                        .with(node).at(node.pointer)
                val symbol = symbolAndScope.symbol as SemanticSymbol.Struct
                if (symbol.fields.contains(field)) {
                    symbol.fields[field]!!.get()
                } else if (symbol.functions.contains(field)) {
                    // this should be a function header
                    val funcSymbol = symbol.functions[field]!!
                    if (funcSymbol.isClassMethod())
                        throw CompileError("Cannot call class method '${funcSymbol.identifier}' on instance")
                            .with(node).at(node.pointer)
                    if (funcSymbol.selfParam.get()!!.isMut) {
                        // then base should be mutable, i.e. a left value
                        try {
                            // call the function to check mutability
                            resolveLeftValueExpression(node.base)
                        } catch (e: CompileError) {
                            throw CompileError("Cannot call mutable method '${funcSymbol.identifier}' on immutable instance")
                                .with(node).at(node.pointer).with(e)
                        }
                    }
                    funcSymbol.getFunctionHeader()
                } else {
                    throw CompileError("Unresolved field '${field}' in struct '${baseType.identifier}'")
                        .with(node).at(node.pointer)
                }
            }

            is SemanticType.ReferenceType -> {
                // auto de-reference
                val resolved = resolveFieldAccessForType(baseType.type.get(), field, node, recursiveResolveFunc)
                resolved
            }

            // TODO: add support for builtin function calls
            else -> throw CompileError("Type '${baseType}' does not support field access")
                .with(node).at(node.pointer)
        }
    }

    // Now this is the full-fledged path resolver used in the expression context (add support for handling constants and static functions)
    private fun resolvePathAccess(node: ExpressionNode): SemanticType {
        if (node !is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode)
            throw IllegalStateException("Expected path access expression, got: $node")
        return resolvePathInExpressionNode(node.pathInExpressionNode)
    }

    private fun resolvePathInExpressionNode(node: PathInExpressionNode): SemanticType {
        val path = node.path
        if (path.isEmpty())
            throw CompileError("Empty path expression").with(node).at(node.pointer)

        when (path.size) {
            1 -> {
                val segment = path.first()
                return when (segment.token) {
                    Token.I_IDENTIFIER -> {
                        when (val symbol = scopedVarMaintainer.resolve(segment.name!!)) {
                            is SemanticSymbol.Variable -> symbol.type.get()
                            is SemanticSymbol.Const -> symbol.type.get()
                            else -> {
                                // first try interpreting as function header
                                val func = (sequentialLookup(segment.name, currentScope(), { it.functionST }))
                                if (func != null)
                                    return (func.symbol as SemanticSymbol.Function).getFunctionHeader()
                                // then try interpreting as struct/enum/trait type
                                val typeSym = (sequentialLookup(segment.name, currentScope(), { it.typeST }))
                                    ?: throw CompileError("Unresolved variable, constant, function, or type: ${segment.name}")
                                        .with(node).at(node.pointer)
                                when (val typeSymbol = typeSym.symbol) {
                                    is SemanticSymbol.Struct -> typeSymbol.definesType
                                    is SemanticSymbol.Enum -> typeSymbol.definesType
                                    is SemanticSymbol.Trait -> typeSymbol.definesType
                                    else -> throw CompileError("Symbol is not a variable, constant, function, or type: ${segment.name}")
                                        .with(node).at(node.pointer)
                                }
                            }
                        }
                    }

                    Token.K_SELF -> {
                        val symbol = scopedVarMaintainer.resolveVariable("self")
                            ?: throw CompileError("Unresolved self instance")
                                .with(node).at(node.pointer)
                        symbol.type.get()
                    }

                    else -> throw CompileError("Unsupported path segment: $segment")
                        .with(node).at(node.pointer)
                }
            }

            // enum::(identifier|constant) | (struct|Self|trait)::(static function|constant)
            2 -> {
                val base = path[0]
                val member = path[1]
                val baseSymbol = when (base.token) {
                    Token.I_IDENTIFIER -> sequentialLookup(base.name!!, currentScope(), { it.typeST })?.symbol
                    Token.K_SELF -> selfResolver.getSelf()
                    else -> throw CompileError("Unsupported base path segment: $base").with(node).at(node.pointer)
                } ?: throw CompileError("Cannot find symbol to base path segment: $base").with(node).at(node.pointer)
                val memberName = when(member.token) {
                    Token.I_IDENTIFIER -> member.name!!
                    else -> throw CompileError("Unsupported member path segment: $member").with(node).at(node.pointer)
                }

                when (baseSymbol) {
                    is SemanticSymbol.Enum -> {
                        if (baseSymbol.fields?.contains(memberName) ?: false)
                            return baseSymbol.definesType
                        if (baseSymbol.constants.contains(memberName))
                            return baseSymbol.constants[memberName]!!.type.get()
                        throw CompileError("Unresolved enum member '$memberName' in enum '${baseSymbol.identifier}'")
                    }
                    is SemanticSymbol.Struct, is SemanticSymbol.Trait -> {
                        val commonSymbol = baseSymbol as SemanticSymbol.AssociativeItem
                        if (commonSymbol.functions.contains(memberName)) {
                            val func = commonSymbol.functions[memberName]!!
                            if (!func.isClassMethod()) throw CompileError("Cannot call non-static function '${func.identifier}' in type context")
                            return func.getFunctionHeader()
                        }
                        if (commonSymbol.constants.contains(memberName)) {
                            return commonSymbol.constants[memberName]!!.type.get()
                        }
                        throw CompileError("Unresolved static member '$memberName' in type '${baseSymbol.identifier}'")
                    }

                    else -> throw CompileError("Error in resolved symbol: Expected non-builtin typelike, got: $baseSymbol")
                        .with(node).at(node.pointer)
                }
            }

            else -> throw CompileError("Unresolved path segment: $path").with(node).at(node.pointer)
        }
    }

    private fun resolveCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode): SemanticType {
        val calleeType = resolveExpression(node.callee)
        val argTypes = node.arguments.map { resolveExpression(it) }

        if (calleeType !is SemanticType.FunctionHeader)
            throw CompileError("Callee is not a function, got: $calleeType")
                .with(node).at(node.pointer)
        // check argument types, discarding self param

        if (argTypes.size != calleeType.paramTypes.size)
            throw CompileError("Argument count mismatch: expected ${calleeType.paramTypes.size}, got ${argTypes.size}")
                .with(node).at(node.pointer)
        for (i in argTypes.indices) {
            val expectedType = calleeType.paramTypes[i]
            val actualType = argTypes[i]
            try {
                ExpressionAnalyzer.tryImplicitCast(actualType, expectedType)
            } catch (e: CompileError) {
                throw CompileError("Argument ${i+1} type mismatch: expected $expectedType, got $actualType")
                    .with(node.arguments[i]).at(node.arguments[i].pointer).with(e)
            }
        }

        return calleeType.returnType
    }

    private fun ExpressionNode.ensureIsOfType(expected: SemanticType, name: String = "Expression") {
        val exprType = resolveExpression(this)
        try {
            ExpressionAnalyzer.tryImplicitCast(exprType, expected)
        } catch (e: CompileError) {
            throw CompileError("$name must be of type $expected, got $exprType")
                .with(this).at(this.pointer).with(e)
        }
    }

    private fun List<ExpressionNode>.ensureAllIsOfType(expected: SemanticType) {
        for (node in this) {
            val exprType = resolveExpression(node)
            try {
                ExpressionAnalyzer.tryImplicitCast(exprType, expected)
            } catch (e: CompileError) {
                throw CompileError("Expression must be of type $expected, got $exprType")
                    .with(node).at(node.pointer).with(e)
            }
        }
    }

    @Suppress("unused")
    private fun ensureTypeListsMatch(expected: List<SemanticType>, actual: List<SemanticType>, node: ExpressionNode) {
        if (expected.size != actual.size)
            throw CompileError("Expected ${expected.size} types, got ${actual.size}")
                .with(node).at(node.pointer)
        for (i in expected.indices) {
            val exp = expected[i]
            val act = actual[i]
            try {
                ExpressionAnalyzer.tryImplicitCast(act, exp)
            } catch (e: CompileError) {
                throw CompileError("Type ${i+1} mismatch: expected $exp, got $act")
                    .with(node).at(node.pointer).with(e)
            }
        }
    }

    @Suppress("unused")
    private fun ensureTypeMapsMatch(expected: Map<String, SemanticType>, actual: Map<String, SemanticType>, node: ExpressionNode) {
        if (expected.size != actual.size)
            throw CompileError("Expected ${expected.size} types, got ${actual.size}")
                .with(node).at(node.pointer)
        for (key in expected.keys) {
            if (!actual.containsKey(key))
                throw CompileError("Expected key '$key' not found")
                    .with(node).at(node.pointer)
            val exp = expected[key]!!
            val act = actual[key]!!
            try {
                ExpressionAnalyzer.tryImplicitCast(act, exp)
            } catch (e: CompileError) {
                throw CompileError("Type for key '$key' mismatch: expected $exp, got $act")
                    .with(node).at(node.pointer).with(e)
            }
        }
    }

    private fun lookupVarType(name: String): SemanticType {
        val symbol = scopedVarMaintainer.resolve(name)
            ?: throw CompileError("Unresolved variable: $name")
        return when (symbol) {
            is SemanticSymbol.Variable -> symbol.type.get()
            is SemanticSymbol.Const -> symbol.type.get()

            else -> throw CompileError("Symbol is not a variable or constant: $name")
        }
    }

    private fun SemanticSymbol.Function.getFunctionHeader(): SemanticType.FunctionHeader {
        val selfParamType = this.selfParam.get()?.type?.get()
        val paramTypes = this.funcParams.get().map { it.type.get() }
        val returnType = this.returnType.get()
        return SemanticType.FunctionHeader(this.identifier, selfParamType, paramTypes, returnType)
    }

    private fun Token.isAssignmentVariant() = this in setOf(
        Token.O_EQ,
        Token.O_PLUS_EQ,
        Token.O_MINUS_EQ,
        Token.O_STAR_EQ,
        Token.O_DIV_EQ,
        Token.O_PERCENT_EQ,
        Token.O_AND_EQ,
        Token.O_OR_EQ,
        Token.O_XOR_EQ,
        Token.O_SLFT_EQ,
        Token.O_SRIT_EQ,
    )
}