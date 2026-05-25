package space.norb.llvm.core

import space.norb.llvm.types.*

/**
 * Abstract class representing all LLVM types.
 *
 * This is the core abstraction for all LLVM types in the IR system.
 * All LLVM types inherit from this abstract class, providing a common
 * interface for type operations and properties.
 *
 * Primitive types are defined in types/PrimitiveTypes.kt
 * Derived types are defined in types/DerivedTypes.kt
 */
abstract class Type {
    
    /**
     * Returns a string representation of this type in LLVM IR format.
     *
     * @return The LLVM IR string representation of this type
     */
    abstract override fun toString(): String
    
    /**
     * Checks if this type is a primitive type (void, integer, floating-point).
     *
     * @return true if this is a primitive type, false otherwise
     */
    abstract fun isPrimitiveType(): Boolean
    
    /**
     * Checks if this type is a derived type (pointer, function, array, struct).
     *
     * @return true if this is a derived type, false otherwise
     */
    abstract fun isDerivedType(): Boolean
    
    /**
     * Checks if this type can be used in integer operations.
     *
     * @return true if this type supports integer operations, false otherwise
     */
    abstract fun isIntegerType(): Boolean
    
    /**
     * Checks if this type can be used in floating-point operations.
     *
     * @return true if this type supports floating-point operations, false otherwise
     */
    abstract fun isFloatingPointType(): Boolean
    
    /**
     * Checks if this type is a pointer type.
     *
     * @return true if this is a pointer type, false otherwise
     */
    abstract fun isPointerType(): Boolean
    
    /**
     * Checks if this type is a function type.
     *
     * @return true if this is a function type, false otherwise
     */
    abstract fun isFunctionType(): Boolean
    
    /**
     * Checks if this type is an array type.
     *
     * @return true if this is an array type, false otherwise
     */
    abstract fun isArrayType(): Boolean
    
    /**
     * Checks if this type is a struct type.
     *
     * @return true if this is a struct type, false otherwise
     */
    abstract fun isStructType(): Boolean
    
    /**
     * Returns the size of this type in bits, if known.
     *
     * @return The size in bits, or null if not applicable/unknown
     */
    abstract fun getPrimitiveSizeInBits(): Int?
    
    /**
     * Abstract class representing all primitive LLVM types.
     * These are the basic building blocks of the LLVM type system.
     */
    abstract class PrimitiveType : Type() {
        override fun isPrimitiveType(): Boolean = true
        override fun isDerivedType(): Boolean = false
    }
    
    /**
     * Abstract class representing all derived LLVM types.
     * These types are constructed from other types.
     */
    abstract class DerivedType : Type() {
        override fun isPrimitiveType(): Boolean = false
        override fun isDerivedType(): Boolean = true
    }
    
    companion object {
        /**
         * Creates a void type instance.
         *
         * @return The void type
         */
        fun getVoidType(): Type = VoidType
        
        /**
         * Creates a label type instance.
         *
         * @return The label type
         */
        fun getLabelType(): Type = LabelType
        
        /**
         * Creates a metadata type instance.
         *
         * @return The metadata type
         */
        fun getMetadataType(): Type = MetadataType
        
        /**
         * Creates an integer type with the specified bit width.
         *
         * @param bits The number of bits for the integer type
         * @return An integer type with the specified bit width
         * @throws IllegalArgumentException if bit width is not positive
         */
        fun getIntegerType(bits: Int): Type {
            require(bits > 0) { "Integer bit width must be positive, got $bits" }
            return IntegerType(bits)
        }
        
        /**
         * Creates a float type instance (single precision).
         *
         * @return The float type
         */
        fun getFloatType(): Type = FloatingPointType.FloatType
        
        /**
         * Creates a double type instance (double precision).
         *
         * @return The double type
         */
        fun getDoubleType(): Type = FloatingPointType.DoubleType
        
        /**
         * Creates a pointer type.
         *
         * This method returns the un-typed pointer type, complying with the latest LLVM IR standard.
         * All pointers are of a single type regardless of the element type they point to.
         *
         * @return The un-typed pointer type
         */
        fun getPointerType(): Type = PointerType
        
        /**
         * Creates a pointer type (deprecated method for backward compatibility).
         *
         * This method returns the un-typed pointer type, complying with the latest LLVM IR standard.
         * The elementType parameter is ignored since all pointers are un-typed.
         *
         * @param elementType The type this pointer points to (ignored in un-typed mode)
         * @return The un-typed pointer type
         */
        @Deprecated("Use getPointerType() instead. Element type is ignored in un-typed pointer mode.")
        fun getPointerType(elementType: Type): Type = PointerType
        
        /**
         * Creates a function type with the specified return type and parameters.
         *
         * @param returnType The return type of the function
         * @param paramTypes The parameter types of the function
         * @return A function type
         */
        fun getFunctionType(returnType: Type, paramTypes: List<Type>): Type =
            FunctionType(returnType, paramTypes)
        
        /**
         * Creates an array type with the specified element type and number of elements.
         *
         * @param elementType The type of array elements
         * @param numElements The number of elements in the array
         * @return An array type
         * @throws IllegalArgumentException if numElements is not positive
         */
        fun getArrayType(elementType: Type, numElements: Int): Type {
            require(numElements > 0) { "Array element count must be positive, got $numElements" }
            return ArrayType(numElements, elementType)
        }
        
        /**
         * Creates a struct type with the specified element types.
         *
         * @param elementTypes The types of struct elements
         * @return A struct type
         */
        fun getStructType(elementTypes: List<Type>): Type = StructType(elementTypes)
    }
}