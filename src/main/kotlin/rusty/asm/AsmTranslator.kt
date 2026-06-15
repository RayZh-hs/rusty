package rusty.asm

import rusty.core.RiscvTargetConfig
import rusty.asm.support.AsmContext
import rusty.asm.support.PlacedStackObject
import rusty.asm.support.StackFrame
import rusty.asm.utils.SavableSlot
import rusty.asm.utils.callerSavedRegisters
import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.binary.AShrInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.binary.AndInst
import space.norb.llvm.instructions.binary.LShrInst
import space.norb.llvm.instructions.binary.MulInst
import space.norb.llvm.instructions.binary.OrInst
import space.norb.llvm.instructions.binary.SDivInst
import space.norb.llvm.instructions.binary.SRemInst
import space.norb.llvm.instructions.binary.ShlInst
import space.norb.llvm.instructions.binary.SubInst
import space.norb.llvm.instructions.binary.UDivInst
import space.norb.llvm.instructions.binary.URemInst
import space.norb.llvm.instructions.binary.XorInst
import space.norb.llvm.instructions.casts.BitcastInst
import space.norb.llvm.instructions.casts.PtrToIntInst
import space.norb.llvm.instructions.casts.SExtInst
import space.norb.llvm.instructions.casts.TruncInst
import space.norb.llvm.instructions.casts.ZExtInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.instructions.terminators.ReturnInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.StructType
import space.norb.llvm.types.VoidType
import space.norb.llvm.utils.computeLayout
import space.norb.llvm.values.constants.ArrayConstant
import space.norb.llvm.values.constants.IntConstant
import space.norb.llvm.values.constants.NullPointerConstant
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.riscv.*
import space.norb.riscv.Register as RvRegister

internal class AsmTranslator(private val context: AsmContext) {
    private val argumentRegisters = listOf(a0, a1, a2, a3, a4, a5, a6, a7)
    private val registerBytes = RiscvTargetConfig.REGISTER_BYTES
    private val pointerWidthBits = RiscvTargetConfig.POINTER_WIDTH_BITS
    private val module: Module = context.module
    private lateinit var asm: RiscvAsm
    private lateinit var function: Function
    private lateinit var frame: StackFrame
    private lateinit var allocation: Map<Value, SavableSlot>
    private lateinit var allocaObjects: Map<AllocaInst, PlacedStackObject>
    private lateinit var blockLabels: Map<BasicBlock, String>
    private lateinit var edgePhiMoves: Map<Pair<BasicBlock, BasicBlock>, List<Pair<PhiNode, Value>>>

    fun translate(): String {
        return riscv(target = RiscvTargetConfig.ASM_TARGET) {
            asm = this
            emitGlobals()
            text()
            for (fn in module.functions.filter { it.hasBody() }) {
                lowerFunction(fn)
                blank()
            }
        }.render()
    }

    private fun emitGlobals() {
        if (module.globalVariables.isEmpty()) return
        asm.rodata()
        for (global in module.globalVariables) {
            val name = global.asmName()
            if (global.isConstantValue) {
                asm.global(name)
            }
            asm.label(name)
            emitGlobalInitializer(global)
        }
        asm.blank()
    }

    private fun emitGlobalInitializer(global: GlobalVariable) {
        val initializer = global.initializer
        when (initializer) {
            is ArrayConstant -> asm.emitArrayConstant(initializer)
            is IntConstant -> emitIntegerConstant(initializer)
            is NullPointerConstant -> emitPointerWord(0)
            null -> asm.zero(global.elementType?.sizeBytes(module) ?: 4)
            else -> asm.zero(initializer.type.sizeBytes(module))
        }
    }

    private fun emitIntegerConstant(value: IntConstant) {
        when (value.type.bitWidth) {
            in 1..8 -> asm.byte(value.value.toInt())
            in 9..16 -> asm.half(value.value.toInt())
            in 17..32 -> asm.word(expr(value.value.toString()))
            in 33..64 -> asm.directive(".dword", expr(value.value.toString()))
            else -> throw UnsupportedOperationException("Unsupported integer width ${value.type.bitWidth} in global")
        }
    }

