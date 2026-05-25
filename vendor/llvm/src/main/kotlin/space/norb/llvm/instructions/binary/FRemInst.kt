package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Floating-point remainder instruction.
 *
 * Performs floating-point remainder operation.
 *
 * LLVM IR syntax: result = frem <ty> op1, op2
 *
 * Properties:
 * - Not Commutative
 * - Not Associative
 * - Supports floating-point types
 *
 * Example:
 * %result = frem float %a, %b
 */
class FRemInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFRemInst(this)
    
    override fun getOpcodeName(): String = "frem"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): FRemInst {
            if (!lhs.type.isFloatingPointType()) {
                throw IllegalArgumentException("FRemInst only supports floating-point types, got ${lhs.type}")
            }
            return FRemInst(name, lhs.type, lhs, rhs)
        }
    }
}
