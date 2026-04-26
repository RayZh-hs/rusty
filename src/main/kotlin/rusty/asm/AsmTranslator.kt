package rusty.asm

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
    private val module: Module = context.module
    private lateinit var asm: RiscvAsm
    private lateinit var function: Function
    private lateinit var frame: StackFrame
    private lateinit var allocation: Map<Value, SavableSlot>
    private lateinit var allocaObjects: Map<AllocaInst, PlacedStackObject>
    private lateinit var blockLabels: Map<BasicBlock, String>
    private lateinit var edgePhiMoves: Map<Pair<BasicBlock, BasicBlock>, List<Pair<PhiNode, Value>>>

    fun translate(): String {
        return riscv {
            asm = this
            emitGlobals()
            text()
            for (fn in module.functions.filterNot { it.isDeclaration }) {
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
            is IntConstant -> asm.word(initializer.value.toInt())
            is NullPointerConstant -> asm.word(0)
            null -> asm.zero(global.elementType?.sizeBytes(module) ?: 4)
            else -> asm.zero(initializer.type.sizeBytes(module))
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
                    else -> word(element.value.toInt())
                }
                is ArrayConstant -> emitArrayConstant(element)
                is NullPointerConstant -> word(0)
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
                    alignBytes = alloca.allocatedType.computeLayout(module, 32).alignment,
                    name = name,
                )
        }
        return result
    }

    private fun emitPrologue() {
        adjustStack(-frame.frameSizeBytes)
        for (saved in frame.frameObjects.filter { it.stackObject.savedRegister != null }) {
            val register = saved.stackObject.savedRegister!!.toRv()
            storeWord(register, addressOfStack(saved, t6))
        }
    }

    private fun emitEpilogue() {
        for (saved in frame.frameObjects.filter { it.stackObject.savedRegister != null }.asReversed()) {
            val register = saved.stackObject.savedRegister!!.toRv()
            loadWord(register, addressOfStack(saved, t6))
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
        for ((index, parameter) in function.parameters.withIndex()) {
            if (index >= argumentRegisters.size) {
                throw UnsupportedOperationException("Stack-passed parameters are not lowered yet in ${function.name}")
            }

            val incoming = argumentRegisters[index]
            val size = parameter.type.sizeBytes(module)
            if (size <= 4) {
                writeValue(parameter, incoming)
            } else {
                val destination = addressOf(parameter, t6)
                copyMemory(destination, incoming, size)
            }
        }
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
        val source = addressOf(instruction.pointer, t6)
        if (size <= 4) {
            val loaded = loadSized(t5, source, size)
            writeValue(instruction, loaded)
        } else {
            copyMemory(addressOf(instruction, t5), source, size)
        }
    }

    private fun lowerStore(instruction: StoreInst) {
        val size = instruction.storedType.sizeBytes(module)
        val destination = addressOf(instruction.pointer, t6)
        if (size <= 4) {
            storeSized(loadValue(instruction.value, t5), destination, size)
        } else {
            copyMemory(destination, addressOf(instruction.value, t5), size)
        }
    }

    private fun lowerGep(instruction: GetElementPtrInst) {
        val result = valueDestinationRegister(instruction, t5)
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
        val lhs = loadValue(instruction.lhs, t5)
        val rhs = loadValue(instruction.rhs, t6)
        val dst = valueDestinationRegister(instruction, t5)

        when (instruction) {
            is AddInst -> asm.add(dst, lhs, rhs)
            is SubInst -> asm.sub(dst, lhs, rhs)
            is MulInst -> asm.mul(dst, lhs, rhs)
            is SDivInst -> asm.div(dst, lhs, rhs)
            is UDivInst -> asm.divu(dst, lhs, rhs)
            is SRemInst -> asm.rem(dst, lhs, rhs)
            is URemInst -> asm.remu(dst, lhs, rhs)
            is AndInst -> asm.and(dst, lhs, rhs)
            is OrInst -> asm.or(dst, lhs, rhs)
            is XorInst -> asm.xor(dst, lhs, rhs)
            is ShlInst -> asm.sll(dst, lhs, rhs)
            is LShrInst -> asm.srl(dst, lhs, rhs)
            is AShrInst -> asm.sra(dst, lhs, rhs)
            else -> throw UnsupportedOperationException(
                "Cannot lower binary ${instruction::class.simpleName} in ${function.name}"
            )
        }

        writeValue(instruction, dst)
    }

    private fun lowerIcmp(instruction: ICmpInst) {
        val lhs = loadValue(instruction.lhs, t5)
        val rhs = loadValue(instruction.rhs, t6)
        val dst = valueDestinationRegister(instruction, t5)

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
            if (phi.type.sizeBytes(module) <= 4) {
                val loaded = loadValue(incoming, t5)
                writeValue(phi, loaded)
            } else {
                copyMemory(addressOf(phi, t6), addressOf(incoming, t5), phi.type.sizeBytes(module))
            }
        }
    }

    private fun lowerReturn(instruction: ReturnInst) {
        val returnValue = instruction.getReturnValue()
        if (returnValue != null && returnValue.type != VoidType) {
            val size = returnValue.type.sizeBytes(module)
            if (size <= 4) {
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
            storeWord(register.toRv(), addressOfStack(temp, t6))
        }

        for ((index, argument) in instruction.arguments.withIndex()) {
            if (index >= argumentRegisters.size) {
                throw UnsupportedOperationException("Stack-passed call arguments are not lowered yet in ${function.name}")
            }

            val target = argumentRegisters[index]
            if (argument.type.sizeBytes(module) <= 4) {
                val value = loadValue(argument, target)
                if (value != target) asm.mv(target, value)
            } else {
                val address = addressOf(argument, target)
                if (address != target) asm.mv(target, address)
            }
        }

        val callee = instruction.callee
        when (callee) {
            is Function -> asm.call(callee.asmName())
            else -> {
                val target = loadValue(callee, t5)
                asm.jalr(ra, target, 0)
            }
        }

        val returnScratch = if (instruction.producesValue()) t5 else null
        if (instruction.producesValue() && instruction in allocation) {
            asm.mv(returnScratch!!, a0)
        }

        for ((register, temp) in callerSavedTemps.asReversed()) {
            loadWord(register.toRv(), addressOfStack(temp, t6))
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
                if (bits >= 32) {
                    if (dst != source) asm.mv(dst, source)
                } else {
                    val shift = 32 - bits
                    asm.slli(dst, source, shift)
                    asm.srli(dst, dst, shift)
                }
            }
            is ZExtInst -> {
                val bits = instruction.getSourceBitWidth()
                when {
                    bits >= 32 -> if (dst != source) asm.mv(dst, source)
                    bits <= 0 -> asm.li(dst, 0)
                    else -> {
                        val shift = 32 - bits
                        asm.slli(dst, source, shift)
                        asm.srli(dst, dst, shift)
                    }
                }
            }
            is SExtInst -> {
                val bits = instruction.getSourceBitWidth()
                if (bits >= 32) {
                    if (dst != source) asm.mv(dst, source)
                } else {
                    val shift = 32 - bits
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
                asm.li(scratch, value.value.toInt())
                return scratch
            }
            is NullPointerConstant -> {
                asm.li(scratch, 0)
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
                storeSized(source, addressOfStack(obj, t6), value.type.sizeBytes(module))
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
                if (slot is SavableSlot.Stack && value.type.sizeBytes(module) > 4) {
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

    private fun loadSized(destination: RvRegister, address: RvRegister, sizeBytes: Int): RvRegister {
        when (sizeBytes) {
            1 -> asm.lbu(destination, mem(0, address))
            2 -> asm.lhu(destination, mem(0, address))
            3, 4 -> asm.lw(destination, mem(0, address))
            else -> throw UnsupportedOperationException("Cannot scalar-load $sizeBytes bytes in ${function.name}")
        }
        return destination
    }

    private fun storeSized(source: RvRegister, address: RvRegister, sizeBytes: Int) {
        when (sizeBytes) {
            1 -> asm.sb(source, mem(0, address))
            2 -> asm.sh(source, mem(0, address))
            3, 4 -> asm.sw(source, mem(0, address))
            else -> throw UnsupportedOperationException("Cannot scalar-store $sizeBytes bytes in ${function.name}")
        }
    }

    private fun loadWord(destination: RvRegister, address: RvRegister) {
        asm.lw(destination, mem(0, address))
    }

    private fun storeWord(source: RvRegister, address: RvRegister) {
        asm.sw(source, mem(0, address))
    }

    private fun copyMemory(destination: RvRegister, source: RvRegister, sizeBytes: Int) {
        var offset = 0
        while (offset + 4 <= sizeBytes) {
            asm.lw(t6, mem(offset, source))
            asm.sw(t6, mem(offset, destination))
            offset += 4
        }
        while (offset < sizeBytes) {
            asm.lbu(t6, mem(offset, source))
            asm.sb(t6, mem(offset, destination))
            offset += 1
        }
    }

    private fun addImmediate(destination: RvRegister, base: RvRegister, immediate: Int) {
        if (immediate == 0) {
            if (destination != base) asm.mv(destination, base)
        } else if (immediate in -2048..2047) {
            asm.addi(destination, base, immediate)
        } else {
            asm.li(t6, immediate)
            asm.add(destination, base, t6)
        }
    }

    private fun structFieldOffset(fields: List<Type>, packed: Boolean, field: Int): Int {
        require(field in fields.indices) { "Struct field $field out of bounds" }
        var offset = 0
        for (index in 0 until field) {
            val layout = fields[index].computeLayout(module, 32)
            val align = if (packed) 1 else layout.alignment
            offset = alignUp(offset, align)
            offset += layout.sizeInBytes.toIntExact("struct field")
        }
        val fieldAlign = if (packed) 1 else fields[field].computeLayout(module, 32).alignment
        return alignUp(offset, fieldAlign)
    }
}