    private fun emitPointerWord(value: Long) {
        if (registerBytes == 8) {
            asm.directive(".dword", expr(value.toString()))
        } else {
            asm.word(expr(value.toString()))
        }
    }

    private fun RiscvAsm.emitArrayConstant(array: ArrayConstant) {
        if (array.type.elementType == IntegerType.I8) {
            byte(*array.elements.map { (it as IntConstant).value.toInt() }.toIntArray())
            return
        }

        for (element in array.elements) {
            when (element) {
                is IntConstant -> when (element.type.bitWidth) {
                    in 1..8 -> byte(element.value.toInt())
                    in 9..16 -> half(element.value.toInt())
                    in 17..32 -> word(expr(element.value.toString()))
                    in 33..64 -> directive(".dword", expr(element.value.toString()))
                    else -> throw UnsupportedOperationException(
                        "Unsupported integer width ${element.type.bitWidth} in array initializer"
                    )
                }
                is ArrayConstant -> emitArrayConstant(element)
                is NullPointerConstant -> emitPointerWord(0)
                else -> zero(element.type.sizeBytes(module))
            }
        }
    }

    private fun lowerFunction(fn: Function) {
        function = fn
        frame = context.stackManager.getStackFrame(fn)
        allocation = context.registerAllocation.getValue(fn)
        blockLabels = fn.basicBlocks.associateWith { block -> "${fn.asmName()}.${block.asmName()}" }
        edgePhiMoves = buildEdgePhiMoves(fn)
        allocaObjects = collectAllocaObjects(fn)

        asm.global(fn.asmName())
        asm.type(fn.asmName(), "@function")
        asm.label(fn.asmName())
        emitPrologue()
        moveParametersToAllocatedLocations()

        for (block in fn.basicBlocks) {
            asm.label(blockLabels.getValue(block))
            for (instruction in block.instructionsIncludingTerminator()) {
                lowerInstruction(instruction)
            }
        }

        asm.size(fn.asmName(), ".-${fn.asmName()}")
    }

    private fun buildEdgePhiMoves(fn: Function): Map<Pair<BasicBlock, BasicBlock>, List<Pair<PhiNode, Value>>> {
        val moves = linkedMapOf<Pair<BasicBlock, BasicBlock>, MutableList<Pair<PhiNode, Value>>>()
        for (block in fn.basicBlocks) {
            for (phi in block.instructions.filterIsInstance<PhiNode>()) {
                for ((incomingValue, incomingBlock) in phi.incomingValues) {
                    moves.getOrPut(incomingBlock to block) { mutableListOf() }.add(phi to incomingValue)
                }
            }
        }
        return moves
    }

    private fun collectAllocaObjects(fn: Function): Map<AllocaInst, PlacedStackObject> {
        val result = linkedMapOf<AllocaInst, PlacedStackObject>()
        for ((index, alloca) in fn.instructions().filterIsInstance<AllocaInst>().withIndex()) {
            val name = alloca.stackObjectName(index)
            result[alloca] = frame.objectWithName(name)
                ?: frame.alloca(
                    sizeBytes = (alloca.allocatedType.sizeBytes(module).toLong() * alloca.constantArraySize())
                        .toIntExact("alloca $name"),
                    alignBytes = alloca.allocatedType.computeLayout(module, pointerWidthBits).alignment,
                    name = name,
                )
        }
        return result
    }

    private fun emitPrologue() {
        adjustStack(-frame.frameSizeBytes)
        for (saved in frame.frameObjects.filter { it.stackObject.savedRegister != null }) {
            val register = saved.stackObject.savedRegister!!.toRv()
            storeRegister(register, addressOfStack(saved, t6))
        }
    }

