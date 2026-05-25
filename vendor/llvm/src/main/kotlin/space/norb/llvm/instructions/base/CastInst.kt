package space.norb.llvm.instructions.base

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.visitors.IRVisitor

/**
 * Abstract base class for all cast instructions.
 *
 * Cast instructions convert values from one type to another.
 * These instructions are used to change the representation or interpretation
 * of values while preserving their semantic meaning when appropriate.
 *
 * Examples of cast instructions include:
 * - TruncInst: truncates integer types to smaller widths
 * - ZExtInst: zero-extends integer types to larger widths
 * - SExtInst: sign-extends integer types to larger widths
 * - BitcastInst: bitwise cast between types of the same size
 * - IntToPtrInst: converts integer to pointer
 * - PtrToIntInst: converts pointer to integer
 * - FPToSIInst: converts floating-point to signed integer
 * - SIToFPInst: converts signed integer to floating-point
 * - FPToUIInst: converts floating-point to unsigned integer
 * - UIToFPInst: converts unsigned integer to floating-point
 * - FPTruncInst: truncates floating-point to smaller type
 * - FPExtInst: extends floating-point to larger type
 */
abstract class CastInst(
    name: String?,
    override val type: Type,
    value: Value
) : Instruction(name, type, listOf(value)) {
    val value: Value
        get() = getOperand(0)

    
    init {
        // Validate that we're not casting to same type, except for function-to-pointer casts
        if (value.type == type && !value.type.isFunctionType() && !type.isPointerType()) {
            throw IllegalArgumentException("Cast instruction must convert to a different type: ${value.type} -> $type")
        }
    }
    
    /**
     * Accept method for the visitor pattern.
     * All cast instructions must implement this to support
     * the visitor pattern for IR traversal and processing.
     */
    abstract override fun <T> accept(visitor: IRVisitor<T>): T
    
    /**
     * Gets the opcode name for this cast instruction.
     * This is used for IR printing and debugging.
     */
    abstract fun getOpcodeName(): String
    
    /**
     * Checks if this cast is a no-op (i.e., doesn't change the bit pattern).
     * This can happen with bitcast between identical types or
     * pointer-to-pointer casts in some contexts.
     */
    open fun isNoopCast(): Boolean = false
    
    /**
     * Checks if this cast may lose information (e.g., truncation, float to int).
     * This is useful for optimization and warning purposes.
     */
    abstract fun mayLoseInformation(): Boolean
    
    /**
     * Checks if this cast is between integer types.
     */
    open fun isIntegerCast(): Boolean =
        value.type is space.norb.llvm.types.IntegerType && type is space.norb.llvm.types.IntegerType
    
    /**
     * Checks if this cast is between floating-point types.
     */
    open fun isFloatingPointCast(): Boolean =
        value.type is space.norb.llvm.types.FloatingPointType && type is space.norb.llvm.types.FloatingPointType
    
    /**
     * Checks if this cast involves pointers (either source or destination is a pointer).
     */
    open fun isPointerCast(): Boolean =
        value.type is space.norb.llvm.types.PointerType || type is space.norb.llvm.types.PointerType
}
