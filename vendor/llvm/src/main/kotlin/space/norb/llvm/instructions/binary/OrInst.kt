package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Bitwise OR instruction.
 *
 * Performs bitwise OR operation: result = lhs | rhs
 *
 * LLVM IR syntax: result = or <ty> op1, op2
 *
 * Properties:
 * - Commutative: lhs | rhs == rhs | lhs
 * - Associative: (a | b) | c == a | (b | c)
 * - Supports integer types and vectors of integers
 * - Commonly used for setting bits and combining flags
 *
 * Example:
 * %result = or i32 %a, %b  ; Integer bitwise OR
 * %result = or <4 x i32> %a, %b ; Vector bitwise OR
 * %with_flag = or i32 %value, 1  ; Set the lowest bit
 */
class OrInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitOrInst(this)
    
    override fun getOpcodeName(): String = "or"
    
    override fun isCommutative(): Boolean = true
    
    override fun isAssociative(): Boolean = true
    
    /**
     * Gets the left operand.
     */
    fun getLeftOperand(): Value = lhs
    
    /**
     * Gets the right operand.
     */
    fun getRightOperand(): Value = rhs
    
    companion object {
        /**
         * Creates a new OrInst with proper validation.
         *
         * @param name The name of the instruction result
         * @param type The result type (must match operand types)
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new OrInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, type: Type, lhs: Value, rhs: Value): OrInst {
            return OrInst(name, type, lhs, rhs)
        }
        
        /**
         * Creates a new OrInst with inferred type from operands.
         *
         * @param name The name of the instruction result
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new OrInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, lhs: Value, rhs: Value): OrInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("Operand types must match: ${lhs.type} vs ${rhs.type}")
            }
            return OrInst(name, lhs.type, lhs, rhs)
        }
    }
}