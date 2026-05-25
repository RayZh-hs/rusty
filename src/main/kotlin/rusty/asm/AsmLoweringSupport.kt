package rusty.asm

import rusty.core.RiscvTargetConfig
import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.utils.computeLayout
import space.norb.llvm.values.constants.IntConstant
import space.norb.riscv.x
import space.norb.riscv.Register as RvRegister

internal fun Function.instructions(): Sequence<Instruction> =
    basicBlocks.asSequence().flatMap { it.instructionsIncludingTerminator() }

internal fun Function.hasBody(): Boolean = !isDeclaration && basicBlocks.isNotEmpty()

internal fun BasicBlock.instructionsIncludingTerminator(): Sequence<Instruction> = sequence {
    yieldAll(instructions)
    val terminator = terminator
    if (terminator != null && instructions.lastOrNull() !== terminator) {
        yield(terminator)
    }
}

internal fun Function.containsCall(): Boolean = instructions().any { it is CallInst }

internal fun AllocaInst.stackObjectName(index: Int): String = name ?: "alloca.$index"

internal fun AllocaInst.constantArraySize(): Long {
    val arraySize = getOperandsList().firstOrNull() ?: return 1L
    return (arraySize as? IntConstant)?.value
        ?: throw UnsupportedOperationException("Dynamic alloca size is not lowered yet")
}

internal fun Value.asmName(): String = sanitizeAsmName(name ?: getIdentifier())

internal fun BasicBlock.asmName(): String = sanitizeAsmName(name ?: "bb$id")

private fun sanitizeAsmName(value: String): String {
    val sanitized = buildString {
        for (char in value) {
            append(
                when {
                    char.isLetterOrDigit() || char == '_' || char == '.' || char == '$' -> char
                    else -> '_'
                }
            )
        }
    }
    return if (sanitized.firstOrNull()?.isDigit() == true) "_$sanitized" else sanitized
}

internal fun rusty.asm.utils.Register.toRv(): RvRegister = x(id)

internal fun rusty.asm.utils.Register.callSaveTempName(): String = "call.${name.lowercase()}"

internal fun callArgumentTempName(index: Int): String = "call.arg.$index"

internal fun Type.sizeBytes(module: Module): Int =
    computeLayout(module, pointerWidthBits = RiscvTargetConfig.POINTER_WIDTH_BITS).sizeInBytes.toIntExact("type $this")

internal fun Constant.sizeBytes(module: Module): Int = type.sizeBytes(module)

internal fun Long.toIntExact(description: String): Int {
    require(this <= Int.MAX_VALUE && this >= Int.MIN_VALUE) { "$description is too large: $this" }
    return toInt()
}

internal fun alignUp(value: Int, align: Int): Int {
    val extra = value % align
    return if (extra == 0) value else value + align - extra
}
