package rusty.ir.support.visitors

import rusty.ir.support.FunctionEnvironment
import rusty.ir.support.FunctionPlan
import rusty.ir.support.FunctionPlanBuilder
import rusty.ir.support.GeneratedValue
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.ir.support.toStorageIRType
import rusty.ir.support.visitors.pattern.ParameterBinder
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.companions.SelfResolverCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern
import space.norb.llvm.builder.BuilderUtils
import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.enums.LinkageType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.TypeUtils
import rusty.core.CompilerPointer

class FunctionBodyGenerator(ctx: SemanticContext) : ScopeAwareVisitorBase(ctx) {
    private val staticResolver = StaticResolverCompanion(ctx, SelfResolverCompanion())
    private val envStack = ArrayDeque<FunctionEnvironment>()

    private val exprEmitter by lazy {
        ExpressionEmitter(
            ctx = ctx,
            scopeMaintainer = scopeMaintainer,
            staticResolver = staticResolver,
            emitBlock = this::emitBlock,
            resolveVariable = this::resolveVariable,
            declareVariable = this::declareVariable,
            emitFunctionReturn = this::emitFunctionReturn,
            currentEnv = this::currentEnv,
            addBlockComment = this::insertBlockComment,
        )
    }

    override fun visitFunctionItem(node: rusty.parser.nodes.ItemNode.FunctionItemNode) {
        val containerScope = currentScope()
        val symbol = containerScope.functionST.resolve(node.identifier) as? SemanticSymbol.Function
        if (symbol == null) {
            scopeMaintainer.skipScope()
            return
        }
        scopeMaintainer.withNextScope { funcScope ->
            val renamer = IRContext.renamerFor(symbol)
            val plan = IRContext.functionPlans[symbol]
                ?: throw IllegalStateException("Function plan missing for ${symbol.identifier}")
            val fn = IRContext.functionLookup[symbol]
                ?: IRContext.module.registerFunction(plan.name.identifier, plan.type, LinkageType.INTERNAL, false).also {
                    IRContext.functionLookup[symbol] = it
                    IRContext.functionNameLookup[symbol] = plan.name
                }

            val body = node.withBlockExpressionNode as? rusty.parser.nodes.ExpressionNode.WithBlockExpressionNode.BlockExpressionNode
                ?: return@withNextScope
            if (fn.basicBlocks.isNotEmpty()) return@withNextScope

            val builder = IRBuilder(IRContext.module)
            val entry = fn.insertBasicBlock(Name.block(renamer).identifier, setAsEntrypoint = true)
            builder.positionAtEnd(entry)
            val returnSlot = if (!plan.returnsByPointer && plan.returnType != SemanticType.UnitType) {
                builder.insertAlloca(plan.returnType.toIRType(), Name.auxTemp("ret", renamer).identifier)
            } else null
            val env = FunctionEnvironment(
                builder = builder,
                plan = plan,
                function = fn,
                scope = funcScope,
                renamer = renamer,
                returnSlot = returnSlot,
            )
            env.locals.addLast(mutableMapOf())
            envStack.addLast(env)
            insertBlockComment(node.pointer, "entry")
            bindParameters(symbol, plan)

            val result = emitBlock(body)
            if (!env.terminated) emitFunctionReturn(plan, result)

            envStack.removeLast()
        }
    }

    private fun currentEnv(): FunctionEnvironment = envStack.last()

    private fun bindParameters(symbol: SemanticSymbol.Function, plan: FunctionPlan) {
        val env = currentEnv()
        val args = env.function.parameters

        plan.selfParamIndex?.let { idx ->
            val arg = args[idx]
            val selfSymbol = env.scope.variableST.resolve("self") as? SemanticSymbol.Variable
                ?: throw IllegalStateException("Self symbol not found for ${symbol.identifier}")
            val slot = declareVariable(selfSymbol, Name.auxTemp("self", env.renamer))
            env.builder.insertStore(arg, slot)
        }

        val userArgs = args.filterIndexed { index, _ ->
            index != plan.selfParamIndex && index != plan.retParamIndex
        }
        val binder = ParameterBinder(env.scope)
        val paramSymbols = binder.orderedSymbols(symbol)
        paramSymbols.forEachIndexed { idx, paramSymbol ->
            val slot = declareVariable(paramSymbol)
            env.builder.insertStore(userArgs[idx], slot)
        }
    }

    private fun declareVariable(symbol: SemanticSymbol.Variable, nameOverride: Name? = null): space.norb.llvm.core.Value {
        val env = currentEnv()
        val storageType = symbol.type.get().toIRType()
        val slot = env.builder.insertAlloca(
            storageType,
            (nameOverride ?: Name.ofVariable(symbol, env.renamer)).identifier
        )
        env.locals.last()[symbol] = slot
        return slot
    }

