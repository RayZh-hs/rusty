package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Shift Left instruction.
 *
 * Performs shift left operation: result = lhs << rhs
 * The low-order bits are filled with zero.
 *
 * LLVM IR syntax: result = shl <ty> op1, op2
 *
 * Properties:
 * - Not Commutative
 * - Not Associative
 * - Supports integer types and vectors of integers
 *
 * Example:
 * %result = shl i32 %a, %b  ; Shift left
 * %result = shl <4 x i32> %a, %b ; Vector shift left
 */
class ShlInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitShlInst(this)
    
    override fun getOpcodeName(): String = "shl"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): ShlInst {
            return ShlInst(name, lhs.type, lhs, rhs)
        }
    }
}
