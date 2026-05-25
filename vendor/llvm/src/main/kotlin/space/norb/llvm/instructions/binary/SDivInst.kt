package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Signed division instruction.
 *
 * Performs signed integer division: result = lhs / rhs
 *
 * LLVM IR syntax: result = sdiv <ty> op1, op2
 *
 * Properties:
 * - Not commutative: lhs / rhs != rhs / lhs
 * - Not associative: (a / b) / c != a / (b / c)
 * - Only supports integer types (signed)
 * - Supports vector operations
 * - Division by zero is undefined behavior
 *
 * Example:
 * %result = sdiv i32 %a, %b  ; Signed integer division
 * %result = sdiv <4 x i32> %a, %b ; Vector signed division
 */
class SDivInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitSDivInst(this)
    
    override fun getOpcodeName(): String = "sdiv"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    /**
     * Gets the dividend (numerator).
     */
    fun getDividend(): Value = lhs
    
    /**
     * Gets the divisor (denominator).
     */
    fun getDivisor(): Value = rhs
    
    /**
     * Gets the left operand (dividend).
     */
    fun getLeftOperand(): Value = lhs
    
    /**
     * Gets the right operand (divisor).
     */
    fun getRightOperand(): Value = rhs
    
    companion object {
        /**
         * Creates a new SDivInst with proper validation.
         *
         * @param name The name of the instruction result
         * @param type The result type (must match operand types)
         * @param lhs The dividend (numerator)
         * @param rhs The divisor (denominator)
         * @return A new SDivInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, type: Type, lhs: Value, rhs: Value): SDivInst {
            return SDivInst(name, type, lhs, rhs)
        }
        
        /**
         * Creates a new SDivInst with inferred type from operands.
         *
         * @param name The name of the instruction result
         * @param lhs The dividend (numerator)
         * @param rhs The divisor (denominator)
         * @return A new SDivInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, lhs: Value, rhs: Value): SDivInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("Operand types must match: ${lhs.type} vs ${rhs.type}")
            }
            return SDivInst(name, lhs.type, lhs, rhs)
        }
    }
}