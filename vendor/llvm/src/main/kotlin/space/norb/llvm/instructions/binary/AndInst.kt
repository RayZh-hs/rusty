package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Bitwise AND instruction.
 *
 * Performs bitwise AND operation: result = lhs & rhs
 *
 * LLVM IR syntax: result = and <ty> op1, op2
 *
 * Properties:
 * - Commutative: lhs & rhs == rhs & lhs
 * - Associative: (a & b) & c == a & (b & c)
 * - Supports integer types and vectors of integers
 * - Commonly used for masking operations
 *
 * Example:
 * %result = and i32 %a, %b  ; Integer bitwise AND
 * %result = and <4 x i32> %a, %b ; Vector bitwise AND
 * %masked = and i32 %value, 255  ; Mask to get low 8 bits
 */
class AndInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitAndInst(this)
    
    override fun getOpcodeName(): String = "and"
    
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
         * Creates a new AndInst with proper validation.
         *
         * @param name The name of the instruction result
         * @param type The result type (must match operand types)
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new AndInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, type: Type, lhs: Value, rhs: Value): AndInst {
            return AndInst(name, type, lhs, rhs)
        }
        
        /**
         * Creates a new AndInst with inferred type from operands.
         *
         * @param name The name of the instruction result
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new AndInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, lhs: Value, rhs: Value): AndInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("Operand types must match: ${lhs.type} vs ${rhs.type}")
            }
            return AndInst(name, lhs.type, lhs, rhs)
        }
    }
}