package space.norb.llvm.core

import space.norb.llvm.visitors.IRVisitor

/**
 * Abstract class for constant values in LLVM IR.
 *
 * Constants are immutable values that can be used as operands in instructions.
 * All constant values inherit from this class, providing a common interface
 * for different types of constants (integers, floats, pointers, etc.).
 *
 * Constants have the following properties:
 * - They are immutable and cannot be modified after creation
 * - They can be used as operands to instructions
 * - They have a specific type that determines what operations they support
 * - They may have a name for identification (though many constants are unnamed)
 *
 * This abstract class defines the contract that all constant implementations must follow.
 * Concrete implementations include:
 * - IntConstant for integer constants
 * - FloatConstant for floating-point constants
 * - NullPointerConstant for null pointer values
 * - ArrayConstant for array constants
 * - StructConstant for struct constants
 *
 * @param name The name of this constant (`null` for unnamed constants)
 * @param type The LLVM type of this constant
 */
abstract class Constant(
    override val name: String?,
    override val type: Type
) : Value {
    
    /**
     * Accepts a visitor for this constant.
     * This is part of the visitor pattern implementation for IR traversal.
     *
     * @param visitor The visitor to accept
     * @param T The return type of the visitor
     * @return Result of visiting this constant
     */
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitConstant(this)
    
    /**
     * Checks if this constant can be used as a null pointer value.
     *
     * @return true if this constant represents a null pointer, false otherwise
     */
    open fun isNullValue(): Boolean = false
    
    /**
     * Checks if this constant represents the value zero for its type.
     * This is different from isNullValue() as it applies to non-pointer types.
     *
     * @return true if this constant is zero, false otherwise
     */
    open fun isZeroValue(): Boolean = false
    
    /**
     * Checks if this constant is the all-ones value for its type.
     * This is primarily used for integer types.
     *
     * @return true if this constant is all ones, false otherwise
     */
    open fun isAllOnesValue(): Boolean = false
    
    /**
     * Checks if this constant is one.
     * This is primarily used for integer types.
     *
     * @return true if this constant is one, false otherwise
     */
    open fun isOneValue(): Boolean = false
    
    /**
     * Checks if this constant is negative one.
     * This is primarily used for integer types.
     *
     * @return true if this constant is negative one, false otherwise
     */
    open fun isNegativeOneValue(): Boolean = false
    
    /**
     * Checks if this constant is the minimum value for its type.
     * For integers, this would be the most negative value.
     * For floating-point, this would be the most negative finite value.
     *
     * @return true if this constant is the minimum value, false otherwise
     */
    open fun isMinValue(): Boolean = false
    
    /**
     * Checks if this constant is the maximum value for its type.
     *
     * @return true if this constant is the maximum value, false otherwise
     */
    open fun isMaxValue(): Boolean = false
    
    /**
     * Gets the unique identifier for this constant.
     * In LLVM IR, constants typically don't have names unless explicitly specified.
     *
     * @return Unique identifier string
     */
    override fun getIdentifier(): String {
        return name ?: "const_${hashCode()}"
    }
    
    // Placeholder methods for Phase 1 - to be implemented in later phases
    
    /**
     * Checks if this constant can be converted to the specified type.
     * TODO: Deferred - requires type conversion framework.
     * Dependencies: Type compatibility checking, conversion rules engine,
     * and constant factory methods for target types.
     *
     * @param targetType The type to check conversion to
     * @return true if conversion is possible, false otherwise
     */
    open fun canConvertTo(targetType: Type): Boolean {
        // Phase 1: Only support identical types (no conversion needed)
        return this.type == targetType
    }
    
    /**
     * Checks if this constant can be losslessly converted to the specified type.
     * TODO: Deferred - requires type conversion framework.
     * Dependencies: Type compatibility checking, conversion rules engine,
     * and precision loss analysis algorithms.
     *
     * @param targetType The type to check conversion to
     * @return true if conversion is possible without loss, false otherwise
     */
    open fun canLosslesslyConvertTo(targetType: Type): Boolean {
        // Same type is always losslessly convertible
        if (this.type == targetType) {
            return true
        }
        
        // Integer to integer conversions
        if (this.type.isIntegerType() && targetType.isIntegerType()) {
            val sourceBits = this.type.getPrimitiveSizeInBits() ?: return false
            val targetBits = targetType.getPrimitiveSizeInBits() ?: return false
            
            // Can losslessly convert if target has same or more bits
            return targetBits >= sourceBits
        }
        
        // Float to float conversions
        if (this.type.isFloatingPointType() && targetType.isFloatingPointType()) {
            val sourceBits = this.type.getPrimitiveSizeInBits() ?: return false
            val targetBits = targetType.getPrimitiveSizeInBits() ?: return false
            
            // Can losslessly convert if target has same or more bits
            return targetBits >= sourceBits
        }
        
        // Integer to float conversions (may lose precision for large integers)
        if (this.type.isIntegerType() && targetType.isFloatingPointType()) {
            // For simplicity, assume potential loss of precision
            return false
        }
        
        // Float to integer conversions (always lose fractional part)
        if (this.type.isFloatingPointType() && targetType.isIntegerType()) {
            return false
        }
        
        // Pointer to integer and vice versa (implementation-specific)
        if (this.type.isPointerType() && targetType.isIntegerType()) {
            val targetBits = targetType.getPrimitiveSizeInBits() ?: return false
            // Assume pointer size is 64 bits for now
            return targetBits >= 64
        }
        
        if (this.type.isIntegerType() && targetType.isPointerType()) {
            val sourceBits = this.type.getPrimitiveSizeInBits() ?: return false
            // Assume pointer size is 64 bits for now
            return sourceBits >= 64
        }
        
        return false
    }
    
    /**
     * Attempts to convert this constant to the specified type.
     * TODO: Deferred - requires type conversion framework.
     * Dependencies: Type compatibility checking, conversion rules engine,
     * and constant factory methods for target types.
     *
     * @param targetType The type to convert to
     * @return A new constant of the target type, or null if conversion is not possible
     */
    open fun convertTo(targetType: Type): Constant? {
        // Phase 1: Only support identical types (no conversion needed)
        return if (this.type == targetType) {
            this
        } else {
            null
        }
    }
    
    /**
     * Gets the bit representation of this constant.
     * TODO: Deferred - requires bit utilities framework.
     * Dependencies: Bit-level serialization, endianness handling,
     * and type-specific bit layout algorithms.
     *
     * @return The bit representation as a string, or null if not applicable
     */
    open fun getBitRepresentation(): String? {
        // Phase 1: Bit-level operations not supported yet
        return null
    }
    
    /**
     * Checks if this constant has the same value as another constant.
     * This is different from equals() as it only compares the actual values,
     * not the names or other metadata.
     *
     * @param other The other constant to compare with
     * @return true if the constants have the same value, false otherwise
     */
    open fun hasSameValueAs(other: Constant): Boolean {
        // If types are different, values can't be the same
        if (this.type != other.type) {
            return false
        }
        
        // Base implementation - concrete classes should override for efficiency
        // This provides a generic approach using string representation
        return this.toString() == other.toString()
    }
}