    private fun resolveVariable(symbol: SemanticSymbol.Variable): GeneratedValue {
        val env = currentEnv()
        val slot = env.locals.asReversed().firstNotNullOfOrNull { it[symbol] }
            ?: throw IllegalStateException("Variable ${symbol.identifier} not bound in IR generation")
        val loaded = env.builder.insertLoad(symbol.type.get().toIRType(), slot, Name.ofVariable(symbol, env.renamer).identifier)
        return GeneratedValue(loaded, symbol.type.get())
    }

    private fun <T> withScope(block: () -> T): T {
        val env = currentEnv()
        return scopeMaintainer.withNextScope {
            env.locals.addLast(mutableMapOf())
            try {
                block()
            } finally {
                env.locals.removeLast()
            }
        }
    }

    private fun emitBlock(
        node: rusty.parser.nodes.ExpressionNode.WithBlockExpressionNode.BlockExpressionNode,
    ): GeneratedValue? {
        return withScope {
            node.statements.forEach { stmt ->
                emitStatement(stmt)
                if (currentEnv().terminated) return@withScope null
            }
            node.trailingExpression?.let { exprEmitter.emitExpression(it) }
        }
    }

    private fun emitStatement(node: rusty.parser.nodes.StatementNode) {
        if (currentEnv().terminated) return
        when (node) {
            is rusty.parser.nodes.StatementNode.NullStatementNode -> Unit
            is rusty.parser.nodes.StatementNode.ExpressionStatementNode -> exprEmitter.emitExpression(node.expression)
            is rusty.parser.nodes.StatementNode.ItemStatementNode -> Unit
            is rusty.parser.nodes.StatementNode.LetStatementNode -> emitLet(node)
        }
    }

    private fun emitLet(node: rusty.parser.nodes.StatementNode.LetStatementNode) {
        val env = currentEnv()
        val expectedType = node.typeNode?.let { staticResolver.resolveTypeNode(it, currentScope()) }
        val names = extractSymbolsFromTypedPattern(
            node.patternNode,
            expectedType ?: SemanticType.WildcardType,
            currentScope()
        ).map { it.identifier }
        val boundSymbols = names.mapNotNull {
            currentScope().variableST.resolve(it) as? SemanticSymbol.Variable
        }
        val value = node.expressionNode?.let { exprEmitter.emitExpression(it) }
        boundSymbols.forEach { sym ->
            val slot = declareVariable(sym)
            insertLetComment(node.pointer, sym.identifier)
            value?.let { env.builder.insertStore(it.value, slot) }
        }
    }

    private fun emitFunctionReturn(plan: FunctionPlan, value: GeneratedValue?) {
        val env = currentEnv()
        when {
            plan.returnsByPointer -> {
                val dest = plan.retParamIndex?.let { env.function.parameters[it] }
                    ?: throw IllegalStateException("Return pointer missing for ${plan.name.identifier}")
                if (value != null) {
                    if (plan.returnType is SemanticType.StructType) {
                        val storageType = plan.returnType.toStorageIRType()
                        val loaded = env.builder.insertLoad(storageType, value.value, Name.auxTemp("ret.copy", env.renamer).identifier)
                        env.builder.insertStore(loaded, dest)
                    } else {
                        env.builder.insertStore(value.value, dest)
                    }
                }
                env.builder.insertRetVoid()
            }
            value != null -> env.builder.insertRet(value.value)
            env.returnSlot != null -> {
                val loaded = env.builder.insertLoad(
                    plan.returnType.toIRType(),
                    env.returnSlot,
                    Name.auxTemp("ret.load", env.renamer).identifier
                )
                env.builder.insertRet(loaded)
            }
            plan.returnType == SemanticType.UnitType -> {
                val zero = BuilderUtils.getIntConstant(0, TypeUtils.I8 as IntegerType)
                env.builder.insertRet(zero)
            }
            else -> {
                val zero = BuilderUtils.createZeroValue(plan.returnType.toIRType())
                env.builder.insertRet(zero)
            }
        }
        env.terminated = true
    }

    private fun insertBlockComment(pointer: CompilerPointer, label: String) {
        currentEnv().builder.insertComment("${formatPointer(pointer)} block ${normalizeBlockLabel(label)}", ";")
    }

    private fun insertLetComment(pointer: CompilerPointer, variableName: String) {
        currentEnv().builder.insertComment("${formatPointer(pointer)} let $variableName", ";")
    }

    private fun normalizeBlockLabel(label: String): String =
        if (label.endsWith("-block")) label.removeSuffix("-block") else label

    private fun formatPointer(pointer: CompilerPointer): String = "[${pointer.line}:${pointer.column}]"
}
