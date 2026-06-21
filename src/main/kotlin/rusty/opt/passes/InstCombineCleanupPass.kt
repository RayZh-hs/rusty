package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.core.ValueUseRegistry
import space.norb.llvm.enums.IcmpPredicate
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.instructions.base.TerminatorInst
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
import space.norb.llvm.instructions.casts.SExtInst
import space.norb.llvm.instructions.casts.TruncInst
import space.norb.llvm.instructions.casts.ZExtInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant
import java.lang.Long.divideUnsigned
import java.lang.Long.remainderUnsigned

object InstCombineCleanupPass : IRPass() {
    private const val MAX_ITERATIONS = 8

    override fun run(module: Module, am: AnalysisManager): Module {
        var changed = false

        repeat(MAX_ITERATIONS) {
            var iterationChanged = false
            for (function in module.functions) {
                if (function.isDeclaration || function.entryBlock == null) continue
                iterationChanged = simplifyFunction(function) || iterationChanged
                iterationChanged = eliminateDeadInstructions(function) || iterationChanged
            }
            changed = changed || iterationChanged
            if (!iterationChanged) {
                if (changed) am.invalidateAll() else am.invalidateNone()
                return module
            }
        }

        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun simplifyFunction(function: Function): Boolean {
        var changed = false
        for (block in function.basicBlocks) {
            for (instruction in block.instructions.toList()) {
                val replacement = simplifyInstruction(instruction) ?: continue
                if (replacement.type != instruction.type) continue

                instruction.replaceAllUsesWith(replacement)
                instruction.detachOperands()
                block.instructions.remove(instruction)
                changed = true
            }
        }
        return changed
    }

    private fun simplifyInstruction(instruction: Instruction): Value? =
        when (instruction) {
            is BinaryInst -> simplifyBinary(instruction)
            is CastInst -> simplifyCast(instruction)
            is ICmpInst -> simplifyICmp(instruction)
            is PhiNode -> simplifyPhi(instruction)
            else -> null
        }

    private fun simplifyBinary(inst: BinaryInst): Value? {
        val lhs = inst.lhs
        val rhs = inst.rhs
        val lhsConst = lhs as? IntConstant
        val rhsConst = rhs as? IntConstant

        if (lhsConst != null && rhsConst != null) {
            foldBinaryConstants(inst, lhsConst, rhsConst)?.let { return it }
        }

        return when (inst) {
            is AddInst -> when {
                rhsConst?.value == 0L -> lhs
                lhsConst?.value == 0L -> rhs
                else -> null
            }
            is SubInst -> when {
                rhsConst?.value == 0L -> lhs
                lhs == rhs -> zero(inst)
                else -> null
            }
            is MulInst -> when {
                rhsConst?.value == 1L -> lhs
                lhsConst?.value == 1L -> rhs
                rhsConst?.value == 0L -> rhsConst
                lhsConst?.value == 0L -> lhsConst
                else -> null
            }
            is SDivInst, is UDivInst -> when {
                rhsConst?.value == 1L -> lhs
                lhsConst?.value == 0L && rhsConst?.value != 0L -> lhsConst
                else -> null
            }
            is SRemInst, is URemInst -> when {
                rhsConst?.value == 1L -> zero(inst)
                lhsConst?.value == 0L && rhsConst?.value != 0L -> lhsConst
                else -> null
            }
            is AndInst -> when {
                rhsConst?.value == 0L -> rhsConst
                lhsConst?.value == 0L -> lhsConst
                rhsConst?.isAllOnesValue() == true -> lhs
                lhsConst?.isAllOnesValue() == true -> rhs
                lhs == rhs -> lhs
                else -> null
            }
            is OrInst -> when {
                rhsConst?.value == 0L -> lhs
                lhsConst?.value == 0L -> rhs
                rhsConst?.isAllOnesValue() == true -> rhsConst
                lhsConst?.isAllOnesValue() == true -> lhsConst
                lhs == rhs -> lhs
                else -> null
            }
            is XorInst -> when {
                rhsConst?.value == 0L -> lhs
                lhsConst?.value == 0L -> rhs
                lhs == rhs -> zero(inst)
                else -> null
            }
            is ShlInst, is LShrInst, is AShrInst -> when {
                rhsConst?.value == 0L -> lhs
                lhsConst?.value == 0L -> lhsConst
                else -> null
            }
            else -> null
        }
    }

    private fun foldBinaryConstants(inst: BinaryInst, lhs: IntConstant, rhs: IntConstant): IntConstant? {
        if (inst is SDivInst || inst is UDivInst || inst is SRemInst || inst is URemInst) {
            if (rhs.value == 0L) return null
        }

        val value = when (inst) {
            is AddInst -> lhs.value + rhs.value
            is SubInst -> lhs.value - rhs.value
            is MulInst -> lhs.value * rhs.value
            is SDivInst -> lhs.value / rhs.value
            is UDivInst -> divideUnsigned(lhs.value, rhs.value)
            is SRemInst -> lhs.value % rhs.value
            is URemInst -> remainderUnsigned(lhs.value, rhs.value)
            is AndInst -> lhs.value and rhs.value
            is OrInst -> lhs.value or rhs.value
            is XorInst -> lhs.value xor rhs.value
            is ShlInst -> lhs.value shl shiftAmount(rhs, inst)
            is LShrInst -> lhs.value ushr shiftAmount(rhs, inst)
            is AShrInst -> lhs.value shr shiftAmount(rhs, inst)
            else -> return null
        }
        return intConstant(value, inst.type as? IntegerType ?: return null)
    }

    private fun shiftAmount(value: IntConstant, inst: BinaryInst): Int {
        val width = (inst.type as? IntegerType)?.bitWidth ?: Long.SIZE_BITS
        return (value.value and (width - 1).toLong()).toInt()
    }

    private fun simplifyCast(inst: CastInst): Value? {
        val source = inst.value
        val constant = source as? IntConstant ?: return null
        val destType = inst.type as? IntegerType ?: return null
        val srcType = source.type

        val folded = when (inst) {
            is TruncInst -> truncate(constant.value, destType.bitWidth)
            is ZExtInst -> zeroExtend(constant.value, srcType.bitWidth)
            is SExtInst -> signExtend(constant.value, srcType.bitWidth)
            else -> return null
        }
        return intConstant(folded, destType)
    }

    private fun simplifyICmp(inst: ICmpInst): Value? {
        if (inst.lhs == inst.rhs) {
            return boolConstant(
                when (inst.predicate) {
                    IcmpPredicate.EQ, IcmpPredicate.UGE, IcmpPredicate.ULE, IcmpPredicate.SGE, IcmpPredicate.SLE -> true
                    IcmpPredicate.NE, IcmpPredicate.UGT, IcmpPredicate.ULT, IcmpPredicate.SGT, IcmpPredicate.SLT -> false
                }
            )
        }

        val lhs = inst.lhs as? IntConstant ?: return null
        val rhs = inst.rhs as? IntConstant ?: return null
        return boolConstant(
            when (inst.predicate) {
                IcmpPredicate.EQ -> lhs.value == rhs.value
                IcmpPredicate.NE -> lhs.value != rhs.value
                IcmpPredicate.UGT -> java.lang.Long.compareUnsigned(lhs.value, rhs.value) > 0
                IcmpPredicate.UGE -> java.lang.Long.compareUnsigned(lhs.value, rhs.value) >= 0
                IcmpPredicate.ULT -> java.lang.Long.compareUnsigned(lhs.value, rhs.value) < 0
                IcmpPredicate.ULE -> java.lang.Long.compareUnsigned(lhs.value, rhs.value) <= 0
                IcmpPredicate.SGT -> lhs.value > rhs.value
                IcmpPredicate.SGE -> lhs.value >= rhs.value
                IcmpPredicate.SLT -> lhs.value < rhs.value
                IcmpPredicate.SLE -> lhs.value <= rhs.value
            }
        )
    }

    private fun simplifyPhi(phi: PhiNode): Value? {
        val incoming = phi.incomingValues
        if (incoming.isEmpty()) return null

        val firstNonSelf = incoming.map { it.first }.firstOrNull { it != phi } ?: return null
        return if (incoming.all { (value, _) -> value == firstNonSelf || value == phi }) {
            firstNonSelf
        } else {
            null
        }
    }

    private fun eliminateDeadInstructions(function: Function): Boolean {
        var changed = false
        var removedInIteration: Boolean

        do {
            removedInIteration = false
            for (block in function.basicBlocks.asReversed()) {
                val iterator = block.instructions.listIterator(block.instructions.size)
                while (iterator.hasPrevious()) {
                    val instruction = iterator.previous()
                    if (instruction.hasUses() || !instruction.isDeadRemovable()) continue
                    instruction.detachOperands()
                    iterator.remove()
                    removedInIteration = true
                    changed = true
                }
            }
        } while (removedInIteration)

        return changed
    }

    private fun Instruction.isDeadRemovable(): Boolean =
        when (this) {
            is TerminatorInst -> false
            is MemoryInst -> this is GetElementPtrInst && !mayReadFromMemory() && !mayWriteToMemory()
            is OtherInst -> isPure()
            is BinaryInst -> true
            is CastInst -> true
            else -> false
        }

    private fun zero(value: Value): IntConstant? {
        val type = value.type as? IntegerType ?: return null
        return intConstant(0L, type)
    }

    private fun boolConstant(value: Boolean): IntConstant =
        IntConstant(if (value) 1L else 0L, IntegerType(1))

    private fun intConstant(value: Long, type: IntegerType): IntConstant =
        IntConstant(truncate(value, type.bitWidth), type)

    private fun truncate(value: Long, bitWidth: Int): Long {
        if (bitWidth >= Long.SIZE_BITS) return value
        val mask = (1L shl bitWidth) - 1L
        return value and mask
    }

    private fun zeroExtend(value: Long, sourceBitWidth: Int): Long =
        truncate(value, sourceBitWidth)

    private fun signExtend(value: Long, sourceBitWidth: Int): Long {
        if (sourceBitWidth >= Long.SIZE_BITS) return value
        val mask = (1L shl sourceBitWidth) - 1L
        val signBit = 1L shl (sourceBitWidth - 1)
        val truncated = value and mask
        return if ((truncated and signBit) != 0L) truncated or mask.inv() else truncated
    }

    private fun User.detachOperands() {
        for (index in 0 until getNumOperands()) {
            ValueUseRegistry.unregisterUse(getOperand(index), this)
        }
    }

}