    private fun emitEpilogue() {
        for (saved in frame.frameObjects.filter { it.stackObject.savedRegister != null }.asReversed()) {
            val register = saved.stackObject.savedRegister!!.toRv()
            loadRegister(register, addressOfStack(saved, t6))
        }
        adjustStack(frame.frameSizeBytes)
        asm.ret()
    }

    private fun adjustStack(delta: Int) {
        if (delta == 0) return
        if (delta in -2048..2047) {
            asm.addi(sp, sp, delta)
        } else {
            asm.li(t6, kotlin.math.abs(delta))
            if (delta < 0) asm.sub(sp, sp, t6) else asm.add(sp, sp, t6)
        }
    }

    private fun moveParametersToAllocatedLocations() {
        for ((index, _) in function.parameters.withIndex()) {
            val src = if (index < argumentRegisters.size) {
                argumentRegisters[index]
            } else {
                val offset = frame.frameSizeBytes + (index - argumentRegisters.size) * registerBytes
                loadRegister(t4, stackArgumentAddress(offset, t6))
                t4
            }
            val saveTemp = resolveCallArgTemp(frame, index)
            storeRegister(src, addressOfStack(saveTemp, t6))
        }

        for ((index, parameter) in function.parameters.withIndex()) {
            val temp = resolveCallArgTemp(frame, index)
            val loaded = loadRegisterScratch(temp)
            val size = parameter.type.sizeBytes(module)
            if (size <= registerBytes) {
                writeValue(parameter, loaded)
            } else {
                val destination = addressOf(parameter, t6)
                copyMemory(destination, loaded, size)
            }
        }
    }

    private fun resolveCallArgTemp(frame: StackFrame, index: Int): PlacedStackObject {
        val name = callArgumentTempName(index)
        return frame.objectWithName(name)
            ?: frame.temp(sizeBytes = registerBytes, alignBytes = registerBytes, name = name)
    }

    private fun loadRegisterScratch(temp: PlacedStackObject): RvRegister {
        loadRegister(t5, addressOfStack(temp, t6))
        return t5
    }

    private fun lowerInstruction(instruction: Instruction) {
        when (instruction) {
            is CommentAttachment -> Unit
            is PhiNode -> Unit
            is AllocaInst -> lowerAlloca(instruction)
            is LoadInst -> lowerLoad(instruction)
            is StoreInst -> lowerStore(instruction)
            is GetElementPtrInst -> lowerGep(instruction)
            is BinaryInst -> lowerBinary(instruction)
            is ICmpInst -> lowerIcmp(instruction)
            is BranchInst -> lowerBranch(instruction)
            is ReturnInst -> lowerReturn(instruction)
            is CallInst -> lowerCall(instruction)
            is CastInst -> lowerCast(instruction)
            else -> throw UnsupportedOperationException(
                "Cannot lower ${instruction::class.simpleName} in ${function.name}"
            )
        }
    }

    private fun lowerAlloca(instruction: AllocaInst) {
        if (instruction !in allocation) return
        val obj = allocaObjects.getValue(instruction)
        writeValue(instruction, addressOfStack(obj, t6))
    }

    private fun lowerLoad(instruction: LoadInst) {
        val size = instruction.loadedType.sizeBytes(module)
        if (size <= registerBytes) {
            val source = addressOf(instruction.pointer, t6)
            val loaded = loadSized(t5, source, size)
            writeValue(instruction, loaded)
        } else {
            val destination = addressOf(instruction, t3)
            val source = addressOf(instruction.pointer, t6)
            copyMemory(destination, source, size)
        }
    }

    private fun lowerStore(instruction: StoreInst) {
        val size = instruction.storedType.sizeBytes(module)
        if (size <= registerBytes) {
            val value = loadValue(instruction.value, t4)
            storeSized(value, addressOf(instruction.pointer, t6), size)
        } else {
            val destination = addressOf(instruction.pointer, t3)
            val source = addressOf(instruction.value, t6)
            copyMemory(destination, source, size)
        }
    }

