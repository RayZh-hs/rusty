package space.norb.llvm.types

import space.norb.llvm.core.Type

/**
 * Utility functions and commonly used type constants for working with LLVM types.
 */
object TypeUtils {
    // Common integer type constants
    val I1: Type = IntegerType(1)
    val I8: Type = IntegerType(8)
    val I16: Type = IntegerType(16)
    val I32: Type = IntegerType(32)
    val I64: Type = IntegerType(64)
    val I128: Type = IntegerType(128)
    
    // Common floating-point type constants
    val FLOAT: Type = FloatingPointType.FloatType
    val DOUBLE: Type = FloatingPointType.DoubleType
    
    // Common primitive type constants
    val VOID: Type = VoidType
    val LABEL: Type = LabelType
    val METADATA: Type = MetadataType

    // Pointer type constant (untyped pointer)
    val PTR: Type = PointerType
    
    // ==================== Type Compatibility Checks ====================
    
    /**
     * Checks if a type is a first-class type.
     * First-class types can be used as operands to most instructions.
     *
     * @param type The type to check
     * @return true if the type is first-class, false otherwise
     */
    fun isFirstClassType(type: Type): Boolean {
        return when (type) {
            is VoidType, is LabelType, is MetadataType -> false
            else -> true
        }
    }
    
    /**
     * Checks if a type is a single value type.
     * Single value types represent exactly one value.
     *
     * @param type The type to check
     * @return true if the type is a single value type, false otherwise
     */
    fun isSingleValueType(type: Type): Boolean {
        return when (type) {
            is IntegerType, is FloatingPointType, is PointerType -> true
            else -> false
        }
    }
    
    /**
     * Checks if a type is an aggregate type.
     * Aggregate types contain multiple elements.
     *
     * @param type The type to check
     * @return true if the type is an aggregate type, false otherwise
     */
    fun isAggregateType(type: Type): Boolean {
        return when (type) {
            is ArrayType, is StructType -> true
            else -> false
        }
    }
    
    /**
     * Checks if a type is an integer type.
     *
     * @param type The type to check
     * @return true if the type is an integer type, false otherwise
     */
    fun isIntegerType(type: Type): Boolean = type.isIntegerType()
    
    /**
     * Checks if a type is a floating-point type.
     *
     * @param type The type to check
     * @return true if the type is a floating-point type, false otherwise
     */
    fun isFloatingPointType(type: Type): Boolean = type.isFloatingPointType()
    
    /**
     * Checks if a type is a pointer type.
     *
     * This method works with un-typed pointers.
     *
     * @param type The type to check
     * @return true if the type is a pointer type, false otherwise
     */
    fun isPointerTy(type: Type): Boolean {
        return when (type) {
            is PointerType -> true
            else -> false
        }
    }
    
    // ==================== Type Size and Alignment Utilities ====================
    
    /**
     * Returns the size in bits for primitive types.
     *
     * @param type The type to get the size for
     * @return The size in bits, or null if not applicable
     */
    fun getPrimitiveSizeInBits(type: Type): Int? = type.getPrimitiveSizeInBits()
    
    /**
     * Returns the size in bits for scalar types.
     * Scalar types include integers, floating-point types, and pointers.
     *
     * For pointer types, this method returns 64 bits.
     *
     * @param type The type to get the size for
     * @return The size in bits, or null if not a scalar type
     */
    fun getScalarSizeInBits(type: Type): Int? {
        return when (type) {
            is IntegerType -> type.bitWidth
            is FloatingPointType -> type.getPrimitiveSizeInBits()
            is PointerType -> {
                // For pointers, return standard pointer size
                64 // Assume 64-bit pointers
            }
            else -> null
        }
    }
    
    // ==================== Type Relationship Utilities ====================
    
    /**
     * Returns the element type for array, pointer, and vector types.
     *
     * For pointer types, this method returns null as there's no element type information
     * in the un-typed pointer model.
     *
     * @param type The type to get the element type for
     * @return The element type, or null if not applicable
     */
    fun getElementType(type: Type): Type? {
        return when (type) {
            is ArrayType -> type.elementType
            is PointerType -> {
                // For un-typed pointers, there's no element type information
                // This is the key difference in the new LLVM IR model
                null
            }
            else -> null
        }
    }
    
    /**
     * Returns the number of parameters for function types.
     *
     * @param type The type to get the parameter count for
     * @return The number of parameters, or null if not a function type
     */
    fun getFunctionNumParams(type: Type): Int? {
        return when (type) {
            is FunctionType -> type.paramTypes.size
            else -> null
        }
    }
    
    /**
     * Returns the parameter type at the specified index for function types.
     *
     * @param type The function type to get the parameter from
     * @param index The index of the parameter
     * @return The parameter type at the index, or null if not applicable
     */
    fun getFunctionParamType(type: Type, index: Int): Type? {
        return when (type) {
            is FunctionType -> {
                if (index >= 0 && index < type.paramTypes.size) {
                    type.paramTypes[index]
                } else null
            }
            else -> null
        }
    }
    
    // ==================== Type Casting Utilities ====================
    
