package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Floating-point division instruction.
 *
 * Performs floating-point division: result = lhs / rhs
 *
 * LLVM IR syntax: result = fdiv <ty> op1, op2
 *
 * Properties:
 * - Not Commutative
 * - Not Associative
 * - Supports floating-point types
 *
 * Example:
 * %result = fdiv float %a, %b
 */
class FDivInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFDivInst(this)
    
    override fun getOpcodeName(): String = "fdiv"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): FDivInst {
            if (!lhs.type.isFloatingPointType()) {
                throw IllegalArgumentException("FDivInst only supports floating-point types, got ${lhs.type}")
            }
            return FDivInst(name, lhs.type, lhs, rhs)
        }
    }
}