    private fun lowerGep(instruction: GetElementPtrInst) {
        val result = t4
        val base = addressOf(instruction.pointer, result)
        if (base != result) asm.mv(result, base)

        var currentType = instruction.elementType
        for ((indexPosition, indexValue) in instruction.indices.withIndex()) {
            val strideAndNext = gepStrideAndNextType(currentType, indexPosition, indexValue)
            val stride = strideAndNext.first
            currentType = strideAndNext.second
            if (stride == 0) continue

            val constantIndex = (indexValue as? IntConstant)?.value
            if (constantIndex != null) {
                addImmediate(result, result, (constantIndex * stride).toIntExact("gep offset"))
            } else {
                val indexRegister = loadValue(indexValue, t6)
                asm.li(t5, stride)
                asm.mul(t5, indexRegister, t5)
                asm.add(result, result, t5)
            }
        }

        writeValue(instruction, result)
    }

    private fun gepStrideAndNextType(
        currentType: Type,
        indexPosition: Int,
        indexValue: Value,
    ): Pair<Int, Type> {
        if (indexPosition == 0) {
            return currentType.sizeBytes(module) to currentType
        }

        return when (currentType) {
            is ArrayType -> currentType.elementType.sizeBytes(module) to currentType.elementType
            is StructType.AnonymousStructType -> {
                val field = (indexValue as? IntConstant)?.value?.toInt()
                    ?: throw UnsupportedOperationException("Dynamic struct GEP index in ${function.name}")
                structFieldOffset(currentType.elementTypes, currentType.isPacked, field) to currentType.elementTypes[field]
            }
            is StructType.NamedStructType -> {
                val field = (indexValue as? IntConstant)?.value?.toInt()
                    ?: throw UnsupportedOperationException("Dynamic struct GEP index in ${function.name}")
                val fields = (if (currentType.isOpaque()) module.getNamedStructType(currentType.name) else currentType)
                    ?.elementTypes
                    ?: throw UnsupportedOperationException("Opaque struct GEP for ${currentType.name}")
                structFieldOffset(fields, currentType.isPacked, field) to fields[field]
            }
            else -> currentType.sizeBytes(module) to currentType
        }
    }

    private fun lowerBinary(instruction: BinaryInst) {
        val lhs = loadValue(instruction.lhs, t4)
        val rhs = loadValue(instruction.rhs, t5)
        val dst = valueDestinationRegister(instruction, t6)
        val isI32 = instruction.type is IntegerType && (instruction.type as IntegerType).bitWidth == 32

        when (instruction) {
            is AddInst -> if (isI32) asm.emit("addw", dst, lhs, rhs) else asm.add(dst, lhs, rhs)
            is SubInst -> if (isI32) asm.emit("subw", dst, lhs, rhs) else asm.sub(dst, lhs, rhs)
            is MulInst -> if (isI32) asm.emit("mulw", dst, lhs, rhs) else asm.mul(dst, lhs, rhs)
            is SDivInst -> if (isI32) asm.emit("divw", dst, lhs, rhs) else asm.div(dst, lhs, rhs)
            is UDivInst -> if (isI32) asm.emit("divuw", dst, lhs, rhs) else asm.divu(dst, lhs, rhs)
            is SRemInst -> if (isI32) asm.emit("remw", dst, lhs, rhs) else asm.rem(dst, lhs, rhs)
            is URemInst -> if (isI32) asm.emit("remuw", dst, lhs, rhs) else asm.remu(dst, lhs, rhs)
            is AndInst -> asm.and(dst, lhs, rhs)
            is OrInst -> asm.or(dst, lhs, rhs)
            is XorInst -> asm.xor(dst, lhs, rhs)
            is ShlInst -> if (isI32) asm.emit("sllw", dst, lhs, rhs) else asm.sll(dst, lhs, rhs)
            is LShrInst -> if (isI32) asm.emit("srlw", dst, lhs, rhs) else asm.srl(dst, lhs, rhs)
            is AShrInst -> if (isI32) asm.emit("sraw", dst, lhs, rhs) else asm.sra(dst, lhs, rhs)
            else -> throw UnsupportedOperationException(
                "Cannot lower binary ${instruction::class.simpleName} in ${function.name}"
            )
        }

        writeValue(instruction, dst)
    }

