package rusty.ir.support.visitors

import rusty.ir.support.FunctionEnvironment
import rusty.ir.support.FunctionPlan
import rusty.ir.support.GeneratedValue
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticValue
import rusty.semantic.visitors.companions.ScopeMaintainerCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion
import rusty.semantic.visitors.utils.sequentialLookup
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import space.norb.llvm.builder.BuilderUtils
import space.norb.llvm.enums.IcmpPredicate
import space.norb.llvm.enums.LinkageType
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.TypeUtils
import space.norb.llvm.values.constants.ArrayConstant

class ExpressionEmitter(
    private val ctx: SemanticContext,
    private val scopeMaintainer: ScopeMaintainerCompanion,
    private val staticResolver: StaticResolverCompanion,
    private val emitBlock: (ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) -> GeneratedValue?,
    private val resolveVariable: (SemanticSymbol.Variable) -> GeneratedValue,
    private val declareVariable: (SemanticSymbol.Variable, Name?) -> space.norb.llvm.core.Value,
    private val emitFunctionReturn: (FunctionPlan, GeneratedValue?) -> Unit,
    private val currentEnv: () -> FunctionEnvironment,
) {
    private val controlFlowEmitter = ControlFlowEmitter(
        ctx = ctx,
        emitExpr = { emitExpression(it) },
        currentEnv = currentEnv,
    )

    fun emitExpression(node: ExpressionNode): GeneratedValue? {
        if (currentEnv().terminated) return null
        return when (node) {
            is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode -> emitBlock(node)
            is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode -> emitBlock(node.expression)
            is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> controlFlowEmitter.emitIf(node)
            is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode -> controlFlowEmitter.emitLoop(node)
            is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode -> controlFlowEmitter.emitWhile(node)
            is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode -> null

            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode -> emitLiteral(node)
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> emitPath(node)
            is ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode -> emitStructLiteral(node)
            is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode -> emitCall(node)
            is ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode -> emitInfix(node)
            is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode -> emitPrefix(node)
            is ExpressionNode.WithoutBlockExpressionNode.ReferenceExpressionNode -> emitReference(node)
            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode -> emitDereference(node)
            is ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode -> emitCast(node)

            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode -> {
                emitFunctionReturn(currentEnv().plan, node.expr?.let { emitExpression(it) })
                currentEnv().terminated = true
                null
            }
            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode -> {
                controlFlowEmitter.emitBreak(node); null
            }
            is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode -> {
                controlFlowEmitter.emitContinue(); null
            }
            else -> null
        }
    }

    private fun emitLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode): GeneratedValue {
        val exprType = ctx.expressionTypeMemory.recall(node) { SemanticType.UnitType }
        return when (node) {
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value, TypeUtils.I32 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value.toLong(), TypeUtils.I32 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value, TypeUtils.I32 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value.toLong(), TypeUtils.I32 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value, TypeUtils.I32 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value, TypeUtils.I32 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode -> {
                val v = BuilderUtils.getIntConstant(if (node.value) 1 else 0, TypeUtils.I1 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode -> {
                val v = BuilderUtils.getIntConstant(node.value.code.toLong(), TypeUtils.I8 as IntegerType)
                GeneratedValue(v, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode -> {
                emitStringLiteral(node.value, isCString = false, exprType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode -> {
                emitStringLiteral(node.value, isCString = true, exprType)
            }
        }
    }

    private fun emitStringLiteral(raw: String, isCString: Boolean, exprType: SemanticType): GeneratedValue {
        val text = if (raw.endsWith("\u0000")) raw else raw + "\u0000"
        val cache = if (isCString) IRContext.cStringLiteralLookup else IRContext.stringLiteralLookup
        val module = IRContext.module
        val global = cache.getOrPut(text) {
            val ty = ArrayType(text.length, TypeUtils.I8 as IntegerType)
            val arr = ArrayConstant(
                text.map { BuilderUtils.getIntConstant(it.code.toLong(), TypeUtils.I8 as IntegerType) },
                ty
            )
            module.registerGlobalVariable(
                name = Name.auxTemp(if (isCString) "cstr" else "str").identifier,
                initialValue = arr,
                isConstant = true,
                linkage = LinkageType.PRIVATE,
            )
        }
        val builder = currentEnv().builder
        val gep = builder.insertGep(
            global.type,
            global,
            listOf(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType)
            ),
            Name.auxTemp("str.ptr").identifier
        )
        return GeneratedValue(gep, exprType)
    }

    private fun emitPath(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode): GeneratedValue? {
        val path = node.pathInExpressionNode.path
        if (path.size != 1) return null
        val seg = path.first()
        val identifier = seg.name ?: return null

        val resolvedVar = sequentialLookup(identifier, scopeMaintainer.currentScope) { it.variableST }?.symbol
        when (resolvedVar) {
            is SemanticSymbol.Variable -> return resolveVariable(resolvedVar)
            is SemanticSymbol.Const -> {
                val constVal = staticResolver.resolveConstItem(identifier, scopeMaintainer.currentScope)
                return emitSemanticValue(constVal)
            }
            else -> {}
        }
        val resolvedFn = sequentialLookup(identifier, scopeMaintainer.currentScope) { it.functionST }?.symbol
        if (resolvedFn is SemanticSymbol.Function) {
            val fn = IRContext.functionLookup[resolvedFn] ?: return null
            return GeneratedValue(fn, SemanticType.FunctionHeader(identifier, null, emptyList(), resolvedFn.returnType.get()))
        }
        return null
    }

    private fun emitStructLiteral(
        node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode
    ): GeneratedValue {
        val env = currentEnv()
        val structName = node.pathInExpressionNode.path.first().name
            ?: throw IllegalStateException("Unnamed struct literal")
        val symbol = sequentialLookup(structName, scopeMaintainer.currentScope) { it.typeST }?.symbol
            as? SemanticSymbol.Struct
            ?: throw IllegalStateException("Struct '$structName' not found")
        val structType = IRContext.structTypeLookup[structName]
            ?: throw IllegalStateException("Struct type '$structName' not registered")
        val storage = env.builder.insertAlloca(structType, Name.ofStruct(structName).identifier)
        node.fields.forEachIndexed { index, field ->
            val fieldType = symbol.fields[field.identifier]?.get()
                ?: throw IllegalStateException("Unknown field ${field.identifier} on $structName")
            val value = when {
                field.expressionNode != null -> emitExpression(field.expressionNode)
                else -> {
                    val resolved = scopeMaintainer.currentScope.variableST.resolve(field.identifier)
                            as? SemanticSymbol.Variable
                        ?: throw IllegalStateException("Shorthand field ${field.identifier} missing binding")
                    resolveVariable(resolved)
                }
            } ?: throw IllegalStateException("Failed to emit field ${field.identifier}")
            val gep = env.builder.insertGep(
                structType,
                storage,
                listOf(
                    BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                    BuilderUtils.getIntConstant(index.toLong(), TypeUtils.I32 as IntegerType)
                ),
                Name.auxTemp("${structName}.${field.identifier}").identifier
            )
            env.builder.insertStore(value.value, gep)
        }
        val ptrValue = env.builder.insertBitcast(storage, TypeUtils.PTR, Name.auxTemp("${structName}.ptr").identifier)
        return GeneratedValue(ptrValue, symbol.definesType)
    }

    private fun emitCall(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode): GeneratedValue? {
        val callee = node.callee
            val fnSymbol = when (callee) {
                is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                    val name = callee.pathInExpressionNode.path.first().name ?: return null
                    sequentialLookup(name, scopeMaintainer.currentScope) { it.functionST }?.symbol
                }
                else -> null
            } as? SemanticSymbol.Function ?: return null

        val plan = IRContext.functionPlans[fnSymbol] ?: return null
        val fn = IRContext.functionLookup[fnSymbol] ?: return null

        val env = currentEnv()
        val userArgs = node.arguments.mapNotNull { emitExpression(it)?.value }
        val callArgs = mutableListOf<space.norb.llvm.core.Value>()

        plan.selfParamIndex?.let { idx ->
            while (callArgs.size < idx) callArgs.add(BuilderUtils.getNullPointer(TypeUtils.PTR))
            callArgs.add(idx, BuilderUtils.getNullPointer(TypeUtils.PTR))
        }

        var retSlot: space.norb.llvm.core.Value? = null
        if (plan.returnsByPointer) {
            retSlot = env.builder.insertAlloca(plan.returnType.toIRType(), Name.auxReturn().identifier)
            plan.retParamIndex?.let { idx ->
                while (callArgs.size < idx) callArgs.add(BuilderUtils.getNullPointer(TypeUtils.PTR))
                callArgs.add(idx, retSlot)
            }
        }
        callArgs.addAll(userArgs)

        val callInst = env.builder.insertCall(fn, callArgs, Name.auxTemp("call").identifier)
        return if (plan.returnsByPointer) {
            val loaded = env.builder.insertLoad(plan.returnType.toIRType(), retSlot!!, Name.auxTemp("ret.load").identifier)
            GeneratedValue(loaded, plan.returnType)
        } else GeneratedValue(callInst, plan.returnType)
    }

    private fun emitInfix(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode): GeneratedValue? {
        val env = currentEnv()
        val op = node.op
        if (op.isAssignmentVariant()) {
            val target = node.left as? ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode
                ?: return null
            val symbolName = target.pathInExpressionNode.path.first().name ?: return null
            val symbol = sequentialLookup(symbolName, scopeMaintainer.currentScope) { it.variableST }?.symbol
                as? SemanticSymbol.Variable ?: return null
            val lhsSlot = env.locals.asReversed().firstNotNullOfOrNull { it[symbol] }
                ?: return null
            val rhs = emitExpression(node.right) ?: return null
            env.builder.insertStore(rhs.value, lhsSlot)
            return GeneratedValue(rhs.value, SemanticType.UnitType)
        }

        val left = emitExpression(node.left) ?: return null
        val right = emitExpression(node.right) ?: return null
        return when (op) {
            Token.O_PLUS -> GeneratedValue(env.builder.insertAdd(left.value, right.value, Name.auxTemp("add").identifier), left.type)
            Token.O_MINUS -> GeneratedValue(env.builder.insertSub(left.value, right.value, Name.auxTemp("sub").identifier), left.type)
            Token.O_STAR -> GeneratedValue(env.builder.insertMul(left.value, right.value, Name.auxTemp("mul").identifier), left.type)
            Token.O_DIV -> GeneratedValue(env.builder.insertSDiv(left.value, right.value, Name.auxTemp("div").identifier), left.type)
            Token.O_DOUBLE_EQ -> compareInts(IcmpPredicate.EQ, left, right)
            Token.O_NEQ -> compareInts(IcmpPredicate.NE, left, right)
            Token.O_LANG -> compareInts(IcmpPredicate.SLT, left, right)
            Token.O_RANG -> compareInts(IcmpPredicate.SGT, left, right)
            Token.O_LEQ -> compareInts(IcmpPredicate.SLE, left, right)
            Token.O_GEQ -> compareInts(IcmpPredicate.SGE, left, right)
            else -> null
        }
    }

    private fun compareInts(
        pred: IcmpPredicate,
        left: GeneratedValue,
        right: GeneratedValue,
    ): GeneratedValue {
        val cmp = currentEnv().builder.insertICmp(pred, left.value, right.value, Name.auxTemp("cmp").identifier)
        return GeneratedValue(cmp, SemanticType.BoolType)
    }

    private fun emitPrefix(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode): GeneratedValue? {
        val env = currentEnv()
        val expr = emitExpression(node.expr) ?: return null
        return when (node.op) {
            Token.O_MINUS -> {
                val zero = BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType)
                GeneratedValue(env.builder.insertSub(zero, expr.value, Name.auxTemp("neg").identifier), expr.type)
            }
            Token.O_NOT -> GeneratedValue(env.builder.insertNot(expr.value, Name.auxTemp("not").identifier), expr.type)
            else -> expr
        }
    }

    private fun emitReference(node: ExpressionNode.WithoutBlockExpressionNode.ReferenceExpressionNode): GeneratedValue? {
        val base = node.expr as? ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode ?: return null
        val symbolName = base.pathInExpressionNode.path.first().name ?: return null
        val symbol = sequentialLookup(symbolName, scopeMaintainer.currentScope) { it.variableST }?.symbol
                as? SemanticSymbol.Variable ?: return null
        val slot = currentEnv().locals.asReversed().firstNotNullOfOrNull { it[symbol] } ?: return null
        return GeneratedValue(slot, SemanticType.ReferenceType(symbol.type, rusty.core.utils.Slot(node.isMut)))
    }

    private fun emitDereference(node: ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode): GeneratedValue? {
        val base = emitExpression(node.expr) ?: return null
        val targetType = (ctx.expressionTypeMemory.recall(node.expr) { base.type } as? SemanticType.ReferenceType)?.type?.get()
            ?: base.type
        val loaded = currentEnv().builder.insertLoad(targetType.toIRType(), base.value, Name.auxTemp("deref").identifier)
        return GeneratedValue(loaded, targetType)
    }

    private fun emitCast(node: ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode): GeneratedValue? {
        val expr = emitExpression(node.expr) ?: return null
        val targetType = staticResolver.resolveTypeNode(node.targetType, scopeMaintainer.currentScope)
        val targetIr = targetType.toIRType()
        val builder = currentEnv().builder
        val casted = when (targetIr) {
            TypeUtils.I1 -> builder.insertTrunc(expr.value, TypeUtils.I1 as IntegerType, Name.auxTemp("trunc").identifier)
            TypeUtils.I8 -> builder.insertTrunc(expr.value, TypeUtils.I8 as IntegerType, Name.auxTemp("trunc").identifier)
            TypeUtils.I32 -> builder.insertSExt(expr.value, TypeUtils.I32 as IntegerType, Name.auxTemp("sext").identifier)
            else -> builder.insertBitcast(expr.value, targetIr, Name.auxTemp("bitcast").identifier)
        }
        return GeneratedValue(casted, targetType)
    }

    private fun emitSemanticValue(value: SemanticValue): GeneratedValue {
        return when (value) {
            is SemanticValue.I32Value -> GeneratedValue(
                BuilderUtils.getIntConstant(value.value, TypeUtils.I32 as IntegerType),
                SemanticType.I32Type
            )
            is SemanticValue.U32Value -> GeneratedValue(
                BuilderUtils.getIntConstant(value.value.toLong(), TypeUtils.I32 as IntegerType),
                SemanticType.U32Type
            )
            is SemanticValue.ISizeValue -> GeneratedValue(
                BuilderUtils.getIntConstant(value.value, TypeUtils.I32 as IntegerType),
                SemanticType.ISizeType
            )
            is SemanticValue.USizeValue -> GeneratedValue(
                BuilderUtils.getIntConstant(value.value.toLong(), TypeUtils.I32 as IntegerType),
                SemanticType.USizeType
            )
            is SemanticValue.BoolValue -> GeneratedValue(
                BuilderUtils.getIntConstant(if (value.value) 1 else 0, TypeUtils.I1 as IntegerType),
                SemanticType.BoolType
            )
            is SemanticValue.CharValue -> GeneratedValue(
                BuilderUtils.getIntConstant(value.value.code.toLong(), TypeUtils.I8 as IntegerType),
                SemanticType.CharType
            )
            is SemanticValue.StringValue -> emitStringLiteral(value.value, false, SemanticType.StrType)
            is SemanticValue.CStringValue -> emitStringLiteral(value.value, true, SemanticType.CStrType)
            is SemanticValue.UnitValue -> GeneratedValue(
                BuilderUtils.getIntConstant(0, TypeUtils.I8 as IntegerType),
                SemanticType.UnitType
            )
            is SemanticValue.EnumValue -> GeneratedValue(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                value.type
            )
            else -> GeneratedValue(BuilderUtils.getIntConstant(0, TypeUtils.I8 as IntegerType), value.type)
        }
    }

    private fun Token.isAssignmentVariant(): Boolean = this in setOf(
        Token.O_EQ, Token.O_PLUS_EQ, Token.O_MINUS_EQ, Token.O_STAR_EQ, Token.O_DIV_EQ,
        Token.O_PERCENT_EQ, Token.O_AND_EQ, Token.O_OR_EQ, Token.O_XOR_EQ, Token.O_SLFT_EQ, Token.O_SRIT_EQ,
    )
}
