package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Arithmetic Shift Right instruction.
 *
 * Performs arithmetic shift right operation: result = lhs >> rhs
 * The high-order bits are filled with the sign bit of the first operand.
 *
 * LLVM IR syntax: result = ashr <ty> op1, op2
 *
 * Properties:
 * - Not Commutative
 * - Not Associative
 * - Supports integer types and vectors of integers
 *
 * Example:
 * %result = ashr i32 %a, %b  ; Arithmetic shift right
 * %result = ashr <4 x i32> %a, %b ; Vector arithmetic shift right
 */
class AShrInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitAShrInst(this)
    
    override fun getOpcodeName(): String = "ashr"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): AShrInst {
            return AShrInst(name, lhs.type, lhs, rhs)
        }
    }
}
