package rusty.ir.support.visitors

import rusty.ir.support.FunctionEnvironment
import rusty.ir.support.FunctionPlan
import rusty.ir.support.GeneratedValue
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.ir.support.toStorageIRType
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
import rusty.core.CompilerPointer
import kotlin.sequences.generateSequence

class ExpressionEmitter(
    private val ctx: SemanticContext,
    private val scopeMaintainer: ScopeMaintainerCompanion,
    private val staticResolver: StaticResolverCompanion,
    private val emitBlock: (ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) -> GeneratedValue?,
    private val resolveVariable: (SemanticSymbol.Variable) -> GeneratedValue,
    private val declareVariable: (SemanticSymbol.Variable, Name?) -> space.norb.llvm.core.Value,
    private val emitFunctionReturn: (FunctionPlan, GeneratedValue?) -> Unit,
    private val currentEnv: () -> FunctionEnvironment,
    private val addBlockComment: (CompilerPointer, String) -> Unit,
) {
    private val controlFlowEmitter = ControlFlowEmitter(
        ctx = ctx,
        emitExpr = { emitExpression(it) },
        currentEnv = currentEnv,
        addBlockComment = addBlockComment,
    )

    private fun temp(prefix: String): String = Name.auxTemp(prefix, currentEnv().renamer).identifier

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
            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> emitField(node)
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
                name = Name.auxTempGlobal(if (isCString) "cstr" else "str").identifier,
                initialValue = arr,
                isConstant = true,
                linkage = LinkageType.PRIVATE,
            )
        }
        val elementType = global.elementType
            ?: throw IllegalStateException("Global literal missing element type")
        val builder = currentEnv().builder
        val gep = builder.insertGep(
            elementType,
            global,
            listOf(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType)
            ),
            temp("str.ptr")
        )
        return GeneratedValue(gep, exprType)
    }

    private fun emitPath(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode): GeneratedValue? {
        val path = node.pathInExpressionNode.path
        if (path.isEmpty()) return null
        val seg = path.last()
        if (seg.token == Token.K_SELF) {
            val selfVar = currentEnv().locals.asReversed()
                .flatMap { it.keys }
                .firstOrNull { it.identifier == "self" }
                ?: sequentialLookup("self", scopeMaintainer.currentScope) { it.variableST }?.symbol as? SemanticSymbol.Variable
                ?: throw IllegalStateException("Self not bound in IR generation")
            return resolveVariable(selfVar)
        }
        val identifier = seg.name ?: return null

        val localMatch = currentEnv().locals.asReversed()
            .flatMap { it.keys }
            .firstOrNull { it.identifier == identifier }
        if (localMatch != null) return resolveVariable(localMatch)

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
        throw IllegalStateException("Unresolved path '$identifier' in IR generation")
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
        val storage = env.builder.insertAlloca(structType, temp("struct.$structName"))
        node.fields.forEachIndexed { index, field ->
            symbol.fields[field.identifier]?.get()
                ?: throw IllegalStateException("Unknown field ${field.identifier} on $structName")
            val value = when {
                field.expressionNode != null -> emitExpression(field.expressionNode)
                else -> {
                    val resolved = scopeMaintainer.currentScope.variableST.resolve(field.identifier)
                            as? SemanticSymbol.Variable
                        ?: throw IllegalStateException("Shorthand field ${field.identifier} missing binding")
                    resolveVariable(resolved)
                }
            } ?: throw IllegalStateException("Failed to emit field ${field.identifier} from expr=${field.expressionNode}")
            val gep = env.builder.insertGep(
                structType,
                storage,
                listOf(
                    BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                    BuilderUtils.getIntConstant(index.toLong(), TypeUtils.I32 as IntegerType)
                ),
                temp("$structName.${field.identifier}")
            )
            env.builder.insertStore(value.value, gep)
        }
        val ptrValue = env.builder.insertBitcast(storage, TypeUtils.PTR, temp("$structName.ptr"))
        return GeneratedValue(ptrValue, symbol.definesType)
    }

    private fun emitField(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode): GeneratedValue? {
        var base = emitExpression(node.base) ?: return null
        var baseType = ctx.expressionTypeMemory.recall(node.base) { base.type }

        // Auto-dereference reference layers to reach the underlying struct pointer.
        while (baseType is SemanticType.ReferenceType) {
            val inner = baseType.type.getOrNull() ?: return null
            val loaded = currentEnv().builder.insertLoad(inner.toIRType(), base.value, temp("deref"))
            base = GeneratedValue(loaded, inner)
            baseType = inner
        }

        val structType = baseType as? SemanticType.StructType
            ?: throw IllegalStateException("Field base is not struct for '${node.field}': $baseType")
        val structSymbol = sequentialLookup(structType.identifier, scopeMaintainer.currentScope) { it.typeST }?.symbol
            as? SemanticSymbol.Struct ?: throw IllegalStateException("Struct '${structType.identifier}' not resolved in field access")
        val fieldIndex = structSymbol.fields.keys.toList().indexOf(node.field)
        if (fieldIndex == -1) throw IllegalStateException("Field '${node.field}' not found on struct '${structType.identifier}'")

        val fieldType = structSymbol.fields[node.field]?.get()
            ?: throw IllegalStateException("Field type for '${node.field}' not resolved on ${structType.identifier}")
        val structIrType = IRContext.structTypeLookup[structType.identifier]
            ?: throw IllegalStateException("Struct IR type for '${structType.identifier}' missing")
        val gep = currentEnv().builder.insertGep(
            structIrType,
            base.value,
            listOf(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                BuilderUtils.getIntConstant(fieldIndex.toLong(), TypeUtils.I32 as IntegerType)
            ),
            temp("${structType.identifier}.${node.field}")
        )
        val loaded = currentEnv().builder.insertLoad(fieldType.toIRType(), gep, temp("${structType.identifier}.${node.field}.load"))
        return GeneratedValue(loaded, fieldType)
    }

    private fun emitCall(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode): GeneratedValue? {
        val callee = node.callee

        data class Target(val symbol: SemanticSymbol.Function, val selfArg: space.norb.llvm.core.Value?)

        val target: Target = when (callee) {
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                val name = callee.pathInExpressionNode.path.last().name ?: return null
                val symbol = resolveFunction(name) { it.selfParam.getOrNull() == null } ?: return null
                Target(symbol, null)
            }
            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> {
                val base = emitExpression(callee.base) ?: return null
                val baseType = base.type.unwrapReferences()
                val symbol = resolveFunction(callee.field) { fn ->
                    val selfType = fn.selfParam.getOrNull()?.type?.getOrNull()?.unwrapReferences()
                    selfType != null && selfType == baseType
                } ?: return null
                Target(symbol, base.value)
            }
            else -> return null
        }

        val plan = IRContext.functionPlans[target.symbol] ?: return null
        val fn = IRContext.functionLookup[target.symbol] ?: return null

        val env = currentEnv()
        val userArgs = node.arguments.mapNotNull { emitExpression(it)?.value }
        val callArgs = mutableListOf<space.norb.llvm.core.Value>()

        plan.selfParamIndex?.let { idx ->
            while (callArgs.size < idx) callArgs.add(BuilderUtils.getNullPointer(TypeUtils.PTR))
            val self = target.selfArg ?: BuilderUtils.getNullPointer(TypeUtils.PTR)
            callArgs.add(idx, self)
        }

        var retSlot: space.norb.llvm.core.Value? = null
        if (plan.returnsByPointer) {
            retSlot = env.builder.insertAlloca(plan.returnType.toStorageIRType(), temp("ret.slot"))
            plan.retParamIndex?.let { idx ->
                while (callArgs.size < idx) callArgs.add(BuilderUtils.getNullPointer(TypeUtils.PTR))
                callArgs.add(idx, retSlot)
            }
        }
        callArgs.addAll(userArgs)

        val callInstName = if (plan.returnsByPointer) "" else temp("call")
        val callInst = env.builder.insertCall(fn, callArgs, callInstName)
        return when {
            plan.returnsByPointer && plan.returnType is SemanticType.StructType -> GeneratedValue(retSlot!!, plan.returnType)
            plan.returnsByPointer -> {
                val loaded = env.builder.insertLoad(plan.returnType.toIRType(), retSlot!!, temp("ret.load"))
                GeneratedValue(loaded, plan.returnType)
            }
            plan.returnType is SemanticType.StructType -> {
                val storage = env.builder.insertAlloca(plan.returnType.toStorageIRType(), temp("ret.struct"))
                val structVal = env.builder.insertLoad(plan.returnType.toStorageIRType(), callInst, temp("ret.struct.load"))
                env.builder.insertStore(structVal, storage)
                GeneratedValue(storage, plan.returnType)
            }
            else -> GeneratedValue(callInst, plan.returnType)
        }
    }

    private fun resolveFunction(
        name: String,
        predicate: (SemanticSymbol.Function) -> Boolean
    ): SemanticSymbol.Function? {
        val scoped = generateSequence(scopeMaintainer.currentScope) { it.parent }
            .mapNotNull { scopePointer -> scopePointer.functionST.symbols[name] as? SemanticSymbol.Function }
            .firstOrNull(predicate)
        if (scoped != null) return scoped
        return IRContext.functionPlans.keys.firstOrNull { it.identifier == name && predicate(it) }
    }

    private fun SemanticType.unwrapReferences(): SemanticType {
        var ty: SemanticType = this
        while (ty is SemanticType.ReferenceType) {
            ty = ty.type.get()
        }
        return ty
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
            Token.O_PLUS -> GeneratedValue(env.builder.insertAdd(left.value, right.value, temp("add")), left.type)
            Token.O_MINUS -> GeneratedValue(env.builder.insertSub(left.value, right.value, temp("sub")), left.type)
            Token.O_STAR -> GeneratedValue(env.builder.insertMul(left.value, right.value, temp("mul")), left.type)
            Token.O_DIV -> GeneratedValue(env.builder.insertSDiv(left.value, right.value, temp("div")), left.type)
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
        val cmp = currentEnv().builder.insertICmp(pred, left.value, right.value, temp("cmp"))
        return GeneratedValue(cmp, SemanticType.BoolType)
    }

    private fun emitPrefix(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode): GeneratedValue? {
        val env = currentEnv()
        val expr = emitExpression(node.expr) ?: return null
        return when (node.op) {
            Token.O_MINUS -> {
                val zero = BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType)
                GeneratedValue(env.builder.insertSub(zero, expr.value, temp("neg")), expr.type)
            }
            Token.O_NOT -> GeneratedValue(env.builder.insertNot(expr.value, temp("not")), expr.type)
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
        val loaded = currentEnv().builder.insertLoad(targetType.toIRType(), base.value, temp("deref"))
        return GeneratedValue(loaded, targetType)
    }

    private fun emitCast(node: ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode): GeneratedValue? {
        val expr = emitExpression(node.expr) ?: return null
        val targetType = staticResolver.resolveTypeNode(node.targetType, scopeMaintainer.currentScope)
        val targetIr = targetType.toIRType()
        val sourceIr = expr.type.toIRType()
        if (sourceIr == targetIr) return GeneratedValue(expr.value, targetType)
        val builder = currentEnv().builder
        val casted = when (targetIr) {
            TypeUtils.I1 -> builder.insertTrunc(expr.value, TypeUtils.I1 as IntegerType, temp("trunc"))
            TypeUtils.I8 -> builder.insertTrunc(expr.value, TypeUtils.I8 as IntegerType, temp("trunc"))
            TypeUtils.I32 -> builder.insertSExt(expr.value, TypeUtils.I32 as IntegerType, temp("sext"))
            else -> builder.insertBitcast(expr.value, targetIr, temp("bitcast"))
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
