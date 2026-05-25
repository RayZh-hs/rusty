package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Unsigned division instruction.
 *
 * Performs unsigned integer division: result = lhs / rhs
 *
 * LLVM IR syntax: result = udiv <ty> op1, op2
 *
 * Properties:
 * - Not commutative: lhs / rhs != rhs / lhs
 * - Not associative: (a / b) / c != a / (b / c)
 * - Only supports integer types (unsigned)
 * - Supports vector operations
 * - Division by zero is undefined behavior
 *
 * Example:
 * %result = udiv i32 %a, %b  ; Unsigned integer division
 * %result = udiv <4 x i32> %a, %b ; Vector unsigned division
 */
class UDivInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitUDivInst(this)
    
    override fun getOpcodeName(): String = "udiv"
    
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
    
    companion object {
        /**
         * Creates a new UDivInst with proper validation.
         *
         * @param name The name of the result variable
         * @param lhs The dividend (numerator)
         * @param rhs The divisor (denominator)
         * @return A new UDivInst instance
         * @throws IllegalArgumentException if types don't match or aren't integers
         */
        fun create(name: String?, lhs: Value, rhs: Value): UDivInst {
            // Type checking is handled by BinaryInst base class
            // Additional check for integer types could be added here
            if (!lhs.type.isIntegerType()) {
                throw IllegalArgumentException("UDivInst only supports integer types, got ${lhs.type}")
            }
            
            return UDivInst(name, lhs.type, lhs, rhs)
        }
    }
}