    /**
     * Checks if two types can be bitcast without loss of information.
     *
     * This method handles un-typed pointers correctly:
     * - All pointer types can be bitcast to each other
     * - All pointers are equivalent for bitcasting purposes
     *
     * @param typeA The first type
     * @param typeB The second type
     * @return true if the types can be bitcast without loss, false otherwise
     */
    fun canLosslesslyBitCastTo(typeA: Type, typeB: Type): Boolean {
        // Same types can always be bitcast
        if (typeA == typeB) return true
        
        // Check if both types are pointers
        val isAPointer = typeA is PointerType
        val isBPointer = typeB is PointerType
        
        // Pointers to pointers of any type can be bitcast
        if (isAPointer && isBPointer) return true
        
        val sizeA = getPrimitiveSizeInBits(typeA)
        val sizeB = getPrimitiveSizeInBits(typeB)
        
        // Both types must have a size to be bitcastable
        if (sizeA == null || sizeB == null) return false
        
        // Types must have the same size to be bitcastable
        if (sizeA != sizeB) return false
        
        // Integer types of same size can be bitcast
        if (typeA.isIntegerType() && typeB.isIntegerType()) return true
        
        // Floating-point types of same size can be bitcast
        if (typeA.isFloatingPointType() && typeB.isFloatingPointType()) return true
        
        // Integer and floating-point types of same size can be bitcast
        if ((typeA.isIntegerType() && typeB.isFloatingPointType()) ||
            (typeA.isFloatingPointType() && typeB.isIntegerType())) return true
        
        return false
    }
    
    /**
     * Finds a common type that can represent both input types.
     * This is useful for operations that need a unified type.
     *
     * @param typeA The first type
     * @param typeB The second type
     * @return A common type, or null if no common type exists
     */
    fun getCommonType(typeA: Type, typeB: Type): Type? {
        // If types are the same, return that type
        if (typeA == typeB) return typeA
        
        // For pointer types, return un-typed pointer
        if (typeA.isPointerType() && typeB.isPointerType()) {
            return PointerType
        }
        
        // If one can be losslessly bitcast to the other, return the target
        if (canLosslesslyBitCastTo(typeA, typeB)) return typeB
        if (canLosslesslyBitCastTo(typeB, typeA)) return typeA
        
        // For integer types, return the larger one
        if (typeA.isIntegerType() && typeB.isIntegerType()) {
            val sizeA = getPrimitiveSizeInBits(typeA) ?: return null
            val sizeB = getPrimitiveSizeInBits(typeB) ?: return null
            return if (sizeA >= sizeB) typeA else typeB
        }
        
        // For floating-point types, return the larger one
        if (typeA.isFloatingPointType() && typeB.isFloatingPointType()) {
            val sizeA = getPrimitiveSizeInBits(typeA) ?: return null
            val sizeB = getPrimitiveSizeInBits(typeB) ?: return null
            return if (sizeA >= sizeB) typeA else typeB
        }
        
        // No common type found
        return null
    }
    
    // ==================== Pointer Utilities ====================
    
    /**
     * Checks if two pointer types are equivalent for the purposes of operations.
     *
     * In un-typed mode, all pointer types are equivalent.
     *
     * @param typeA The first pointer type
     * @param typeB The second pointer type
     * @return true if the types are equivalent for operations
     */
    fun arePointersEquivalent(typeA: Type, typeB: Type): Boolean {
        require(typeA.isPointerType()) { "Type A must be a pointer type" }
        require(typeB.isPointerType()) { "Type B must be a pointer type" }
        
        // In un-typed mode, all pointers are equivalent
        return true
    }
    
    /**
     * Gets the element type for a pointer.
     *
     * For un-typed pointers, returns null as there's no element type information.
     *
     * @param pointerType The pointer type
     * @return The element type, or null for un-typed pointers
     */
    fun getPointerTypeElement(pointerType: Type): Type? {
        require(pointerType.isPointerType()) { "Type must be a pointer type" }
        
        // In un-typed mode, there's no element type information
        return null
    }
    
    /**
     * Creates a pointer type that can be used in GEP operations.
     *
     * For GEP operations, we use the un-typed pointer type.
     *
     * @param elementType The element type being accessed
     * @return The un-typed pointer type for GEP operations
     */
    fun createGEPPointerType(elementType: Type): Type {
        // In un-typed mode, use un-typed pointer
        return PointerType
    }
    
    /**
     * Checks if a pointer type contains element type information.
     *
     * @param pointerType The pointer type to check
     * @return false for un-typed pointers as they don't contain element type information
     */
    fun hasElementType(pointerType: Type): Boolean {
        require(pointerType.isPointerType()) { "Type must be a pointer type" }
        
        // Un-typed pointers don't contain element type information
        return false
    }
    
    /**
     * Creates an un-typed pointer type.
     *
     * This is the preferred method for creating pointer types in the new LLVM IR standard.
     *
     * @return The un-typed pointer type
     */
    fun createPointerType(): Type {
        return Type.getPointerType()
    }
    
    /**
     * Creates a pointer type with the specified element type (deprecated).
     *
     * This method is deprecated since all pointers are un-typed in the new LLVM IR standard.
     * The elementType parameter is ignored.
     *
     * @param elementType The element type (ignored in un-typed mode)
     * @return The un-typed pointer type
     */
    @Deprecated("Use createPointerType() instead. Element type is ignored in un-typed pointer mode.")
    fun createPointerType(elementType: Type): Type {
        return Type.getPointerType()
    }
}