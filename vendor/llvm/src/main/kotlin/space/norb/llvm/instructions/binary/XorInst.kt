package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Bitwise XOR instruction.
 *
 * Performs bitwise exclusive OR operation: result = lhs ^ rhs
 *
 * LLVM IR syntax: result = xor <ty> op1, op2
 *
 * Properties:
 * - Commutative: lhs ^ rhs == rhs ^ lhs
 * - Associative: (a ^ b) ^ c == a ^ (b ^ c)
 * - Supports integer types and vectors of integers
 * - Commonly used for toggling bits and parity operations
 * - XOR with -1 produces bitwise complement
 *
 * Example:
 * %result = xor i32 %a, %b  ; Integer bitwise XOR
 * %result = xor <4 x i32> %a, %b ; Vector bitwise XOR
 * %flipped = xor i32 %value, -1  ; Bitwise complement
 * %toggled = xor i32 %flags, 1  ; Toggle the lowest bit
 */
class XorInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitXorInst(this)
    
    override fun getOpcodeName(): String = "xor"
    
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
         * Creates a new XorInst with proper validation.
         *
         * @param name The name of the instruction result
         * @param type The result type (must match operand types)
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new XorInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, type: Type, lhs: Value, rhs: Value): XorInst {
            return XorInst(name, type, lhs, rhs)
        }
        
        /**
         * Creates a new XorInst with inferred type from operands.
         *
         * @param name The name of the instruction result
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new XorInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, lhs: Value, rhs: Value): XorInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("Operand types must match: ${lhs.type} vs ${rhs.type}")
            }
            return XorInst(name, lhs.type, lhs, rhs)
        }
    }
}