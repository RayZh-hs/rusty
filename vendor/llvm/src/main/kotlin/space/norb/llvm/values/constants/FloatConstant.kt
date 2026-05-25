package space.norb.llvm.values.constants

import space.norb.llvm.core.Constant
import space.norb.llvm.types.FloatingPointType
import kotlin.math.abs

/**
 * Constant floating-point value in LLVM IR.
 */
data class FloatConstant(
    val value: Double,
    override val type: FloatingPointType,
    override val name: String = value.toString()
) : Constant(name, type) {
    
    /**
     * Checks if this floating-point constant is null.
     * In LLVM, floating-point constants are never null, only pointer constants can be null.
     *
     * @return false for floating-point constants
     */
    override fun isNullValue(): Boolean = false
    
    /**
     * Checks if this floating-point constant is zero.
     * This checks for both +0.0 and -0.0.
     *
     * @return true if value is +0.0 or -0.0, false otherwise
     */
    override fun isZeroValue(): Boolean = value == 0.0
    
    /**
     * Checks if this floating-point constant is one.
     *
     * @return true if value is 1.0, false otherwise
     */
    override fun isOneValue(): Boolean = value == 1.0
    
    /**
     * Checks if this floating-point constant has all bits set to 1.
     * For floating-point types, this represents NaN (Not a Number).
     *
     * @return true if all bits are 1 (NaN), false otherwise
     */
    override fun isAllOnesValue(): Boolean = value.isNaN()
    
    /**
     * Checks if this floating-point constant is negative one.
     *
     * @return true if value is -1.0, false otherwise
     */
    override fun isNegativeOneValue(): Boolean = value == -1.0
    
    /**
     * Checks if this floating-point constant is the minimum value for its type.
     * For floating-point types, this is the most negative finite value.
     *
     * @return true if value is the minimum for the type, false otherwise
     */
    override fun isMinValue(): Boolean {
        return when (type) {
            is FloatingPointType.FloatType -> value == Float.MIN_VALUE.toDouble()
            is FloatingPointType.DoubleType -> value == Double.MIN_VALUE
        }
    }
    
    /**
     * Checks if this floating-point constant is the maximum value for its type.
     *
     * @return true if value is the maximum for the type, false otherwise
     */
    override fun isMaxValue(): Boolean {
        return when (type) {
            is FloatingPointType.FloatType -> value == Float.MAX_VALUE.toDouble()
            is FloatingPointType.DoubleType -> value == Double.MAX_VALUE
        }
    }
    
    /**
     * Generates LLVM IR representation of this floating-point constant.
     *
     * @return LLVM IR string representation
     */
    override fun toString(): String {
        // Handle special cases for floating-point values
        val valueStr = when {
            value.isNaN() -> "nan"
            value == Double.POSITIVE_INFINITY -> "inf"
            value == Double.NEGATIVE_INFINITY -> "-inf"
            else -> {
                // For regular values, format with appropriate precision
                if (type is FloatingPointType.FloatType) {
                    // For float type, use single precision format
                    "${value}f"
                } else {
                    // For double type, use double precision format
                    value.toString()
                }
            }
        }
        return "FloatConstant(value=$valueStr, type=$type)"
    }
}