    private fun lowerIcmp(instruction: ICmpInst) {
        val lhs = loadValue(instruction.lhs, t4)
        val rhs = loadValue(instruction.rhs, t5)
        val dst = valueDestinationRegister(instruction, t6)

        when (instruction.predicate.name) {
            "EQ" -> {
                asm.xor(dst, lhs, rhs)
                asm.seqz(dst, dst)
            }
            "NE" -> {
                asm.xor(dst, lhs, rhs)
                asm.snez(dst, dst)
            }
            "SLT" -> asm.slt(dst, lhs, rhs)
            "SGT" -> asm.slt(dst, rhs, lhs)
            "SLE" -> {
                asm.slt(dst, rhs, lhs)
                asm.xori(dst, dst, 1)
            }
            "SGE" -> {
                asm.slt(dst, lhs, rhs)
                asm.xori(dst, dst, 1)
            }
            "ULT" -> asm.sltu(dst, lhs, rhs)
            "UGT" -> asm.sltu(dst, rhs, lhs)
            "ULE" -> {
                asm.sltu(dst, rhs, lhs)
                asm.xori(dst, dst, 1)
            }
            "UGE" -> {
                asm.sltu(dst, lhs, rhs)
                asm.xori(dst, dst, 1)
            }
            else -> throw UnsupportedOperationException("Unknown icmp predicate ${instruction.predicate}")
        }

        writeValue(instruction, dst)
    }

    private fun lowerBranch(instruction: BranchInst) {
        val currentBlock = instruction.getParent() as? BasicBlock
            ?: function.basicBlocks.first { it.terminator == instruction }

        if (instruction.isUnconditional()) {
            val destination = instruction.getDestination() as BasicBlock
            emitPhiMoves(currentBlock, destination)
            asm.j(blockLabels.getValue(destination))
            return
        }

        val trueDestination = instruction.getTrueDestination() as BasicBlock
        val falseDestination = instruction.getFalseDestination() as BasicBlock
        val falseEdgeLabel = "${blockLabels.getValue(currentBlock)}.__false_edge"
        val cond = loadValue(instruction.getCondition()!!, t5)

        asm.beqz(cond, falseEdgeLabel)
        emitPhiMoves(currentBlock, trueDestination)
        asm.j(blockLabels.getValue(trueDestination))
        asm.label(falseEdgeLabel)
        emitPhiMoves(currentBlock, falseDestination)
        asm.j(blockLabels.getValue(falseDestination))
    }

    private fun emitPhiMoves(from: BasicBlock, to: BasicBlock) {
        val pending = edgePhiMoves[from to to].orEmpty().toMutableList()
        while (pending.isNotEmpty()) {
            val nextIndex = pending.indexOfFirst { (candidatePhi, _) ->
                val candidateDestination = allocation[candidatePhi]
                pending.none { (_, incoming) -> incoming !== candidatePhi && allocation[incoming] == candidateDestination }
            }.takeIf { it >= 0 } ?: 0

            val (phi, incoming) = pending.removeAt(nextIndex)
            if (phi.type.sizeBytes(module) <= registerBytes) {
                val loaded = loadValue(incoming, t5)
                writeValue(phi, loaded)
            } else {
                val phiDest = addressOf(phi, t3)
                val incomingSrc = addressOf(incoming, t6)
                copyMemory(phiDest, incomingSrc, phi.type.sizeBytes(module))
            }
        }
    }

    private fun lowerReturn(instruction: ReturnInst) {
        val returnValue = instruction.getReturnValue()
        if (returnValue != null && returnValue.type != VoidType) {
            val size = returnValue.type.sizeBytes(module)
            if (size <= registerBytes) {
                val reg = loadValue(returnValue, a0)
                if (reg != a0) asm.mv(a0, reg)
            } else {
                throw UnsupportedOperationException("Direct aggregate returns are not lowered yet in ${function.name}")
            }
        }
        emitEpilogue()
    }

