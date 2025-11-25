package rusty.ir.support.visitors

import rusty.ir.support.FunctionEnvironment
import rusty.ir.support.FunctionPlan
import rusty.ir.support.GeneratedValue
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.ir.support.toStorageIRType
import rusty.ir.support.unwrapReferences
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticValue
import rusty.semantic.visitors.companions.ScopeMaintainerCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion
import rusty.semantic.visitors.utils.sequentialLookup
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.PathIndentSegmentNode
import space.norb.llvm.core.Value
import space.norb.llvm.builder.BuilderUtils
import space.norb.llvm.enums.IcmpPredicate
import space.norb.llvm.enums.LinkageType
import space.norb.llvm.structure.Function
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.FunctionType
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
    private val externalFunctions = mutableMapOf<String, Function>()

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
            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> emitIndex(node)
            is ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode -> emitArrayLiteral(node)
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
        val builder = currentEnv().bodyBuilder
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
        val env = currentEnv()
        if (seg.token == Token.K_SELF) {
            val selfVar = env.findLocalSymbol("self")
                ?: sequentialLookup("self", scopeMaintainer.currentScope) { it.variableST }?.symbol as? SemanticSymbol.Variable
                ?: throw IllegalStateException("Self not bound in IR generation")
            return resolveVariable(selfVar)
        }
        val identifier = seg.name ?: return null

        val localMatch = env.findLocalSymbol(identifier)
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
        resolvePathFunctionSymbol(node) { true }?.let { symbol ->
            val fn = IRContext.functionLookup[symbol] ?: return null
            return GeneratedValue(fn, buildFunctionHeader(symbol))
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
        val storage = env.allocaBuilder.insertAlloca(structType, temp("struct.$structName"))
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
            } ?: throw IllegalStateException("Failed to emit field ${field.identifier} from expr=${field.expressionNode}")
            val gep = env.bodyBuilder.insertGep(
                structType,
                storage,
                listOf(
                    BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                    BuilderUtils.getIntConstant(index.toLong(), TypeUtils.I32 as IntegerType)
                ),
                temp("$structName.${field.identifier}")
            )
            storeValueInto(fieldType, value, gep, "$structName.${field.identifier}")
        }
        val ptrValue = env.bodyBuilder.insertBitcast(storage, TypeUtils.PTR, temp("$structName.ptr"))
        return GeneratedValue(ptrValue, symbol.definesType)
    }

    private fun emitField(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode): GeneratedValue? {
        val (ptr, fieldType) = emitFieldPointer(node) ?: return null
        val underlying = fieldType.unwrapReferences()
        val needsLoad = when (underlying) {
            is SemanticType.StructType,
            is SemanticType.ArrayType -> false
            else -> true
        }
        val value = if (needsLoad) {
            currentEnv().bodyBuilder.insertLoad(fieldType.toIRType(), ptr, temp("${node.field}.load"))
        } else {
            ptr
        }
        return GeneratedValue(value, fieldType)
    }

    private fun emitIndex(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode): GeneratedValue? {
        val (ptr, elementType) = emitIndexPointer(node) ?: return null
        val underlying = elementType.unwrapReferences()
        val needsLoad = when (underlying) {
            is SemanticType.StructType,
            is SemanticType.ArrayType -> false
            else -> true
        }
        val value = if (needsLoad) {
            currentEnv().bodyBuilder.insertLoad(elementType.toIRType(), ptr, temp("index.load"))
        } else {
            ptr
        }
        return GeneratedValue(value, elementType)
    }

    private fun emitArrayLiteral(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode): GeneratedValue {
        val arrayType = ctx.expressionTypeMemory.recall(node) {
            throw IllegalStateException("Array literal type missing for $node")
        }
            as? SemanticType.ArrayType
            ?: throw IllegalStateException("Array literal type not resolved for $node")
        val length = arrayType.length.getOrNull()
            ?: throw IllegalStateException("Array length unresolved for $arrayType")
        val elementType = arrayType.elementType.getOrNull()
            ?: throw IllegalStateException("Array element type unresolved for $arrayType")
        val storageType = arrayStorageType(arrayType)

        val env = currentEnv()
        val storage = env.allocaBuilder.insertAlloca(storageType, temp("array.alloc"))
        val elementCount = node.elements.size.takeIf { it > 0 }
            ?: throw IllegalStateException("Array literal missing elements for $arrayType")
        val totalLength = length.value.toLong()
        val repeat = totalLength / elementCount
        require(repeat * elementCount == totalLength) {
            "Array literal length mismatch: elements=$elementCount, length=$totalLength"
        }

        val builder = env.bodyBuilder
        val elementSize = emitTypeSizeBytes(elementType)
        val i32Type = TypeUtils.I32 as IntegerType
        val chunkSize = if (elementCount == 1) {
            elementSize
        } else {
            val elemCountConst = BuilderUtils.getIntConstant(elementCount.toLong(), i32Type)
            builder.insertMul(elementSize, elemCountConst, temp("array.chunk.size"))
        }
        node.elements.forEachIndexed { index, elementExpr ->
            val value = emitExpression(elementExpr)
                ?: throw IllegalStateException("Failed to emit array element at $index for $arrayType")
            storeArrayElement(storageType, storage, index.toLong(), value, elementType)
        }

        val destPtr = builder.insertBitcast(storage, TypeUtils.PTR, temp("array.ptr"))
        if (repeat > 1) {
            // The first chunk already lives in dest; memfill clones it across the tail.
            val zero = BuilderUtils.getIntConstant(0, i32Type)
            val chunkOffset = BuilderUtils.getIntConstant(elementCount.toLong(), i32Type)
            val tailPtrTyped = builder.insertGep(
                storageType,
                storage,
                listOf(zero, chunkOffset),
                temp("array.tail")
            )
            val tailPtr = builder.insertBitcast(tailPtrTyped, TypeUtils.PTR, temp("array.tail.ptr"))
            val repeatValue = BuilderUtils.getIntConstant(repeat - 1, i32Type)
            callMemfill(tailPtr, destPtr, chunkSize, repeatValue)
        }
        return GeneratedValue(destPtr, arrayType)
    }

    private fun emitCall(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode): GeneratedValue? {
        val callee = node.callee

        data class Target(val symbol: SemanticSymbol.Function, val selfArg: space.norb.llvm.core.Value?)

        val target: Target = when (callee) {
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                val symbol = resolvePathFunctionSymbol(callee) { it.selfParam.getOrNull() == null } ?: return null
                Target(symbol, null)
            }
            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> {
                val calleeType = ctx.expressionTypeMemory.recall(callee) { SemanticType.UnitType }
                val baseValue = emitExpression(callee.base) ?: return null
                val builtinHeader = calleeType as? SemanticType.FunctionHeader
                if (builtinHeader != null && builtinHeader.identifier == "to_string") {
                    return emitBuiltinToString(callee.base, baseValue)
                }
                val (selfValue, baseType) = prepareMethodReceiver(callee.base, baseValue)
                val symbol = resolveFunction(callee.field) { fn ->
                    val selfType = fn.selfParam.getOrNull()?.type?.getOrNull()?.unwrapReferences()
                    selfType != null && selfType == baseType
                } ?: return null
                Target(symbol, selfValue.value)
            }
            else -> return null
        }

        val plan = IRContext.functionPlans[target.symbol] ?: return null
        val fn = IRContext.functionLookup[target.symbol] ?: return null

        val env = currentEnv()
        val argumentValues = node.arguments.mapIndexed { index, argument ->
            emitExpression(argument)
                ?: throw IllegalStateException(
                    "Failed to emit argument ${index + 1} for ${plan.name.identifier} " +
                        "at ${node.pointer.line}:${node.pointer.column}"
                )
        }
        val normalizedArgs = prepareCallArguments(argumentValues, target.symbol)
        val callArgs = mutableListOf<Value>()

        plan.selfParamIndex?.let { idx ->
            while (callArgs.size < idx) callArgs.add(BuilderUtils.getNullPointer(TypeUtils.PTR))
            val self = target.selfArg ?: BuilderUtils.getNullPointer(TypeUtils.PTR)
            callArgs.add(idx, self)
        }

        var retSlot: Value? = null
        if (plan.returnsByPointer) {
            retSlot = env.allocaBuilder.insertAlloca(plan.returnType.toStorageIRType(), temp("ret.slot"))
            plan.retParamIndex?.let { idx ->
                while (callArgs.size < idx) callArgs.add(BuilderUtils.getNullPointer(TypeUtils.PTR))
                callArgs.add(idx, retSlot)
            }
        }
        callArgs.addAll(normalizedArgs)

        val expectedArgs = plan.type.paramTypes.size
        require(callArgs.size == expectedArgs) {
            "Call argument mismatch for ${plan.name.identifier} at ${node.pointer.line}:${node.pointer.column}: expected $expectedArgs, got ${callArgs.size}"
        }

        val callInstName = temp(if (plan.returnsByPointer) "call.discard" else "call")
        val callInst = env.bodyBuilder.insertCall(fn, callArgs, callInstName)
        if (plan.returnsByPointer) {
            val slot = retSlot
                ?: throw IllegalStateException("Return slot missing for pointer-returning call ${plan.name.identifier}")
            return GeneratedValue(slot, plan.returnType)
        }
        return GeneratedValue(callInst, plan.returnType)
    }

    private fun prepareCallArguments(
        arguments: List<GeneratedValue>,
        targetSymbol: SemanticSymbol.Function,
    ): List<Value> {
        val paramSymbols = targetSymbol.funcParams.getOrNull()
        if (paramSymbols.isNullOrEmpty()) return arguments.map { it.value }
        return arguments.mapIndexed { index, argument ->
            val paramType = paramSymbols.getOrNull(index)?.type?.getOrNull()
            if (paramType != null && paramType.requiresAggregateStorageCopy()) {
                copyAggregateArgument(argument, paramType, index)
            } else {
                argument.value
            }
        }
    }

    private fun copyAggregateArgument(
        argument: GeneratedValue,
        paramType: SemanticType,
        index: Int,
    ): Value {
        val env = currentEnv()
        val storageType = paramType.unwrapReferences().toStorageIRType()
        val tempAlloca = env.allocaBuilder.insertAlloca(
            storageType,
            temp("arg$index.copy.alloc")
        )
        val copiedValue = env.bodyBuilder.insertLoad(
            storageType,
            argument.value,
            temp("arg$index.copy.load")
        )
        env.bodyBuilder.insertStore(copiedValue, tempAlloca)
        return env.bodyBuilder.insertBitcast(tempAlloca, TypeUtils.PTR, temp("arg$index.copy.ptr"))
    }

    private fun prepareMethodReceiver(
        baseExpr: ExpressionNode,
        initialValue: GeneratedValue
    ): Pair<GeneratedValue, SemanticType> =
        unwrapReferenceValue(baseExpr, initialValue, stopAtPointerTypes = true)

    private fun unwrapReferenceValue(
        baseExpr: ExpressionNode,
        initialValue: GeneratedValue,
        stopAtPointerTypes: Boolean,
    ): Pair<GeneratedValue, SemanticType> {
        var currentValue = initialValue
        var currentType = ctx.expressionTypeMemory.recall(baseExpr) { currentValue.type }
        while (currentType is SemanticType.ReferenceType) {
            val inner = currentType.type.getOrNull() ?: break
            val innerIr = inner.toIRType()
            if (stopAtPointerTypes && inner !is SemanticType.ReferenceType && innerIr == TypeUtils.PTR) {
                currentType = inner
                currentValue = GeneratedValue(currentValue.value, inner)
                break
            }
            val loaded = currentEnv().bodyBuilder.insertLoad(innerIr, currentValue.value, temp("self.deref"))
            currentValue = GeneratedValue(loaded, inner)
            currentType = inner
        }
        return currentValue to currentType.unwrapReferences()
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

    private fun resolvePathFunctionSymbol(
        node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode,
        predicate: (SemanticSymbol.Function) -> Boolean,
    ): SemanticSymbol.Function? {
        val path = node.pathInExpressionNode.path
        if (path.isEmpty()) return null
        if (path.size == 1) {
            val identifier = path[0].name ?: return null
            return resolveFunction(identifier, predicate)
        }
        val member = path[1]
        val memberName = member.name ?: return null
        val baseSymbol = resolveTypeOwnerSymbol(path[0]) ?: return null
        val fn = when (baseSymbol) {
            is SemanticSymbol.Struct -> baseSymbol.functions[memberName]
            is SemanticSymbol.Enum -> baseSymbol.functions[memberName]
            is SemanticSymbol.Trait -> baseSymbol.functions[memberName]
            else -> null
        } ?: return null
        return fn.takeIf(predicate)
    }

    private fun resolveTypeOwnerSymbol(segment: PathIndentSegmentNode): SemanticSymbol? {
        return when (segment.token) {
            Token.I_IDENTIFIER -> {
                val name = segment.name ?: return null
                sequentialLookup(name, scopeMaintainer.currentScope) { it.typeST }?.symbol
            }
            Token.K_TYPE_SELF -> staticResolver.selfResolverRef.getSelf()
            else -> null
        }
    }

    private fun buildFunctionHeader(symbol: SemanticSymbol.Function): SemanticType.FunctionHeader {
        val selfType = symbol.selfParam.getOrNull()?.type?.getOrNull()
        val paramTypes = symbol.funcParams.getOrNull()?.map { it.type.getOrNull() ?: SemanticType.WildcardType } ?: emptyList()
        val returnType = symbol.returnType.getOrNull() ?: SemanticType.UnitType
        return SemanticType.FunctionHeader(symbol.identifier, selfType, paramTypes, returnType)
    }

    private fun arrayStorageType(array: SemanticType.ArrayType): ArrayType {
        val elementType = array.elementType.getOrNull()
            ?: throw IllegalStateException("Array element type unresolved for $array")
        val length = array.length.getOrNull()
            ?: throw IllegalStateException("Array length unresolved for $array")
        val lengthInt = length.value.toLong()
        require(lengthInt <= Int.MAX_VALUE) { "Array length too large for IR: $lengthInt" }
        return ArrayType(lengthInt.toInt(), elementType.toStorageIRType())
    }

    private fun storeArrayElement(
        storageType: ArrayType,
        storage: space.norb.llvm.core.Value,
        index: Long,
        value: GeneratedValue,
        elementSemanticType: SemanticType,
    ) {
        val builder = currentEnv().bodyBuilder
        val gep = builder.insertGep(
            storageType,
            storage,
            listOf(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                BuilderUtils.getIntConstant(index, TypeUtils.I32 as IntegerType),
            ),
            temp("array.elem")
        )
        // For large aggregate types, use memcpy to avoid LLVM crashes
        when (elementSemanticType) {
            is SemanticType.StructType,
            is SemanticType.ArrayType -> {
                val destPtr = builder.insertBitcast(gep, TypeUtils.PTR, temp("array.elem.dest"))
                val srcPtr = builder.insertBitcast(value.value, TypeUtils.PTR, temp("array.elem.src"))
                val size = emitTypeSizeBytes(elementSemanticType)
                val one = BuilderUtils.getIntConstant(1, TypeUtils.I32 as IntegerType)
                callMemfill(destPtr, srcPtr, size, one)
            }
            else -> builder.insertStore(value.value, gep)
        }
    }

    private fun storeValueInto(
        targetType: SemanticType,
        sourceValue: GeneratedValue,
        destinationPtr: Value,
        label: String,
    ) {
        val env = currentEnv()
        val underlying = targetType.unwrapReferences()
        // For aggregate types (structs and arrays), use memcpy instead of load/store
        // to avoid LLVM crashes with very large types
        if (targetType.requiresAggregateStorageCopy()) {
            val destPtr = env.bodyBuilder.insertBitcast(destinationPtr, TypeUtils.PTR, temp("$label.dest"))
            val srcPtr = env.bodyBuilder.insertBitcast(sourceValue.value, TypeUtils.PTR, temp("$label.src"))
            val size = emitTypeSizeBytes(underlying)
            val one = BuilderUtils.getIntConstant(1, TypeUtils.I32 as IntegerType)
            callMemfill(destPtr, srcPtr, size, one)
        } else {
            env.bodyBuilder.insertStore(sourceValue.value, destinationPtr)
        }
    }

    private fun SemanticType.requiresAggregateStorageCopy(): Boolean =
        this !is SemanticType.ReferenceType && when (unwrapReferences()) {
            is SemanticType.StructType,
            is SemanticType.ArrayType -> true
            else -> false
        }

    private fun SemanticType.isUnsignedInteger(): Boolean = when (unwrapReferences()) {
        is SemanticType.U32Type, is SemanticType.USizeType -> true
        else -> false
    }

    private fun SemanticType.prefersZeroExtension(): Boolean = when (unwrapReferences()) {
        is SemanticType.U32Type, is SemanticType.USizeType, SemanticType.BoolType, SemanticType.CharType -> true
        else -> false
    }

    private fun emitInfix(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode): GeneratedValue? {
        val env = currentEnv()
        val op = node.op
        if (op.isAssignmentVariant()) {
            val (lhsPtr, lhsType) = emitAssignmentPointer(node.left) ?: return null
            val rhs = emitExpression(node.right) ?: return null

            if (op == Token.O_EQ) {
                val needsAggregateCopy =
                    node.left !is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode &&
                        lhsType.requiresAggregateStorageCopy()
                if (needsAggregateCopy) {
                    storeValueInto(lhsType, rhs, lhsPtr, "assign")
                } else {
                    env.bodyBuilder.insertStore(rhs.value, lhsPtr)
                }
                return GeneratedValue(rhs.value, SemanticType.UnitType)
            }

            val lhsValue = env.bodyBuilder.insertLoad(lhsType.toIRType(), lhsPtr, temp("load.assign"))
            val lhsUnsigned = lhsType.isUnsignedInteger()
            val result = when (op) {
                Token.O_PLUS_EQ -> env.bodyBuilder.insertAdd(lhsValue, rhs.value, temp("add"))
                Token.O_MINUS_EQ -> env.bodyBuilder.insertSub(lhsValue, rhs.value, temp("sub"))
                Token.O_STAR_EQ -> env.bodyBuilder.insertMul(lhsValue, rhs.value, temp("mul"))
                Token.O_DIV_EQ -> if (lhsUnsigned) {
                    env.bodyBuilder.insertUDiv(lhsValue, rhs.value, temp("div"))
                } else {
                    env.bodyBuilder.insertSDiv(lhsValue, rhs.value, temp("div"))
                }
                Token.O_AND_EQ -> env.bodyBuilder.insertAnd(lhsValue, rhs.value, temp("and"))
                Token.O_OR_EQ -> env.bodyBuilder.insertOr(lhsValue, rhs.value, temp("or"))
                Token.O_XOR_EQ -> env.bodyBuilder.insertXor(lhsValue, rhs.value, temp("xor"))
                Token.O_SLFT_EQ -> env.bodyBuilder.insertShl(lhsValue, rhs.value, temp("shl"))
                Token.O_SRIT_EQ -> if (lhsUnsigned) {
                    env.bodyBuilder.insertLShr(lhsValue, rhs.value, temp("lshr"))
                } else {
                    env.bodyBuilder.insertAShr(lhsValue, rhs.value, temp("ashr"))
                }
                Token.O_PERCENT_EQ -> if (lhsUnsigned) {
                    env.bodyBuilder.insertURem(lhsValue, rhs.value, temp("rem"))
                } else {
                    env.bodyBuilder.insertSRem(lhsValue, rhs.value, temp("rem"))
                }
                else -> TODO("Compound assignment operator $op not yet supported")
            }
            env.bodyBuilder.insertStore(result, lhsPtr)
            return GeneratedValue(result, SemanticType.UnitType)
        }

        if (op == Token.O_DOUBLE_AND || op == Token.O_DOUBLE_OR) {
            return emitLogicalInfix(node, op == Token.O_DOUBLE_OR)
        }

        val left = emitExpression(node.left) ?: return null
        val right = emitExpression(node.right) ?: return null
        val arithmeticUnsigned = ctx.expressionTypeMemory.recall(node.left) { left.type }.isUnsignedInteger()
        return when (op) {
            Token.O_PLUS -> GeneratedValue(env.bodyBuilder.insertAdd(left.value, right.value, temp("add")), left.type)
            Token.O_MINUS -> GeneratedValue(env.bodyBuilder.insertSub(left.value, right.value, temp("sub")), left.type)
            Token.O_STAR -> GeneratedValue(env.bodyBuilder.insertMul(left.value, right.value, temp("mul")), left.type)
            Token.O_DIV -> GeneratedValue(
                if (arithmeticUnsigned) env.bodyBuilder.insertUDiv(left.value, right.value, temp("div"))
                else env.bodyBuilder.insertSDiv(left.value, right.value, temp("div")),
                left.type
            )
            Token.O_AND -> GeneratedValue(env.bodyBuilder.insertAnd(left.value, right.value, temp("and")), left.type)
            Token.O_OR -> GeneratedValue(env.bodyBuilder.insertOr(left.value, right.value, temp("or")), left.type)
            Token.O_BIT_XOR -> GeneratedValue(env.bodyBuilder.insertXor(left.value, right.value, temp("xor")), left.type)
            Token.O_SLFT -> GeneratedValue(env.bodyBuilder.insertShl(left.value, right.value, temp("shl")), left.type)
            Token.O_SRIT -> GeneratedValue(
                if (arithmeticUnsigned) env.bodyBuilder.insertLShr(left.value, right.value, temp("lshr"))
                else env.bodyBuilder.insertAShr(left.value, right.value, temp("ashr")),
                left.type
            )
            Token.O_PERCENT -> GeneratedValue(
                if (arithmeticUnsigned) env.bodyBuilder.insertURem(left.value, right.value, temp("rem"))
                else env.bodyBuilder.insertSRem(left.value, right.value, temp("rem")),
                left.type
            )
            Token.O_DOUBLE_EQ -> compareInts(IcmpPredicate.EQ, left, right)
            Token.O_NEQ -> compareInts(IcmpPredicate.NE, left, right)
            Token.O_LANG -> compareInts(IcmpPredicate.SLT, left, right)
            Token.O_RANG -> compareInts(IcmpPredicate.SGT, left, right)
            Token.O_LEQ -> compareInts(IcmpPredicate.SLE, left, right)
            Token.O_GEQ -> compareInts(IcmpPredicate.SGE, left, right)
            else -> null
        }
    }

    private fun emitLogicalInfix(
        node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode,
        shortCircuitOnTrue: Boolean
    ): GeneratedValue? {
        val env = currentEnv()
        val fn = env.function
        val left = emitExpression(node.left) ?: return null
        val rhsBlock = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        val shortBlock = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        val mergeBlock = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        val boolType = SemanticType.BoolType.toIRType()
        val resultSlot = env.allocaBuilder.insertAlloca(boolType, temp("logic.result"))

        if (shortCircuitOnTrue) {
            env.bodyBuilder.insertCondBr(left.value, shortBlock, rhsBlock)
            env.bodyBuilder.positionAtEnd(shortBlock)
            env.bodyBuilder.insertStore(boolConstant(true), resultSlot)
        } else {
            env.bodyBuilder.insertCondBr(left.value, rhsBlock, shortBlock)
            env.bodyBuilder.positionAtEnd(shortBlock)
            env.bodyBuilder.insertStore(boolConstant(false), resultSlot)
        }
        env.bodyBuilder.insertBr(mergeBlock)

        env.bodyBuilder.positionAtEnd(rhsBlock)
        val right = emitExpression(node.right) ?: return null
        env.bodyBuilder.insertStore(right.value, resultSlot)
        env.bodyBuilder.insertBr(mergeBlock)

        env.bodyBuilder.positionAtEnd(mergeBlock)
        val loaded = env.bodyBuilder.insertLoad(boolType, resultSlot, temp("logic.load"))
        return GeneratedValue(loaded, SemanticType.BoolType)
    }

    private fun compareInts(
        pred: IcmpPredicate,
        left: GeneratedValue,
        right: GeneratedValue,
    ): GeneratedValue {
        val cmp = currentEnv().bodyBuilder.insertICmp(pred, left.value, right.value, temp("cmp"))
        return GeneratedValue(cmp, SemanticType.BoolType)
    }

    private fun emitPrefix(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode): GeneratedValue? {
        val env = currentEnv()
        val expr = emitExpression(node.expr) ?: return null
        return when (node.op) {
            Token.O_MINUS -> {
                val zero = BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType)
                GeneratedValue(env.bodyBuilder.insertSub(zero, expr.value, temp("neg")), expr.type)
            }
            Token.O_NOT -> GeneratedValue(env.bodyBuilder.insertNot(expr.value, temp("not")), expr.type)
            else -> expr
        }
    }

    private fun emitReference(node: ExpressionNode.WithoutBlockExpressionNode.ReferenceExpressionNode): GeneratedValue? {
        val cachedType = ctx.expressionTypeMemory.recall(node) {
            SemanticType.ReferenceType(
                rusty.core.utils.Slot(SemanticType.WildcardType),
                rusty.core.utils.Slot(node.isMut)
            )
        } as? SemanticType.ReferenceType

        return when (val base = node.expr) {
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode ->
                emitPathReference(base, cachedType, node.isMut)
            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> {
                val (ptr, fieldType) = emitFieldPointer(base) ?: return null
                val referenceType = cachedType ?: SemanticType.ReferenceType(
                    rusty.core.utils.Slot(fieldType),
                    rusty.core.utils.Slot(node.isMut)
                )
                GeneratedValue(ptr, referenceType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> {
                val (ptr, elementType) = emitIndexPointer(base) ?: return null
                val referenceType = cachedType ?: SemanticType.ReferenceType(
                    rusty.core.utils.Slot(elementType),
                    rusty.core.utils.Slot(node.isMut)
                )
                GeneratedValue(ptr, referenceType)
            }
            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode -> {
                val inner = emitExpression(base.expr) ?: return null
                val referenceType = cachedType ?: SemanticType.ReferenceType(
                    rusty.core.utils.Slot(ctx.expressionTypeMemory.recall(base.expr) { inner.type }),
                    rusty.core.utils.Slot(node.isMut)
                )
                GeneratedValue(inner.value, referenceType)
            }
            else -> null
        }
    }

    private fun emitPathReference(
        base: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode,
        resolvedType: SemanticType.ReferenceType?,
        isMut: Boolean,
    ): GeneratedValue? {
        val symbolName = base.pathInExpressionNode.path.first().name ?: return null
        val env = currentEnv()
        val localMatch = env.findLocalSymbol(symbolName)
        val symbol = localMatch
            ?: sequentialLookup(symbolName, scopeMaintainer.currentScope) { it.variableST }?.symbol as? SemanticSymbol.Variable
            ?: return null
        val slot = env.findLocalSlot(symbol) ?: return null
        val symbolType = symbol.type.get()
        val baseType = ctx.expressionTypeMemory.recall(base) { symbolType }
        val underlyingType = when (baseType) {
            is SemanticType.ReferenceType -> baseType.type.getOrNull() ?: symbolType
            else -> baseType
        }
        val pointerValue = when (underlyingType) {
            is SemanticType.ArrayType,
            is SemanticType.StructType -> currentEnv().bodyBuilder.insertLoad(
                symbolType.toIRType(),
                slot,
                temp("addr.load")
            )
            else -> slot
        }
        val referenceType = resolvedType ?: SemanticType.ReferenceType(symbol.type, rusty.core.utils.Slot(isMut))
        return GeneratedValue(pointerValue, referenceType)
    }

    private fun emitDereference(node: ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode): GeneratedValue? {
        val baseValue = emitExpression(node.expr) ?: return null
        val (resolved, resolvedType) = unwrapReferenceValue(node.expr, baseValue, stopAtPointerTypes = true)
        return GeneratedValue(resolved.value, resolvedType)
    }

    private fun emitCast(node: ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode): GeneratedValue? {
        val expr = emitExpression(node.expr) ?: return null
        val targetType = staticResolver.resolveTypeNode(node.targetType, scopeMaintainer.currentScope)
        val targetIr = targetType.toIRType()
        val sourceIr = expr.type.toIRType()
        val sourceType = ctx.expressionTypeMemory.recall(node.expr) { expr.type }
        if (sourceIr == targetIr) return GeneratedValue(expr.value, targetType)
        val builder = currentEnv().bodyBuilder
        val casted = when (targetIr) {
            TypeUtils.I1 -> builder.insertTrunc(expr.value, TypeUtils.I1 as IntegerType, temp("trunc"))
            TypeUtils.I8 -> builder.insertTrunc(expr.value, TypeUtils.I8 as IntegerType, temp("trunc"))
            TypeUtils.I32 -> {
                val zeroExtend = sourceType.prefersZeroExtension()
                if (zeroExtend) {
                    builder.insertZExt(expr.value, TypeUtils.I32 as IntegerType, temp("zext"))
                } else {
                    builder.insertSExt(expr.value, TypeUtils.I32 as IntegerType, temp("sext"))
                }
            }
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

    private fun boolConstant(value: Boolean): Value =
        BuilderUtils.getIntConstant(if (value) 1 else 0, TypeUtils.I1 as IntegerType)

    private fun Token.isAssignmentVariant(): Boolean = this in setOf(
        Token.O_EQ, Token.O_PLUS_EQ, Token.O_MINUS_EQ, Token.O_STAR_EQ, Token.O_DIV_EQ,
        Token.O_PERCENT_EQ, Token.O_AND_EQ, Token.O_OR_EQ, Token.O_XOR_EQ, Token.O_SLFT_EQ, Token.O_SRIT_EQ,
    )

    private fun emitAssignmentPointer(node: ExpressionNode): Pair<Value, SemanticType>? {
        val env = currentEnv()
        return when (node) {
            is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> {
                val name = node.pathInExpressionNode.path.first().name ?: return null
                val symbol = env.findLocalSymbol(name)
                    ?: sequentialLookup(name, scopeMaintainer.currentScope) { it.variableST }?.symbol as? SemanticSymbol.Variable
                    ?: return null
                val slot = env.findLocalSlot(symbol) ?: return null
                Pair(slot, symbol.type.get())
            }
            is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> emitFieldPointer(node)
            is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> emitIndexPointer(node)
            is ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode -> {
                val base = emitExpression(node.expr) ?: return null
                val refType = ctx.expressionTypeMemory.recall(node.expr) { base.type } as? SemanticType.ReferenceType
                    ?: return null
                Pair(base.value, refType.type.get())
            }
            else -> null
        }
    }

    private fun emitFieldPointer(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode): Pair<Value, SemanticType>? {
        val baseValue = emitExpression(node.base) ?: return null
        val (resolvedBase, resolvedType) = unwrapReferenceValue(node.base, baseValue, stopAtPointerTypes = true)
        val structType = resolvedType as? SemanticType.StructType ?: return null
        val structSymbol = sequentialLookup(structType.identifier, scopeMaintainer.currentScope) { it.typeST }?.symbol
            as? SemanticSymbol.Struct ?: return null
        val fieldIndex = structSymbol.fields.keys.toList().indexOf(node.field)
        if (fieldIndex == -1) return null
        val fieldType = structSymbol.fields[node.field]?.get() ?: return null
        val structIrType = IRContext.structTypeLookup[structType.identifier] ?: return null
        val gep = currentEnv().bodyBuilder.insertGep(
            structIrType,
            resolvedBase.value,
            listOf(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                BuilderUtils.getIntConstant(fieldIndex.toLong(), TypeUtils.I32 as IntegerType)
            ),
            temp("${node.field}.addr")
        )
        return Pair(gep, fieldType)
    }

    private fun emitIndexPointer(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode): Pair<Value, SemanticType>? {
        val baseValue = emitExpression(node.base) ?: return null
        val (resolvedBase, resolvedType) = unwrapReferenceValue(node.base, baseValue, stopAtPointerTypes = true)
        val arrayType = resolvedType as? SemanticType.ArrayType ?: return null
        val elementType = arrayType.elementType.getOrNull() ?: return null
        val storageType = arrayStorageType(arrayType)
        val indexValue = emitExpression(node.index) ?: return null
        val gep = currentEnv().bodyBuilder.insertGep(
            storageType,
            resolvedBase.value,
            listOf(
                BuilderUtils.getIntConstant(0, TypeUtils.I32 as IntegerType),
                indexValue.value
            ),
            temp("index.addr")
        )
        return Pair(gep, elementType)
    }

    private fun emitBuiltinToString(baseExpr: ExpressionNode, baseValue: GeneratedValue): GeneratedValue? {
        var currentValue = baseValue
        var currentType = ctx.expressionTypeMemory.recall(baseExpr) { baseValue.type }
        while (currentType is SemanticType.ReferenceType) {
            val inner = currentType.type.getOrNull() ?: return null
            if (inner == SemanticType.StrType || inner == SemanticType.CStrType) {
                currentType = inner
                break
            }
            val loaded = currentEnv().bodyBuilder.insertLoad(inner.toIRType(), currentValue.value, temp("deref"))
            currentValue = GeneratedValue(loaded, inner)
            currentType = inner
        }
        return when (currentType) {
            is SemanticType.StrType -> GeneratedValue(currentValue.value, SemanticType.StringStructType)
            is SemanticType.StructType -> {
                if (currentType.identifier == "String") GeneratedValue(currentValue.value, SemanticType.StringStructType) else null
            }
            is SemanticType.I32Type, is SemanticType.ISizeType -> emitIntToString(currentValue, signed = true)
            is SemanticType.U32Type, is SemanticType.USizeType -> emitIntToString(currentValue, signed = false)
            else -> null
        }
    }

    private fun emitIntToString(value: GeneratedValue, signed: Boolean): GeneratedValue {
        val mallocFn = ensureExternalFunction(
            "malloc",
            TypeUtils.PTR,
            listOf(TypeUtils.I64),
            false
        )
        val sizeConst = BuilderUtils.getIntConstant(32L, TypeUtils.I64 as IntegerType)
        val buffer = currentEnv().bodyBuilder.insertCall(mallocFn, listOf(sizeConst), temp("str.alloc"))
        val formatLiteral = if (signed) emitStringLiteral("%d", true, SemanticType.CStrType)
        else emitStringLiteral("%u", true, SemanticType.CStrType)
        val sprintfFn = ensureExternalFunction(
            "sprintf",
            TypeUtils.I32,
            listOf(TypeUtils.PTR, TypeUtils.PTR),
            true
        )
        currentEnv().bodyBuilder.insertCall(sprintfFn, listOf(buffer, formatLiteral.value, value.value), temp("sprintf"))
        return GeneratedValue(buffer, SemanticType.StringStructType)
    }

    private fun emitTypeSizeBytes(type: SemanticType): Value {
        val builder = currentEnv().bodyBuilder
        val i32Type = TypeUtils.I32 as IntegerType
        return when (type) {
            is SemanticType.ReferenceType -> BuilderUtils.getIntConstant(8L, i32Type)
            SemanticType.StrType,
            SemanticType.CStrType -> BuilderUtils.getIntConstant(8L, i32Type)
            SemanticType.BoolType -> BuilderUtils.getIntConstant(1, i32Type)
            SemanticType.CharType -> BuilderUtils.getIntConstant(1, i32Type)
            SemanticType.UnitType,
            SemanticType.NeverType -> BuilderUtils.getIntConstant(1, i32Type)
            SemanticType.I32Type,
            SemanticType.U32Type,
            SemanticType.ISizeType,
            SemanticType.USizeType,
            SemanticType.AnyIntType,
            SemanticType.AnyUnsignedIntType,
            SemanticType.AnySignedIntType,
            SemanticType.ExitType -> BuilderUtils.getIntConstant(4, i32Type)
            is SemanticType.EnumType -> BuilderUtils.getIntConstant(4, i32Type)
            is SemanticType.StructType -> {
                val fn = IRContext.structSizeFunctionLookup[type.identifier]
                    ?: throw IllegalStateException("Struct size helper missing for ${type.identifier}")
                builder.insertCall(fn, emptyList(), temp("sizeof.${type.identifier}"))
            }
            is SemanticType.ArrayType -> {
                val element = type.elementType.getOrNull()
                    ?: throw IllegalStateException("Array element type unresolved for $type")
                val len = type.length.getOrNull()
                    ?: throw IllegalStateException("Array length unresolved for $type")
                val elementSize = emitTypeSizeBytes(element)
                val lenConst = BuilderUtils.getIntConstant(len.value.toLong(), i32Type)
                builder.insertMul(elementSize, lenConst, temp("sizeof.array"))
            }
            else -> BuilderUtils.getIntConstant(8L, i32Type)
        }
    }

    /**
     * Copy an aggregate value (struct or array) from source pointer to destination pointer
     * using memcpy. This is used to avoid LLVM crashes with very large aggregate types
     * when using load/store directly.
     */
    fun copyAggregate(
        sourceType: SemanticType,
        sourcePtr: Value,
        destPtr: Value,
        label: String,
    ) {
        val env = currentEnv()
        val destPtrCast = env.bodyBuilder.insertBitcast(destPtr, TypeUtils.PTR, temp("$label.dest"))
        val srcPtrCast = env.bodyBuilder.insertBitcast(sourcePtr, TypeUtils.PTR, temp("$label.src"))
        val size = emitTypeSizeBytes(sourceType)
        val one = BuilderUtils.getIntConstant(1, TypeUtils.I32 as IntegerType)
        callMemfill(destPtrCast, srcPtrCast, size, one)
    }

    private fun callMemfill(
        destPtr: Value,
        srcPtr: Value,
        chunkSize: Value,
        repeatCount: Value,
    ) {
        val fn = ensureExternalFunction(
            "aux.func.memfill",
            TypeUtils.VOID,
            listOf(TypeUtils.PTR, TypeUtils.PTR, TypeUtils.I32, TypeUtils.I32),
            false
        )
        currentEnv().bodyBuilder.insertVoidCall(fn, listOf(destPtr, srcPtr, chunkSize, repeatCount))
    }

    private fun ensureExternalFunction(
        name: String,
        returnType: space.norb.llvm.core.Type,
        paramTypes: List<space.norb.llvm.core.Type>,
        isVarArg: Boolean
    ): Function {
        return externalFunctions.getOrPut(name) {
            val fnType = FunctionType(returnType, paramTypes, isVarArg, emptyList())
            IRContext.module.registerFunction(name, fnType, LinkageType.EXTERNAL, isVarArg)
        }
    }
}
