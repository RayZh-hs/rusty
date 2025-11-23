package rusty.semantic.visitors

import com.andreapivetta.kolor.darkGray
import com.andreapivetta.kolor.magenta
import com.andreapivetta.kolor.yellow
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
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticValue
import rusty.semantic.visitors.bases.SimpleVisitorBase
import rusty.semantic.visitors.companions.ScopedVariableMaintainerCompanion
import rusty.semantic.visitors.companions.SelfResolverCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion
import rusty.semantic.visitors.utils.ExpressionAnalyzer
import rusty.semantic.visitors.utils.IntegerBoundGuard
import rusty.semantic.visitors.utils.ProgressiveTypeInferrer
import rusty.semantic.visitors.utils.ProgressiveTypeInferrer.Companion.inferCommonType
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern
import rusty.semantic.visitors.utils.getIdentifierFromType
import rusty.semantic.visitors.utils.isClassMethod
import rusty.semantic.visitors.utils.sequentialLookup
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Stack
import kotlin.sequences.generateSequence

class FunctionTracerVisitor(ctx: SemanticContext): SimpleVisitorBase(ctx) {
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
            val identifier = getIdentifierFromType(ctx, node.typeNode)
            val (symbol, _) = sequentialLookup(identifier, currentScope(), {it.typeST})
                ?: throw CompileError("Cannot find symbol for impl: $identifier")
            selfResolver.withinSymbol(symbol) {
                super.visitInherentImplItem(node)
            }
        }
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        scopedVarMaintainer.withNextScope {
            val identifier = getIdentifierFromType(ctx, node.typeNode)
            val (symbol, _) = sequentialLookup(identifier, currentScope(), {it.typeST})
                ?: throw CompileError("Cannot find symbol for impl: $identifier")
            selfResolver.withinSymbol(symbol) {
                super.visitTraitImplItem(node)
            }
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
                enforceIntegerExpectation(node.withBlockExpressionNode, returnType)
            }
        }
    }

    fun resolveLeftValueExpression(node: ExpressionNode, autoDeref: Boolean = false): SemanticType {
        // returns (dereferenced type, has dereferenced)
        fun mutDerefType(type: SemanticType): Pair<SemanticType, Boolean> {
            var currentType = type
            var hasDeref = false
            while (currentType is SemanticType.ReferenceType) {
                if (!currentType.isMutable.get())
                    throw CompileError("Cannot assign to dereferenced immutable reference type: $type")
                        .with(node).at(node.pointer)
                currentType = currentType.type.get()
                hasDeref = true
            }
            return Pair(currentType, hasDeref)
        }

        fun isMutFromSymbol(symbol: SemanticSymbol.Variable): Boolean {
            if (!autoDeref) {
                return symbol.mutable.get()
            }
            var baseType = symbol.type.get()
            if (baseType !is SemanticType.ReferenceType) {
                return symbol.mutable.get()
            }
            // envoke auto-deref on the type
            while (baseType is SemanticType.ReferenceType) {
                if (!baseType.isMutable.get())
                    return false
                baseType = baseType.type.get()
            }
            return true
        }

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
                        if (!isMutFromSymbol(symbol)) {
                            throw CompileError("Cannot assign to immutable variable: ${segment.name}")
                                .with(node).at(node.pointer)
                        }
                        symbol.type.get()
                    }

                    Token.K_SELF -> {
                        val symbol = scopedVarMaintainer.resolveVariable("self")
                            ?: throw CompileError("Unresolved self instance")
                                .with(node).at(node.pointer)
                        if (!symbol.mutable.get() && !autoDeref)
                            throw CompileError("Cannot assign to immutable self instance")
                                .with(node).at(node.pointer)
                        symbol.type.get()
                    }

                    else -> throw CompileError("Unsupported left-value path segment: $segment")
                        .with(node).at(node.pointer)
                }
            }

            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode ->
                resolveFieldAccess(node, {  expr ->
                    resolveLeftValueExpression(expr, autoDeref = true)
                })

            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> {
                node.index.ensureIsIndexInteger("Index expression")
                val baseType = resolveLeftValueExpression(node.base, autoDeref = true)
                val (derefBaseType, isReference) = mutDerefType(baseType)
                when (derefBaseType) {
                    is SemanticType.ArrayType -> {
                        if (!isReference) {
                            // need to check mutability of the array itself
                            resolveLeftValueExpression(node.base, autoDeref = false)
                        }
                        return derefBaseType.elementType.get()
                    }

                    else -> throw CompileError("Type '$baseType' does not support indexing")
                        .with(node).at(node.pointer)
                }
            }

            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode -> {
                val exprType = resolveLeftValueExpression(node.expr, autoDeref = true)
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
        return ctx.expressionTypeMemory.recall(node) { resolveExpressionInternal(node) }
    }
    
    private fun resolveExpressionInternal(node: ExpressionNode): SemanticType {
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
                return ctx.expressionTypeMemory.recall(node.expression) {
                    resolveBlockExpression(node.expression)
                }
            }
            is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode -> {
                funcRepeatResolvers.push(ProgressiveTypeInferrer(SemanticType.WildcardType)).afterWhich {
                    ctx.expressionTypeMemory.recall(node.expression) {
                        resolveBlockExpression(node.expression, ignoreFinalVerdict = true)
                    }
                }
                return funcRepeatResolvers.pop().type.let {
                    // wildcard type means no breaks, so the loop will never break out
                    if (it == SemanticType.WildcardType) SemanticType.NeverType else it
                }
            }
            is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode -> {
                node.condition.expression.ensureIsOfType(SemanticType.BoolType, "While loop condition")
                funcRepeatResolvers.push(ProgressiveTypeInferrer(SemanticType.WildcardType)).afterWhich {
                    ctx.expressionTypeMemory.recall(node.expression) {
                        resolveBlockExpression(node.expression, ignoreFinalVerdict = true)
                    }
                }
                return funcRepeatResolvers.pop().type.let {
                    // same here, wildcard type means no breaks
                    if (it == SemanticType.WildcardType) SemanticType.NeverType else it
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
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode -> {
                        if (!IntegerBoundGuard.checkSigned(node.value))
                            throw CompileError("Integer literal ${node.value} is out of bounds for i32")
                                .with(node).at(node.pointer)
                        SemanticType.I32Type
                    }
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode -> {
                        if (!IntegerBoundGuard.checkUnsigned(node.value))
                            throw CompileError("Integer literal ${node.value} is out of bounds for u32")
                                .with(node).at(node.pointer)
                        SemanticType.U32Type
                    }
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode -> {
                        if (!IntegerBoundGuard.checkSigned(node.value))
                            throw CompileError("Integer literal ${node.value} is out of bounds for isize")
                                .with(node).at(node.pointer)
                        SemanticType.ISizeType
                    }
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode -> {
                        if (!IntegerBoundGuard.checkUnsigned(node.value))
                            throw CompileError("Integer literal ${node.value} is out of bounds for usize")
                                .with(node).at(node.pointer)
                        SemanticType.USizeType
                    }
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode -> {
                        if (!IntegerBoundGuard.checkAny(node.value))
                            throw CompileError("Integer literal ${node.value} is out of bounds for any supported integer type")
                                .with(node).at(node.pointer)
                        if (!IntegerBoundGuard.checkSigned(node.value)) // -MAX should have been handled in intersectUnaryMinus
                            SemanticType.AnyUnsignedIntType
                        else
                            SemanticType.AnyIntType
                    }
                    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode -> {
                        if (!IntegerBoundGuard.checkSigned(node.value))
                            throw CompileError("Integer literal ${node.value} is out of bounds for any supported signed integer type")
                                .with(node).at(node.pointer)
                        SemanticType.AnySignedIntType
                    }
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
                node.index.ensureIsIndexInteger("Index expression")
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
                val originalLength = node.elements.size.toULong()
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
                if (node.expr != null) {
                    enforceIntegerExpectation(node.expr, shouldRet.type)
                }
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
                if (node.expr != null) {
                    enforceIntegerExpectation(node.expr, shouldRet.type)
                }
//                return if (node.expr == null) {
//                    SemanticType.NeverType
//                } else {
//                    resolveExpression(node.expr)
//                }
                return SemanticType.NeverType
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
                    enforceIntegerExpectation(node.right, leftHandle)
                    // All assignment variants return unit
                    return SemanticType.UnitType
                } else {
                    val leftType = resolveExpression(node.left)
                    val resultType = runCatching {
                        ExpressionAnalyzer.tryBinaryOperate(leftType, rightType, node.op)
                    }.getOrElse {
                        throw CompileError("Cannot apply operator '${node.op}' to types '$leftType' and '$rightType'")
                            .with(node).at(node.pointer).with(it)
                    }
                    propagateIntegerOperandsFromPeers(node.left, leftType, node.right, rightType, node.op)
                    return resultType
                }
            }
            is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode -> {
                val intersection = IntegerBoundGuard.intersectUnaryMinus(node)
                if (intersection != null) {
                    return intersection
                }
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
                throw CompileError("In expressions, `_` can only be used on the left-hand side of an assignment")
//                return SemanticType.WildcardType
            }
            is ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode,
            is ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode
                -> throw CompileError("Tuples have been removed from the language")
                .with(node).at(node.pointer)
        }
    }

    // ignoreFinalVerdict is used for loops, where breaks contribute to the block type
    fun resolveBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode, ignoreFinalVerdict: Boolean = false): SemanticType {
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
                        var bindingType = expectedType
                        if (stmt.expressionNode != null) {
                            val exprType = resolveExpression(stmt.expressionNode)
                            try {
                                val castResult = ExpressionAnalyzer.tryImplicitCast(exprType, expectedType)
                                if (expectedType == SemanticType.WildcardType) {
                                    bindingType = castResult
                                }
                            } catch (e: CompileError) {
                                throw CompileError("Let binding expression type $exprType does not match expected type $expectedType")
                                    .with(stmt.expressionNode).at(stmt.expressionNode.pointer).with(e)
                            }
                            enforceIntegerExpectation(stmt.expressionNode, bindingType)
                        }
                        val symbols = extractSymbolsFromTypedPattern(
                            stmt.patternNode, bindingType, currentScope())
                        for (sym in symbols) {
                            scopedVarMaintainer.declare(sym)
                            println("[${scopedVarMaintainer.currentScope().toShortString()}]".magenta() + " Declared symbol ".darkGray() + sym.identifier.yellow() + ": ".darkGray() + sym.type.getOrNull())
                        }
                    }
                    is StatementNode.ItemStatementNode -> visit(stmt.item)
                    is StatementNode.NullStatementNode -> {}
                }
            }
            // handle trailing expression
            when (node.trailingExpression) {
                null -> {
                    fun lastUsefulStatement(stmts: List<StatementNode>): StatementNode? {
                        for (i in stmts.size - 1 downTo 0) {
                            val stmt = stmts[i]
                            if (stmt !is StatementNode.ItemStatementNode)
                                return stmt
                        }
                        return null
                    }

                    fun isReturnStatement(stmt: StatementNode?): Boolean {
                        if (stmt !is StatementNode.ExpressionStatementNode)
                            return false
                        val expr = stmt.expression
                        return expr is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode
                    }

                    fun isExitStatement(stmt: StatementNode?): Boolean {
                        if (stmt !is StatementNode.ExpressionStatementNode)
                            return false
                        val expr = stmt.expression
                        return expr is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode
                                && expr.callee is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode
                                && expr.callee.pathInExpressionNode.path.size == 1
                                && expr.callee.pathInExpressionNode.path[0].token == Token.I_IDENTIFIER
                                && expr.callee.pathInExpressionNode.path[0].name == "exit"
                    }

                    // special case: if the last statement is a return, then the block type is never type
                    if (node.statements.isNotEmpty() && isReturnStatement(lastUsefulStatement(node.statements)))
                        SemanticType.NeverType
                    else if (node.statements.isNotEmpty() && isExitStatement(lastUsefulStatement(node.statements)))
                        SemanticType.ExitType
                    else
                        if (ignoreFinalVerdict) SemanticType.WildcardType
                        else SemanticType.UnitType
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
                            var result = resolveLeftValueExpression(node.base, autoDeref = true)
                            // the base should, if it is a reference, be a mutable one
                            while (result is SemanticType.ReferenceType) {
                                if (!result.isMutable.get())
                                    throw CompileError("Method '${funcSymbol.identifier}' cannot be called on non-mut reference base to type: $baseType")
                                        .at(node.pointer)
                                result = result.type.get()
                            }
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
                                val func = generateSequence(currentScope()) { it.parent }
                                    .mapNotNull { scopePointer ->
                                        val symbol = scopePointer.functionST.symbols[segment.name]
                                        symbol as? SemanticSymbol.Function
                                    }
                                    .firstOrNull { it.selfParam.getOrNull() == null }
                                if (func != null) return func.getFunctionHeader()
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
                        // Prefer the semantic self type when available (e.g., within impl/trait methods)
                        val selfType = selfResolver.getSelfType()
                        if (selfType != null) return selfType

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
                    Token.K_TYPE_SELF -> selfResolver.getSelf()
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
            enforceIntegerExpectation(node.arguments[i], expectedType)
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
        enforceIntegerExpectation(this, expected)
    }

    private fun ExpressionNode.ensureIsIndexInteger(name: String) {
        val exprType = resolveExpression(this)
        val isInteger = exprType.isConcreteInteger() || exprType.isAnyIntFamily()
        if (!isInteger) {
            throw CompileError("$name must be an integer type, got $exprType")
                .with(this).at(this.pointer)
        }
        enforceIntegerExpectation(this, exprType)
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
            enforceIntegerExpectation(node, expected)
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

    private fun enforceIntegerExpectation(node: ExpressionNode?, expectedType: SemanticType) {
        if (node == null) return
        val targetType = deriveConcreteIntegerTarget(expectedType) ?: return
        val visited = identityExpressionSet()
        val guards = identityExpressionSet()
        propagateIntegerConstraint(node, targetType, visited, guards)
        if (guards.isEmpty()) return

        guards.forEach { guard ->
            val guardType = resolveExpression(guard)
            val fallback = guardType.defaultConcreteFallback() ?: return@forEach
            warnDefaultInference(guard, guardType, fallback)
            ctx.expressionTypeMemory.overwrite(guard, fallback)
            propagateIntegerConstraint(guard, fallback, identityExpressionSet(), identityExpressionSet())
        }
    }

    private fun deriveConcreteIntegerTarget(type: SemanticType?): SemanticType? {
        return when (type) {
            is SemanticType.I32Type,
            is SemanticType.ISizeType,
            is SemanticType.U32Type,
            is SemanticType.USizeType -> type

            is SemanticType.ReferenceType -> deriveConcreteIntegerTarget(type.type.getOrNull())
            else -> null
        }
    }

    private fun propagateIntegerConstraint(
        node: ExpressionNode,
        targetType: SemanticType,
        visited: MutableSet<ExpressionNode>,
        guardNodes: MutableSet<ExpressionNode>,
    ) {
        if (!visited.add(node)) return
        val currentType = resolveExpression(node)
        val assigned = assignConcreteIntTypeIfNeeded(node, currentType, targetType)
        if (!assigned && currentType.isAnyIntFamily()) {
            guardNodes.add(node)
        }

        when (node) {
            is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode ->
                node.trailingExpression?.let { propagateIntegerConstraint(it, targetType, visited, guardNodes) }

            is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode ->
                propagateIntegerConstraint(node.expression, targetType, visited, guardNodes)

            is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode ->
                propagateIntegerConstraint(node.expression, targetType, visited, guardNodes)

            is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode ->
                propagateIntegerConstraint(node.expression, targetType, visited, guardNodes)

            is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> {
                node.ifs.forEach { propagateIntegerConstraint(it.then, targetType, visited, guardNodes) }
                node.elseBranch?.let { propagateIntegerConstraint(it, targetType, visited, guardNodes) }
            }

            is ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode -> {
                if (!node.op.isAssignmentVariant()) {
                    propagateIntegerConstraint(node.left, targetType, visited, guardNodes)
                    propagateIntegerConstraint(node.right, targetType, visited, guardNodes)
                }
            }

            is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode ->
                propagateIntegerConstraint(node.expr, targetType, visited, guardNodes)

            is ExpressionNode.WithoutBlockExpressionNode.ReferenceExpressionNode ->
                propagateIntegerConstraint(node.expr, targetType, visited, guardNodes)

            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode ->
                propagateIntegerConstraint(node.expr, targetType, visited, guardNodes)

            is ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode ->
                guardNodes.add(node.expr)

            is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode -> {}
            else -> {}
        }
    }

    private fun assignConcreteIntTypeIfNeeded(
        node: ExpressionNode,
        currentType: SemanticType,
        targetType: SemanticType,
    ): Boolean {
        return when (currentType) {
            is SemanticType.AnyIntType -> {
                overwriteExpressionType(node, currentType, targetType); true
            }

            is SemanticType.AnySignedIntType -> {
                if (targetType.isConcreteSignedInteger()) {
                    overwriteExpressionType(node, currentType, targetType); true
                } else {
                    false
                }
            }

            is SemanticType.AnyUnsignedIntType -> {
                if (targetType.isConcreteUnsignedInteger()) {
                    overwriteExpressionType(node, currentType, targetType); true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    private fun overwriteExpressionType(node: ExpressionNode, from: SemanticType, to: SemanticType) {
        ctx.expressionTypeMemory.overwrite(node, to)
        println("[type-infer] ${node.pointer}: $from -> $to")
    }

    private fun warnDefaultInference(node: ExpressionNode, from: SemanticType, fallback: SemanticType) {
        println("[type-infer][warn] ${node.pointer}: unable to infer $from, defaulting to $fallback")
    }

    private fun identityExpressionSet(): MutableSet<ExpressionNode> {
        return Collections.newSetFromMap(IdentityHashMap())
    }

    private fun propagateIntegerOperandsFromPeers(
        leftNode: ExpressionNode,
        leftType: SemanticType,
        rightNode: ExpressionNode,
        rightType: SemanticType,
        op: Token,
    ) {
        if (!op.isIntegerConstraintOperator()) return
        val leftIntegerLike = leftType.isConcreteInteger() || leftType.isAnyIntFamily()
        val rightIntegerLike = rightType.isConcreteInteger() || rightType.isAnyIntFamily()
        if (!leftIntegerLike && !rightIntegerLike) return

        val rightTarget = deriveConcreteIntegerTarget(rightType)
        if (rightTarget != null && leftType.isAnyIntFamily()) {
            enforceIntegerExpectation(leftNode, rightTarget)
        }

        val leftTarget = deriveConcreteIntegerTarget(leftType)
        if (leftTarget != null && rightType.isAnyIntFamily()) {
            enforceIntegerExpectation(rightNode, leftTarget)
        }
    }

    private fun SemanticType.isConcreteSignedInteger() =
        this is SemanticType.I32Type || this is SemanticType.ISizeType

    private fun SemanticType.isConcreteUnsignedInteger() =
        this is SemanticType.U32Type || this is SemanticType.USizeType

    private fun SemanticType.isConcreteInteger() =
        isConcreteSignedInteger() || isConcreteUnsignedInteger()

    private fun SemanticType.isAnyIntFamily() =
        this is SemanticType.AnyIntType || this is SemanticType.AnySignedIntType || this is SemanticType.AnyUnsignedIntType

    private fun SemanticType.defaultConcreteFallback(): SemanticType? = when (this) {
        is SemanticType.AnyUnsignedIntType -> SemanticType.U32Type
        is SemanticType.AnyIntType,
        is SemanticType.AnySignedIntType -> SemanticType.I32Type
        else -> null
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

    private fun Token.isIntegerConstraintOperator() = this in setOf(
        Token.O_PLUS,
        Token.O_MINUS,
        Token.O_STAR,
        Token.O_DIV,
        Token.O_PERCENT,
        Token.O_LANG,
        Token.O_LEQ,
        Token.O_RANG,
        Token.O_GEQ,
        Token.O_DOUBLE_EQ,
        Token.O_NEQ,
        Token.O_AND,
        Token.O_OR,
        Token.O_BIT_XOR,
        Token.O_SLFT,
        Token.O_SRIT,
    )
}
