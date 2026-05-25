package space.norb.llvm.types

import space.norb.llvm.core.Type
import space.norb.llvm.values.constants.NullPointerConstant

/**
 * Utility functions for pointer type casting and conversion operations.
 *
 * This utility provides functions for working with un-typed pointers
 * in compliance with the latest LLVM IR standard.
 */
object PointerCastingUtils {
    
    /**
     * Checks if a pointer can be cast to another pointer type.
     *
     * In the un-typed pointer model, all pointers can be cast to each other.
     *
     * @param sourceType The source pointer type
     * @param targetType The target pointer type
     * @return true if the cast is allowed, false otherwise
     */
    fun canCastPointerTo(sourceType: Type, targetType: Type): Boolean {
        require(sourceType.isPointerType()) { "Source type must be a pointer type" }
        require(targetType.isPointerType()) { "Target type must be a pointer type" }
        
        // In un-typed mode, all pointers can be cast to any pointer type
        return true
    }
    
    /**
     * Creates a bitcast instruction between pointer types.
     *
     * This is a utility function that would typically be used by IRBuilder
     * to create bitcast instructions for pointer type conversions.
     *
     * @param sourceType The source pointer type
     * @param targetType The target pointer type
     * @return The resulting pointer type, or `null` if the cast is not possible
     */
    fun createPointerBitcast(sourceType: Type, targetType: Type): Type? {
        if (!canCastPointerTo(sourceType, targetType)) {
            return null
        }
        
        // In un-typed mode, all pointers are of the same type
        return PointerType
    }
    
    /**
     * Gets the appropriate pointer type for a given element type.
     *
     * This utility centralizes pointer type creation logic.
     *
     * @param elementType The element type
     * @return The un-typed pointer type
     */
    fun getPointerTypeFor(elementType: Type): Type {
        return Type.getPointerType()
    }
    
    /**
     * Creates a null pointer constant for the specified element type.
     *
     * This utility centralizes null pointer creation.
     *
     * @param elementType The element type
     * @return A null pointer constant of the appropriate type
     */
    fun createNullPointer(elementType: Type): NullPointerConstant {
        return NullPointerConstant.create(elementType)
    }
    
    /**
     * Creates a null pointer constant with explicit pointer type.
     *
     * This utility creates a null pointer with the exact pointer type provided.
     *
     * @param pointerType The exact pointer type to use
     * @return A null pointer constant with the specified type
     */
    fun createNullPointerWithExactType(pointerType: Type): NullPointerConstant {
        return NullPointerConstant.createWithPointerType(pointerType)
    }
    
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
     * Checks if a pointer type can be used in operations that require element type information.
     *
     * In the un-typed pointer model, operations need explicit type information
     * provided separately.
     *
     * @param pointerType The pointer type to check
     * @return false for un-typed pointers as they don't contain element type information
     */
    fun hasElementTypeInformation(pointerType: Type): Boolean {
        require(pointerType.isPointerType()) { "Type must be a pointer type" }
        
        // Un-typed pointers don't contain element type information
        return false
    }
    
    /**
     * Creates a pointer type for operations.
     *
     * In un-typed mode, creates the un-typed pointer type.
     * Element type information must be provided separately for operations.
     *
     * @param elementType The element type (ignored in un-typed mode)
     * @return The un-typed pointer type
     */
    fun createTypedPointerForOperation(elementType: Type): Type {
        // In un-typed mode, create un-typed pointer
        // Element type information must be provided separately for operations
        return PointerType
    }
    
    /**
     * Validates if a pointer operation can be performed with the given types.
     *
     * This method helps ensure that pointer operations are valid in the
     * un-typed pointer mode.
     *
     * @param pointerType The pointer type being used
     * @param elementType The element type required for the operation (ignored for un-typed)
     * @return true if the operation is valid, false otherwise
     */
    fun validatePointerOperation(pointerType: Type, elementType: Type? = null): Boolean {
        if (!pointerType.isPointerType()) return false
        
        // For un-typed pointers, any element type is acceptable
        // The operation must handle type information separately
        return true
    }
    
    /**
     * Gets the appropriate pointer type for return values from functions.
     *
     * In un-typed mode, returns the un-typed pointer type.
     *
     * @param elementType The element type the function should return a pointer to (ignored)
     * @return The un-typed pointer type for function returns
     */
    fun getFunctionReturnPointerType(elementType: Type): Type {
        // In un-typed mode, return un-typed pointer
        return PointerType
    }
    
    /**
     * Checks if two pointer types can be used interchangeably in function signatures.
     *
     * In un-typed mode, all pointers are compatible for function signatures.
     *
     * @param typeA The first pointer type
     * @param typeB The second pointer type
     * @return true if the types are compatible for function signatures
     */
    fun areFunctionPointerTypesCompatible(typeA: Type, typeB: Type): Boolean {
        require(typeA.isPointerType()) { "Type A must be a pointer type" }
        require(typeB.isPointerType()) { "Type B must be a pointer type" }
        
        // In un-typed mode, all pointers are compatible for function signatures
        return true
    }
}
