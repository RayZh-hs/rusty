package space.norb.llvm.values.constants

import space.norb.llvm.core.Constant
import space.norb.llvm.types.IntegerType

/**
 * Constant integer value in LLVM IR.
 */
data class IntConstant(
    val value: Long,
    override val type: IntegerType,
    val isUnsigned: Boolean = false,
    override val name: String = value.toString()
) : Constant(name, type) {
    
    /**
     * Checks if this integer constant is null.
     * In LLVM, integer constants are never null, only pointer constants can be null.
     *
     * @return false for integer constants
     */
    override fun isNullValue(): Boolean = false
    
    /**
     * Checks if this integer constant is zero.
     *
     * @return true if value is 0, false otherwise
     */
    override fun isZeroValue(): Boolean = value == 0L
    
    /**
     * Checks if this integer constant is one.
     *
     * @return true if value is 1, false otherwise
     */
    override fun isOneValue(): Boolean = value == 1L
    
    /**
     * Checks if this integer constant has all bits set to 1.
     * For an N-bit integer, this checks if the value equals 2^N - 1.
     *
     * @return true if all bits are 1, false otherwise
     */
    override fun isAllOnesValue(): Boolean {
        val allOnes = (1L shl type.bitWidth) - 1
        return value == allOnes
    }
    
    /**
     * Checks if this integer constant is negative one.
     * For signed integers, this is represented as all bits set to 1.
     *
     * @return true if value is -1, false otherwise
     */
    override fun isNegativeOneValue(): Boolean = value == -1L
    
    /**
     * Checks if this integer constant is the minimum value for its type.
     * For an N-bit signed integer, this is -2^(N-1).
     *
     * @return true if value is the minimum for the type, false otherwise
     */
    override fun isMinValue(): Boolean {
        val minValue = -(1L shl (type.bitWidth - 1))
        return value == minValue
    }
    
    /**
     * Checks if this integer constant is the maximum value for its type.
     * For an N-bit signed integer, this is 2^(N-1) - 1.
     *
     * @return true if value is the maximum for the type, false otherwise
     */
    override fun isMaxValue(): Boolean {
        val maxValue = (1L shl (type.bitWidth - 1)) - 1
        return value == maxValue
    }
    
    /**
     * Generates LLVM IR representation of this integer constant.
     *
     * @return LLVM IR string representation
     */
    override fun toString(): String = "IntConstant(value=$value, type=$type)"
}