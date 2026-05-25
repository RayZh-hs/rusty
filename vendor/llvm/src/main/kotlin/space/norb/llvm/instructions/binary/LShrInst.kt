package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Logical Shift Right instruction.
 *
 * Performs logical shift right operation: result = lhs >>> rhs
 * The high-order bits are filled with zero.
 *
 * LLVM IR syntax: result = lshr <ty> op1, op2
 *
 * Properties:
 * - Not Commutative
 * - Not Associative
 * - Supports integer types and vectors of integers
 *
 * Example:
 * %result = lshr i32 %a, %b  ; Logical shift right
 * %result = lshr <4 x i32> %a, %b ; Vector logical shift right
 */
class LShrInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitLShrInst(this)
    
    override fun getOpcodeName(): String = "lshr"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): LShrInst {
            return LShrInst(name, lhs.type, lhs, rhs)
        }
    }
}
