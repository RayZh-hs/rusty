package space.norb.llvm.values.constants

import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.types.ArrayType

/**
 * Constant array value in LLVM IR.
 *
 * This implementation supports:
 * - Nested arrays (arrays of arrays)
 * - Strict type enforcement for all elements
 * - LLVM IR compliant output format: [i32 1, i32 2, i32 3]
 * - Integration with existing constant system
 *
 * @property elements The list of constant elements in the array
 * @property type The ArrayType representing this array's type
 * @property name Optional name for this constant (`null` when unnamed)
 */
data class ArrayConstant(
    val elements: List<Constant>,
    override val type: ArrayType,
    override val name: String? = null
) : Constant(name, type) {
    
    init {
        validateElements()
    }
    
    /**
     * Validates that all elements match the expected element type and count.
     */
    private fun validateElements() {
        if (elements.isEmpty()) {
            throw IllegalArgumentException("Array elements cannot be empty")
        }
        
        if (elements.size != type.numElements) {
            throw IllegalArgumentException(
                "Expected ${type.numElements} elements, got ${elements.size}"
            )
        }
        
        val expectedElementType = type.elementType
        elements.forEachIndexed { index, element ->
            if (element.type != expectedElementType) {
                throw IllegalArgumentException(
                    "Element at index $index has type ${element.type}, expected $expectedElementType"
                )
            }
        }
    }
    
    /**
     * Checks if this array constant represents a null value.
     * Arrays are never null in LLVM IR.
     *
     * @return false for array constants
     */
    override fun isNullValue(): Boolean = false
    
    /**
     * Checks if all elements in this array are zero.
     *
     * @return true if all elements are zero, false otherwise
     */
    override fun isZeroValue(): Boolean = elements.all { it.isZeroValue() }
    
    /**
     * Checks if this array constant represents value one.
     * Arrays are never considered "one" as a whole.
     *
     * @return false for array constants
     */
    override fun isOneValue(): Boolean = false
    
    /**
     * Checks if all elements in this array have all bits set to 1.
     *
     * @return true if all elements have all bits set to 1, false otherwise
     */
    override fun isAllOnesValue(): Boolean = elements.all { it.isAllOnesValue() }
    
    /**
     * Checks if this array constant represents negative one.
     * Arrays are never considered "negative one" as a whole.
     *
     * @return false for array constants
     */
    override fun isNegativeOneValue(): Boolean = false
    
    /**
     * Checks if this array constant represents the minimum value for its type.
     * Arrays don't have a single minimum value concept.
     *
     * @return false for array constants
     */
    override fun isMinValue(): Boolean = false
    
    /**
     * Checks if this array constant represents the maximum value for its type.
     * Arrays don't have a single maximum value concept.
     *
     * @return false for array constants
     */
    override fun isMaxValue(): Boolean = false
    
    /**
     * Generates LLVM IR representation of this array constant.
     * Returns the proper LLVM IR format: [elementType element1, elementType element2, ...]
     *
     * @return LLVM IR string representation
     */
    override fun toString(): String {
        return "[${elements.joinToString(", ") { element ->
            "${element.type} ${formatConstantValue(element)}"
        }}]"
    }
    
    /**
     * Formats individual constant values for LLVM IR output.
     * Handles nested arrays and different constant types.
     *
     * @param constant The constant to format
     * @return Formatted string representation for LLVM IR
     */
    private fun formatConstantValue(constant: Constant): String {
        return when (constant) {
            is ArrayConstant -> constant.toString()
            is space.norb.llvm.values.constants.IntConstant -> {
                if (constant.isUnsigned && constant.value < 0) {
                    constant.value.toULong().toString()
                } else {
                    constant.value.toString()
                }
            }
            is space.norb.llvm.values.constants.FloatConstant -> {
                formatFloatForIR(constant)
            }
            is space.norb.llvm.values.constants.NullPointerConstant -> "null"
            else -> constant.toString()
        }
    }
    
    /**
     * Formats floating-point constants for LLVM IR output.
     *
     * @param constant The FloatConstant to format
     * @return Formatted string representation for LLVM IR
     */
    private fun formatFloatForIR(constant: space.norb.llvm.values.constants.FloatConstant): String {
        val value = constant.value
        return when {
            value.isNaN() -> "nan"
            value == Double.POSITIVE_INFINITY -> "inf"
            value == Double.NEGATIVE_INFINITY -> "-inf"
            else -> {
                if (constant.type is space.norb.llvm.types.FloatingPointType.FloatType) {
                    String.format("%.6e", value.toFloat())
                } else {
                    String.format("%.6e", value)
                }
            }
        }
    }
    
    /**
     * Checks if this ArrayConstant has the same value as another constant.
     * Two array constants are equal if they have the same type and all elements are equal.
     *
     * @param other The other constant to compare with
     * @return true if they have the same value, false otherwise
     */
    override fun hasSameValueAs(other: Constant): Boolean {
        if (this === other) return true
        if (other !is ArrayConstant) return false
        if (this.type != other.type) return false
        if (this.elements.size != other.elements.size) return false
        
        return this.elements.zip(other.elements).all { (a, b) ->
            a.hasSameValueAs(b)
        }
    }
    
    companion object {
        /**
         * Creates an ArrayConstant with the specified ArrayType and elements.
         *
         * @param type The ArrayType for the array
         * @param elements The list of constant elements
         * @return A new ArrayConstant instance
         */
        fun create(type: ArrayType, elements: List<Constant>): ArrayConstant {
            return ArrayConstant(elements, type)
        }
        
        /**
         * Creates an ArrayConstant with the specified element type and elements.
         * The ArrayType is inferred from the element type and element count.
         *
         * @param elementType The type of each element in the array
         * @param elements The list of constant elements
         * @return A new ArrayConstant instance
         */
        fun create(elementType: Type, elements: List<Constant>): ArrayConstant {
            val arrayType = ArrayType(elements.size, elementType)
            return ArrayConstant(elements, arrayType)
        }
        
        /**
         * Creates an ArrayConstant with all elements set to zero.
         *
         * @param type The ArrayType for the array
         * @return A new ArrayConstant with all zero elements
         */
        fun createZero(type: ArrayType): ArrayConstant {
            val zeroElement = when {
                type.elementType.isIntegerType() -> {
                    space.norb.llvm.values.constants.IntConstant(
                        0L, 
                        type.elementType as space.norb.llvm.types.IntegerType
                    )
                }
                type.elementType.isFloatingPointType() -> {
                    space.norb.llvm.values.constants.FloatConstant(
                        0.0, 
                        type.elementType as space.norb.llvm.types.FloatingPointType
                    )
                }
                type.elementType.isPointerType() -> {
                    space.norb.llvm.values.constants.NullPointerConstant.create(type.elementType)
                }
                else -> {
                    throw IllegalArgumentException("Unsupported element type for zero array: ${type.elementType}")
                }
            }
            
            val elements = List(type.numElements) { zeroElement }
            return ArrayConstant(elements, type)
        }
        
        /**
         * Creates an ArrayConstant with all elements set to the same value.
         *
         * @param type The ArrayType for the array
         * @param value The constant value to fill the array with
         * @return A new ArrayConstant with all elements set to the specified value
         */
        fun createFilled(type: ArrayType, value: Constant): ArrayConstant {
            if (value.type != type.elementType) {
                throw IllegalArgumentException(
                    "Value type ${value.type} does not match expected element type ${type.elementType}"
                )
            }
            
            val elements = List(type.numElements) { value }
            return ArrayConstant(elements, type)
        }
        
        /**
         * Creates an ArrayConstant from varargs.
         *
         * @param elementType The type of each element in the array
         * @param elements The constant elements as varargs
         * @return A new ArrayConstant instance
         */
        fun create(elementType: Type, vararg elements: Constant): ArrayConstant {
            return create(elementType, elements.toList())
        }
        
        /**
         * Creates an ArrayConstant from varargs with explicit ArrayType.
         *
         * @param type The ArrayType for the array
         * @param elements The constant elements as varargs
         * @return A new ArrayConstant instance
         */
        fun create(type: ArrayType, vararg elements: Constant): ArrayConstant {
            return create(type, elements.toList())
        }
    }
}
