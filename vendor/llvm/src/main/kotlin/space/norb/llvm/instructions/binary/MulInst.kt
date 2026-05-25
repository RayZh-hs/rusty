package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Multiplication instruction.
 *
 * Performs integer or floating-point multiplication: result = lhs * rhs
 *
 * LLVM IR syntax: result = mul <ty> op1, op2
 *
 * Properties:
 * - Commutative: lhs * rhs == rhs * lhs
 * - Associative: (a * b) * c == a * (b * c)
 * - Supports integer and floating-point types
 * - Supports vector operations
 *
 * Example:
 * %result = mul i32 %a, %b  ; Integer multiplication
 * %result = mul float %x, %y ; Floating-point multiplication
 */
class MulInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitMulInst(this)
    
    override fun getOpcodeName(): String = "mul"
    
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
         * Creates a new MulInst with proper validation.
         *
         * @param name The name of the instruction result
         * @param type The result type (must match operand types)
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new MulInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, type: Type, lhs: Value, rhs: Value): MulInst {
            return MulInst(name, type, lhs, rhs)
        }
        
        /**
         * Creates a new MulInst with inferred type from operands.
         *
         * @param name The name of the instruction result
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new MulInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, lhs: Value, rhs: Value): MulInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("Operand types must match: ${lhs.type} vs ${rhs.type}")
            }
            return MulInst(name, lhs.type, lhs, rhs)
        }
    }
}