package space.norb.llvm.instructions.base

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.visitors.IRVisitor

/**
 * Abstract base class for all binary instructions.
 *
 * Binary instructions perform operations on two operands and produce a result.
 * These instructions operate on integer, floating-point, or vector types.
 *
 * Examples of binary instructions include:
 * - Arithmetic: AddInst, SubInst, MulInst, SDivInst, UDivInst, FDivInst, etc.
 * - Logical: AndInst, OrInst, XorInst
 * - Comparison operations (though some may be in OtherInst category)
 *
 * All binary instructions follow the pattern:
 * result = opcode lhs, rhs
 */
abstract class BinaryInst(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : Instruction(name, type, listOf(lhs, rhs)) {
    val lhs: Value
        get() = getOperand(0)

    val rhs: Value
        get() = getOperand(1)

    
    init {
        // Validate that both operands have compatible types
        if (lhs.type != rhs.type) {
            throw IllegalArgumentException("Binary instruction operands must have the same type: ${lhs.type} vs ${rhs.type}")
        }
        
        // Validate that the result type is compatible with operand types
        if (type != lhs.type) {
            throw IllegalArgumentException("Binary instruction result type must match operand types: $type vs ${lhs.type}")
        }
    }
    
    /**
     * Accept method for the visitor pattern.
     * All binary instructions must implement this to support
     * the visitor pattern for IR traversal and processing.
     */
    abstract override fun <T> accept(visitor: IRVisitor<T>): T
    
    /**
     * Gets the opcode name for this binary instruction.
     * This is used for IR printing and debugging.
     */
    abstract fun getOpcodeName(): String
    
    /**
     * Checks if this binary instruction is commutative (i.e., lhs op rhs == rhs op lhs).
     * This is useful for optimization passes.
     */
    open fun isCommutative(): Boolean = false
    
    /**
     * Checks if this binary instruction is associative.
     * This is useful for optimization passes.
     */
    open fun isAssociative(): Boolean = false
}