    private fun lowerCall(instruction: CallInst) {
        val callerSavedTemps = callerSavedTemps()
        for ((register, temp) in callerSavedTemps) {
            storeRegister(register.toRv(), addressOfStack(temp, t6))
        }

        for ((index, argument) in instruction.arguments.withIndex()) {
            val temp = frame.objectWithName(callArgumentTempName(index))
                ?: throw IllegalStateException("Missing call argument temp $index in ${function.name}")
            val value = if (argument.type.sizeBytes(module) <= registerBytes) {
                loadValue(argument, t5)
            } else {
                addressOf(argument, t5)
            }
            storeRegister(value, addressOfStack(temp, t6))
        }

        for (index in instruction.arguments.indices.take(argumentRegisters.size)) {
            val temp = frame.objectWithName(callArgumentTempName(index))
                ?: throw IllegalStateException("Missing call argument temp $index in ${function.name}")
            loadRegister(argumentRegisters[index], addressOfStack(temp, t6))
        }

        val stackArgumentCount = (instruction.arguments.size - argumentRegisters.size).coerceAtLeast(0)
        val stackArgumentBytes = alignUp(stackArgumentCount * registerBytes, 16)
        for (stackIndex in 0 until stackArgumentCount) {
            val argumentIndex = argumentRegisters.size + stackIndex
            val temp = frame.objectWithName(callArgumentTempName(argumentIndex))
                ?: throw IllegalStateException("Missing call argument temp $argumentIndex in ${function.name}")
            loadRegister(t4, addressOfStack(temp, t6))
            storeRegister(t4, stackArgumentAddress(stackIndex * registerBytes - stackArgumentBytes, t6))
        }

        adjustStack(-stackArgumentBytes)
        val callee = instruction.callee
        when (callee) {
            is Function -> asm.call(callee.asmName())
            else -> {
                val target = loadValue(callee, t5)
                asm.jalr(ra, target, 0)
            }
        }
        adjustStack(stackArgumentBytes)

        val returnScratch = if (instruction.producesValue()) t5 else null
        if (instruction.producesValue() && instruction in allocation) {
            asm.mv(returnScratch!!, a0)
        }

        for ((register, temp) in callerSavedTemps.asReversed()) {
            loadRegister(register.toRv(), addressOfStack(temp, t6))
        }

        if (instruction.producesValue() && instruction in allocation) {
            writeValue(instruction, returnScratch!!)
        }
    }

    private fun callerSavedTemps(): List<Pair<rusty.asm.utils.Register, PlacedStackObject>> {
        return allocation.values
            .asSequence()
            .filterIsInstance<SavableSlot.Register>()
            .map { it.physical }
            .filter { it in callerSavedRegisters }
            .distinct()
            .mapNotNull { register ->
                frame.objectWithName(register.callSaveTempName())?.let { register to it }
            }
            .toList()
    }

    private fun lowerCast(instruction: CastInst) {
        val source = loadValue(instruction.value, t5)
        val dst = valueDestinationRegister(instruction, t5)

        when (instruction) {
            is BitcastInst, is PtrToIntInst -> {
                if (dst != source) asm.mv(dst, source)
            }
            is TruncInst -> {
                val bits = instruction.getDestinationBitWidth()
                if (bits >= registerBytes * 8) {
                    if (dst != source) asm.mv(dst, source)
                } else {
                    val shift = registerBytes * 8 - bits
                    asm.slli(dst, source, shift)
                    asm.srli(dst, dst, shift)
                }
            }
            is ZExtInst -> {
                val bits = instruction.getSourceBitWidth()
                when {
                    bits >= registerBytes * 8 -> if (dst != source) asm.mv(dst, source)
                    bits <= 0 -> asm.li(dst, 0)
                    else -> {
                        val shift = registerBytes * 8 - bits
                        asm.slli(dst, source, shift)
                        asm.srli(dst, dst, shift)
                    }
                }
            }
            is SExtInst -> {
                val bits = instruction.getSourceBitWidth()
                if (bits >= registerBytes * 8) {
                    if (dst != source) asm.mv(dst, source)
                } else {
                    val shift = registerBytes * 8 - bits
                    asm.slli(dst, source, shift)
                    asm.srai(dst, dst, shift)
                }
            }
            else -> throw UnsupportedOperationException(
                "Cannot lower cast ${instruction::class.simpleName} in ${function.name}"
            )
        }

        writeValue(instruction, dst)
    }

