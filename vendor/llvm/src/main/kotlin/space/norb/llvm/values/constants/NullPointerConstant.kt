package space.norb.llvm.values.constants

import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.types.PointerType

/**
 * Null pointer constant in LLVM IR.
 *
 * This implementation uses un-typed pointers in compliance with the latest LLVM IR standard.
 * All pointers are of a single type (similar to `void*` in C).
 *
 * IR output examples:
 * ```
 * ptr null         ; Null pointer for un-typed pointer type
 * ptr null         ; Same null pointer for all pointer types
 * ptr null         ; Same null pointer for all pointer types
 * ```
 *
 * Key characteristics:
 * - Creates un-typed null pointers that work with any pointer type
 * - Type information is preserved through context
 * - LLVM IR compliant output
 */
class NullPointerConstant private constructor(
    override val type: Type,
    val elementType: Type? = null
) : Constant("null", type) {
    
    /**
     * Checks if this null pointer constant represents a null value.
     * Always returns true for NullPointerConstant instances.
     *
     * @return true
     */
    override fun isNullValue(): Boolean = true
    
    /**
     * Checks if this constant represents the value zero for its type.
     * For null pointers, this returns false as null is conceptually different from zero.
     *
     * @return false for null pointers
     */
    override fun isZeroValue(): Boolean = false
    
    /**
     * Checks if this constant is one.
     * For null pointers, this always returns false.
     *
     * @return false for null pointers
     */
    override fun isOneValue(): Boolean = false
    
    /**
     * Checks if this constant is the all-ones value for its type.
     * For null pointers, this always returns false.
     *
     * @return false for null pointers
     */
    override fun isAllOnesValue(): Boolean = false
    
    /**
     * Checks if this constant is negative one.
     * For null pointers, this always returns false.
     *
     * @return false for null pointers
     */
    override fun isNegativeOneValue(): Boolean = false
    
    /**
     * Checks if this constant is the minimum value for its type.
     * For null pointers, this always returns false.
     *
     * @return false for null pointers
     */
    override fun isMinValue(): Boolean = false
    
    /**
     * Checks if this constant is the maximum value for its type.
     * For null pointers, this always returns false.
     *
     * @return false for null pointers
     */
    override fun isMaxValue(): Boolean = false
    
    /**
     * Returns the string representation of this null pointer constant in LLVM IR format.
     * Generates valid LLVM IR for null pointers.
     *
     * @return LLVM IR representation of the null pointer
     */
    override fun toString(): String = "${type.toString()} null"
    
    /**
     * Gets the bit representation of this null pointer constant.
     * For null pointers, this returns all zeros.
     *
     * @return String of zeros representing the null pointer bits
     */
    override fun getBitRepresentation(): String? {
        val bits = type.getPrimitiveSizeInBits() ?: 64
        return "0".repeat(bits)
    }
    
    /**
     * Checks if this constant has the same value as another constant.
     * For null pointers, this returns true if the other constant is also a null pointer.
     *
     * @param other The other constant to compare with
     * @return true if both constants are null pointers, false otherwise
     */
    override fun hasSameValueAs(other: Constant): Boolean {
        return other is NullPointerConstant
    }
    
    /**
     * Checks if this NullPointerConstant is equal to another object.
     * All NullPointerConstant instances are equal regardless of their element type.
     *
     * @param other The object to compare with
     * @return true if the other object is also a NullPointerConstant, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        return other is NullPointerConstant
    }
    
    /**
     * Returns the hash code for this NullPointerConstant.
     * All NullPointerConstant instances have the same hash code.
     *
     * @return The hash code for NullPointerConstant
     */
    override fun hashCode(): Int {
        return "NullPointerConstant".hashCode()
    }
    
    companion object {
        /**
         * Creates a null pointer constant for the specified element type.
         *
         * This method creates an un-typed null pointer but preserves the element type
         * information for context and type checking purposes.
         *
         * @param elementType The element type this pointer points to
         * @return A null pointer constant of the un-typed pointer type
         */
        fun create(elementType: Type): NullPointerConstant {
            // Create un-typed null pointer but preserve element type context
            // In the un-typed pointer model, all pointers are of type PointerType
            // but we keep track of the element type for context
            return NullPointerConstant(PointerType, elementType)
        }
        
        /**
         * Creates a null pointer constant with the specified pointer type.
         *
         * This method creates a null pointer with the exact pointer type provided.
         *
         * @param pointerType The exact pointer type to use
         * @return A null pointer constant with the specified type
         */
        fun createWithPointerType(pointerType: Type): NullPointerConstant {
            require(pointerType.isPointerType()) { "Type must be a pointer type" }
            return NullPointerConstant(pointerType, null)
        }
        
        /**
         * Creates an un-typed null pointer constant.
         *
         * This method creates an un-typed null pointer.
         *
         * @return An un-typed null pointer constant
         */
        fun createUntyped(): NullPointerConstant {
            return NullPointerConstant(PointerType, null)
        }
    }
}