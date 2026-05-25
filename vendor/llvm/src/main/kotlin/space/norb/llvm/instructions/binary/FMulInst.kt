package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Floating-point multiplication instruction.
 *
 * Performs floating-point multiplication: result = lhs * rhs
 *
 * LLVM IR syntax: result = fmul <ty> op1, op2
 *
 * Properties:
 * - Commutative
 * - Not Associative
 * - Supports floating-point types
 *
 * Example:
 * %result = fmul float %a, %b
 */
class FMulInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFMulInst(this)
    
    override fun getOpcodeName(): String = "fmul"
    
    override fun isCommutative(): Boolean = true
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): FMulInst {
            if (!lhs.type.isFloatingPointType()) {
                throw IllegalArgumentException("FMulInst only supports floating-point types, got ${lhs.type}")
            }
            return FMulInst(name, lhs.type, lhs, rhs)
        }
    }
}