    private fun valueDestinationRegister(value: Value, fallback: RvRegister): RvRegister {
        return (allocation[value] as? SavableSlot.Register)?.physical?.toRv() ?: fallback
    }

    private fun loadValue(value: Value, scratch: RvRegister): RvRegister {
        when (value) {
            is IntConstant -> {
                loadImmediate(scratch, value.value)
                return scratch
            }
            is NullPointerConstant -> {
                loadImmediate(scratch, 0)
                return scratch
            }
            is GlobalVariable -> {
                asm.la(scratch, value.asmName())
                return scratch
            }
            is Function -> {
                asm.la(scratch, value.asmName())
                return scratch
            }
        }

        return when (val slot = allocation[value]) {
            is SavableSlot.Register -> slot.physical.toRv()
            is SavableSlot.Stack -> {
                val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                    ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                loadSized(scratch, addressOfStack(obj, scratch), value.type.sizeBytes(module))
            }
            null -> throw IllegalStateException("No register allocation for ${value.getIdentifier()} in ${function.name}")
        }
    }

    private fun writeValue(value: Value, source: RvRegister) {
        when (val slot = allocation[value]) {
            is SavableSlot.Register -> {
                val destination = slot.physical.toRv()
                if (destination != source) asm.mv(destination, source)
            }
            is SavableSlot.Stack -> {
                val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                    ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                val addressScratch = if (source == t4) t6 else t4
                val safeSource = if (source == t6) { asm.mv(t3, source); t3 } else source
                storeSized(safeSource, addressOfStack(obj, addressScratch), value.type.sizeBytes(module))
            }
            null -> Unit
        }
    }

    private fun addressOf(value: Value, scratch: RvRegister): RvRegister {
        return when (value) {
            is AllocaInst -> addressOfStack(allocaObjects.getValue(value), scratch)
            is GlobalVariable -> {
                asm.la(scratch, value.asmName())
                scratch
            }
            else -> {
                val slot = allocation[value]
                if (slot is SavableSlot.Stack && value.type.sizeBytes(module) > registerBytes) {
                    val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                        ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                    addressOfStack(obj, scratch)
                } else {
                    loadValue(value, scratch)
                }
            }
        }
    }

    private fun addressOfStack(obj: PlacedStackObject, destination: RvRegister): RvRegister {
        addImmediate(destination, sp, obj.offsetFromSp)
        return destination
    }

    private fun stackArgumentAddress(offsetFromSp: Int, destination: RvRegister): RvRegister {
        addImmediate(destination, sp, offsetFromSp)
        return destination
    }

    private fun loadSized(destination: RvRegister, address: RvRegister, sizeBytes: Int): RvRegister {
        when (sizeBytes) {
            1 -> asm.lbu(destination, mem(0, address))
            2 -> asm.lhu(destination, mem(0, address))
            3, 4 -> asm.lw(destination, mem(0, address))
            5, 6, 7, 8 -> asm.emit("ld", destination, mem(0, address))
            else -> throw UnsupportedOperationException("Cannot scalar-load $sizeBytes bytes in ${function.name}")
        }
        return destination
    }

    private fun storeSized(source: RvRegister, address: RvRegister, sizeBytes: Int) {
        when (sizeBytes) {
            1 -> asm.sb(source, mem(0, address))
            2 -> asm.sh(source, mem(0, address))
            3, 4 -> asm.sw(source, mem(0, address))
            5, 6, 7, 8 -> asm.emit("sd", source, mem(0, address))
            else -> throw UnsupportedOperationException("Cannot scalar-store $sizeBytes bytes in ${function.name}")
        }
    }

