package rusty.ir.support.visitors

import rusty.ir.support.FunctionEnvironment
import rusty.ir.support.FunctionPlan
import rusty.ir.support.GeneratedValue
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.isUnitDerived
import rusty.ir.support.toIRType
import rusty.ir.support.toStorageIRType
import rusty.ir.support.unwrapReferences
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
            val entryBlock = fn.insertBasicBlock(Name.block(renamer).identifier, setAsEntrypoint = true)
            val bodyBlock = fn.insertBasicBlock(Name.block(renamer).identifier, setAsEntrypoint = false)
            builder.positionAtEnd(bodyBlock)
            val allocaBuilder = IRBuilder(IRContext.module)
            allocaBuilder.positionAtEnd(entryBlock)
            val returnSlot = null
            val env = FunctionEnvironment(
                bodyBuilder = builder,
                allocaBuilder = allocaBuilder,
                allocaEntryBlock = entryBlock,
                bodyEntryBlock = bodyBlock,
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

            if (entryBlock.terminator == null) {
                allocaBuilder.positionAtEnd(entryBlock)
                allocaBuilder.insertBr(bodyBlock)
            }

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
            // Self parameter should never be unit-derived, but check anyway
            if (slot != null) {
                env.bodyBuilder.insertStore(arg, slot)
            }
        }

        val userArgs = args.filterIndexed { index, _ ->
            index != plan.selfParamIndex && index != plan.retParamIndex
        }
        val binder = ParameterBinder(env.scope)
        val paramSymbols = binder.orderedSymbols(symbol)
        paramSymbols.forEachIndexed { idx, paramSymbol ->
            val slot = declareVariable(paramSymbol)
            // (REFACTORED) Unit-derived parameters don't need storage
            if (slot != null) {
                env.bodyBuilder.insertStore(userArgs[idx], slot)
            }
        }
    }

    private fun declareVariable(symbol: SemanticSymbol.Variable, nameOverride: Name? = null): space.norb.llvm.core.Value? {
        val env = currentEnv()
        val symbolType = symbol.type.get()
        
        // (REFACTORED) Unit-derived types should not generate allocations
        if (symbolType.isUnitDerived()) {
            // Don't allocate storage for unit-derived types, but still track in symbol table
            env.locals.last()[symbol] = null
            return null
        }
        
        val storageType = symbolType.toIRType()
        val slot = env.allocaBuilder.insertAlloca(
            storageType,
            (nameOverride ?: Name.ofVariable(symbol, env.renamer)).identifier
        )
        env.locals.last()[symbol] = slot
        return slot
    }

    private fun resolveVariable(symbol: SemanticSymbol.Variable): GeneratedValue? {
        val env = currentEnv()
        val symbolType = symbol.type.get()
        
        // (REFACTORED) Unit-derived types have no value to load
        if (symbolType.isUnitDerived()) {
            return null
        }
        
        val slot = env.findLocalSlot(symbol)
            ?: throw IllegalStateException("Variable ${symbol.identifier} not bound in IR generation")
        val loaded = env.bodyBuilder.insertLoad(symbolType.toIRType(), slot, Name.ofVariable(symbol, env.renamer).identifier)
        return GeneratedValue(loaded, symbolType)
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
            is rusty.parser.nodes.StatementNode.ItemStatementNode -> emitItemStatement(node)
            is rusty.parser.nodes.StatementNode.LetStatementNode -> emitLet(node)
        }
    }

    private fun emitItemStatement(node: rusty.parser.nodes.StatementNode.ItemStatementNode) {
        when (val item = node.item) {
            is rusty.parser.nodes.ItemNode.FunctionItemNode -> {
                // Nested function: generate its body
                visitFunctionItem(item)
            }
            else -> Unit // Other item types (const, struct, etc.) don't generate code here
        }
    }

    private fun emitLet(node: rusty.parser.nodes.StatementNode.LetStatementNode) {
        val env = currentEnv()
        val expectedType = node.typeNode?.let { staticResolver.resolveTypeNode(it, currentScope()) }
        val symbols = extractSymbolsFromTypedPattern(
            node.patternNode,
            expectedType ?: SemanticType.WildcardType,
            currentScope()
        )
        val value = node.expressionNode?.let { exprEmitter.emitExpression(it) }
        val initializerType = value?.type
        if (initializerType != null) {
            symbols.forEach { symbol ->
                when (val current = symbol.type.getOrNull()) {
                    null -> symbol.type.set(initializerType)
                    is SemanticType.ReferenceType -> {
                        val inner = current.type.getOrNull()
                        if (inner == null || inner == SemanticType.WildcardType) {
                            current.type.set(initializerType)
                        }
                    }
                    else -> Unit
                }
            }
        }
        symbols.forEach { sym ->
            val resolvedType = sym.type.getOrNull() ?: initializerType
            
            // (REFACTORED) Unit-derived types don't need storage or initialization
            if (resolvedType?.isUnitDerived() == true) {
                declareVariable(sym)  // Just register in symbol table, no storage
                insertLetComment(node.pointer, sym.identifier)
                return@forEach
            }
            
            val slot = declareVariable(sym)
            insertLetComment(node.pointer, sym.identifier)
            if (slot != null && value != null) {
                val generated = value
                if (resolvedType != null && resolvedType.requiresAggregateValueCopy()) {
                    val storageType = resolvedType.unwrapReferences().toStorageIRType()
                    val storageAlloca = env.allocaBuilder.insertAlloca(
                        storageType,
                        Name.auxTemp("${sym.identifier}.storage", env.renamer).identifier
                    )
                    // Use memcpy for aggregate types to avoid LLVM crashes with large structs
                    exprEmitter.copyAggregate(
                        resolvedType.unwrapReferences(),
                        generated.value,
                        storageAlloca,
                        "${sym.identifier}.copy"
                    )
                    env.bodyBuilder.insertStore(storageAlloca, slot)
                } else {
                    env.bodyBuilder.insertStore(generated.value, slot)
                }
            }
        }
    }

    private fun SemanticType?.requiresAggregateValueCopy(): Boolean {
        val current = this ?: return false
        if (current is SemanticType.ReferenceType) return false
        return when (current.unwrapReferences()) {
            is SemanticType.StructType,
            is SemanticType.ArrayType -> true
            else -> false
        }
    }

    private fun emitFunctionReturn(plan: FunctionPlan, value: GeneratedValue?) {
        val env = currentEnv()
        when {
            plan.returnsByPointer -> {
                val dest = plan.retParamIndex?.let { env.function.parameters[it] }
                    ?: throw IllegalStateException("Return pointer missing for ${plan.name.identifier}")
                if (value != null) {
                    // Use memcpy for aggregate types to avoid LLVM crashes with large structs
                    exprEmitter.copyAggregate(plan.returnType, value.value, dest, "ret")
                } else {
                    // Zero-initialize the destination
                    val storageType = plan.returnType.toStorageIRType()
                    val zeroValue = BuilderUtils.createZeroValue(storageType)
                    env.bodyBuilder.insertStore(zeroValue, dest)
                }
                // (REFACTORED) Functions returning by pointer now return void
                env.bodyBuilder.insertRetVoid()
            }
            value != null -> env.bodyBuilder.insertRet(value.value)
            env.returnSlot != null -> {
                val loaded = env.bodyBuilder.insertLoad(
                    plan.returnType.toIRType(),
                    env.returnSlot,
                    Name.auxTemp("ret.load", env.renamer).identifier
                )
                env.bodyBuilder.insertRet(loaded)
            }
            // (REFACTORED) Unit type now maps to void, use insertRetVoid()
            plan.returnType == SemanticType.UnitType -> {
                env.bodyBuilder.insertRetVoid()
            }
            else -> {
                val zero = BuilderUtils.createZeroValue(plan.returnType.toIRType())
                env.bodyBuilder.insertRet(zero)
            }
        }
        env.terminated = true
    }

    private fun insertBlockComment(pointer: CompilerPointer, label: String) {
        currentEnv().bodyBuilder.insertComment("${formatPointer(pointer)} block ${normalizeBlockLabel(label)}", ";")
    }

    private fun insertLetComment(pointer: CompilerPointer, variableName: String) {
        currentEnv().bodyBuilder.insertComment("${formatPointer(pointer)} let $variableName", ";")
    }

    private fun normalizeBlockLabel(label: String): String =
        if (label.endsWith("-block")) label.removeSuffix("-block") else label

    private fun formatPointer(pointer: CompilerPointer): String = "[${pointer.line}:${pointer.column}]"
}
