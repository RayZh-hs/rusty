package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Floating-point subtraction instruction.
 *
 * Performs floating-point subtraction: result = lhs - rhs
 *
 * LLVM IR syntax: result = fsub <ty> op1, op2
 *
 * Properties:
 * - Not Commutative
 * - Not Associative
 * - Supports floating-point types
 *
 * Example:
 * %result = fsub float %a, %b
 */
class FSubInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFSubInst(this)
    
    override fun getOpcodeName(): String = "fsub"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): FSubInst {
            if (!lhs.type.isFloatingPointType()) {
                throw IllegalArgumentException("FSubInst only supports floating-point types, got ${lhs.type}")
            }
            return FSubInst(name, lhs.type, lhs, rhs)
        }
    }
}