    private fun loadRegister(destination: RvRegister, address: RvRegister) {
        if (registerBytes == 8) {
            asm.emit("ld", destination, mem(0, address))
        } else {
            asm.lw(destination, mem(0, address))
        }
    }

    private fun storeRegister(source: RvRegister, address: RvRegister) {
        if (registerBytes == 8) {
            asm.emit("sd", source, mem(0, address))
        } else {
            asm.sw(source, mem(0, address))
        }
    }

    private fun loadImmediate(destination: RvRegister, value: Long) {
        if (value in Int.MIN_VALUE..Int.MAX_VALUE) {
            asm.li(destination, value.toInt())
        } else {
            asm.emit("li", destination, expr(value.toString()))
        }
    }

    private fun copyMemory(destination: RvRegister, source: RvRegister, sizeBytes: Int) {
        var offset = 0
        val chunkBytes = 2040 / registerBytes * registerBytes
        while (offset < sizeBytes) {
            val remaining = sizeBytes - offset
            if (offset in -2048..2047 && remaining <= chunkBytes) {
                while (offset + registerBytes <= sizeBytes) {
                    if (registerBytes == 8) {
                        asm.emit("ld", t4, mem(offset, source))
                        asm.emit("sd", t4, mem(offset, destination))
                    } else {
                        asm.lw(t4, mem(offset, source))
                        asm.sw(t4, mem(offset, destination))
                    }
                    offset += registerBytes
                }
                while (offset < sizeBytes) {
                    asm.lbu(t4, mem(offset, source))
                    asm.sb(t4, mem(offset, destination))
                    offset += 1
                }
            } else {
                val srcBase = if (source == t3) { asm.mv(t5, source); t5 } else source
                val dstBase = if (destination == t6) { asm.mv(t4, destination); t4 } else destination
                addImmediate(t3, dstBase, offset)
                addImmediate(t6, srcBase, offset)
                var chunk = 0
                val chunkSize = kotlin.math.min(remaining, chunkBytes)
                while (chunk + registerBytes <= chunkSize) {
                    if (registerBytes == 8) {
                        asm.emit("ld", t4, mem(chunk, t6))
                        asm.emit("sd", t4, mem(chunk, t3))
                    } else {
                        asm.lw(t4, mem(chunk, t6))
                        asm.sw(t4, mem(chunk, t3))
                    }
                    chunk += registerBytes
                }
                while (chunk < chunkSize) {
                    asm.lbu(t4, mem(chunk, t6))
                    asm.sb(t4, mem(chunk, t3))
                    chunk += 1
                }
                offset += chunk
            }
        }
    }

    private fun addImmediate(destination: RvRegister, base: RvRegister, immediate: Int) {
        if (immediate == 0) {
            if (destination != base) asm.mv(destination, base)
        } else if (immediate in -2048..2047) {
            asm.addi(destination, base, immediate)
        } else {
            val scratch = when {
                t6 != destination && t6 != base -> t6
                t5 != destination && t5 != base -> t5
                else -> t4
            }
            asm.li(scratch, immediate)
            asm.add(destination, base, scratch)
        }
    }

    private fun structFieldOffset(fields: List<Type>, packed: Boolean, field: Int): Int {
        require(field in fields.indices) { "Struct field $field out of bounds" }
        var offset = 0
        for (index in 0 until field) {
            val layout = fields[index].computeLayout(module, pointerWidthBits)
            val align = if (packed) 1 else layout.alignment
            offset = alignUp(offset, align)
            offset += layout.sizeInBytes.toIntExact("struct field")
        }
        val fieldAlign = if (packed) 1 else fields[field].computeLayout(module, pointerWidthBits).alignment
        return alignUp(offset, fieldAlign)
    }
}
