package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Subtraction instruction.
 *
 * Performs integer or floating-point subtraction: result = lhs - rhs
 *
 * LLVM IR syntax: result = sub <ty> op1, op2
 *
 * Properties:
 * - Not commutative: lhs - rhs != rhs - lhs
 * - Not associative: (a - b) - c != a - (b - c)
 * - Supports integer and floating-point types
 * - Supports vector operations
 *
 * Example:
 * %result = sub i32 %a, %b  ; Integer subtraction
 * %result = sub float %x, %y ; Floating-point subtraction
 */
class SubInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitSubInst(this)
    
    override fun getOpcodeName(): String = "sub"
    
    override fun isCommutative(): Boolean = false
    
    override fun isAssociative(): Boolean = false
    
    /**
     * Gets the left operand (minuend).
     */
    fun getLeftOperand(): Value = lhs
    
    /**
     * Gets the right operand (subtrahend).
     */
    fun getRightOperand(): Value = rhs
    
    companion object {
        /**
         * Creates a new SubInst with proper validation.
         *
         * @param name The name of the instruction result
         * @param type The result type (must match operand types)
         * @param lhs The left operand (minuend)
         * @param rhs The right operand (subtrahend)
         * @return A new SubInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, type: Type, lhs: Value, rhs: Value): SubInst {
            return SubInst(name, type, lhs, rhs)
        }
        
        /**
         * Creates a new SubInf with inferred type from operands.
         *
         * @param name The name of the instruction result
         * @param lhs The left operand (minuend)
         * @param rhs The right operand (subtrahend)
         * @return A new SubInst instance
         * @throws IllegalArgumentException if operand types are incompatible
         */
        fun create(name: String?, lhs: Value, rhs: Value): SubInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("Operand types must match: ${lhs.type} vs ${rhs.type}")
            }
            return SubInst(name, lhs.type, lhs, rhs)
        }
    }
}