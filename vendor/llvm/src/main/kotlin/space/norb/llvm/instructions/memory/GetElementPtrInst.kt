package space.norb.llvm.instructions.memory

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.PointerType
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.StructType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant

/**
 * Get element pointer calculation instruction.
 *
 * This instruction calculates a pointer to a subelement of an aggregate data structure.
 * It operates on un-typed pointers, complying with the latest LLVM IR standard.
 * All pointers are of a single type regardless of the element type they point to.
 *
 * IR output example:
 * ```
 * %gep = getelementptr [10 x i32], ptr %array, i64 0, i64 5                ; Uses un-typed pointer
 * %fgep = getelementptr float, ptr %fptr, i64 3                            ; Uses un-typed pointer
 * %inbounds = getelementptr inbounds [10 x i32], ptr %array, i64 0, i64 5  ; In-bounds GEP
 * ```
 *
 * Key characteristics:
 * - All GEP instructions operate on "ptr" type regardless of element type
 * - Element type is explicitly specified as the first parameter
 * - Pointer type no longer conveys element type information
 * - Type safety is ensured through explicit type checking and validation
 * - Supports both regular and in-bounds GEP operations
 * - Handles complex pointer calculations with multiple indices
 * - Supports both struct and array indexing
 *
 * ## Usage Examples:
 *
 * ### Array indexing:
 * ```kotlin
 * val arrayType = ArrayType(10, IntegerType(32))
 * val arrayPtr = // ... some pointer value
 * val indices = listOf(IntConstant(64, 0), IntConstant(64, 5)) // array[5]
 * val gep = GetElementPtrInst("gep", arrayType, arrayPtr, indices)
 * ```
 *
 * ### Struct field access:
 * ```kotlin
 * val structType = StructType(listOf(IntegerType(32), FloatType))
 * val structPtr = // ... some pointer value
 * val indices = listOf(IntConstant(64, 0), IntConstant(64, 1)) // struct.field1
 * val gep = GetElementPtrInst("field", structType, structPtr, indices)
 * ```
 *
 * ### In-bounds GEP:
 * ```kotlin
 * val gep = GetElementPtrInst.createInBounds("gep", arrayType, arrayPtr, indices)
 * ```
 */
class GetElementPtrInst(
    name: String?,
    elementType: Type,
    pointer: Value,
    indices: List<Value>,
    val isInBounds: Boolean = false
) : MemoryInst(name,
    PointerType,
    listOf(pointer) + indices) {
    
    /**
     * The element type for GEP calculations.
     * This is explicitly specified since un-typed pointers don't convey this information.
     * This represents the type of the base element that the pointer points to.
     */
    val elementType: Type = elementType
    
    /**
     * The base pointer operand for GEP calculation.
     * In un-typed mode, this should be a PointerType.
     */
    val pointer: Value
        get() = getOperand(0)
    
    /**
     * The list of indices for GEP calculation.
     * Each index is used to navigate through the element type structure.
     */
    val indices: List<Value>
        get() = getOperandsList().drop(1)
    
    init {
        // Validate the instruction during construction
        validate()
    }
    
    /**
     * The result pointer type of this GEP operation.
     * In un-typed mode, this is PointerType.
     */
    val resultType: Type
        get() = PointerType
    
    /**
     * Gets the pointer operands used by this GEP instruction.
     * GEP has exactly one pointer operand - the base pointer.
     */
    override fun getPointerOperands(): List<Value> = listOf(pointer)
    
    /**
     * GEP instructions never read from memory directly - they only calculate addresses.
     */
    override fun mayReadFromMemory(): Boolean = false
    
    /**
     * GEP instructions never write to memory directly - they only calculate addresses.
     */
    override fun mayWriteToMemory(): Boolean = false
    
    /**
     * Validates the GEP instruction structure and operands.
     */
    private fun validate() {
        // Validate pointer operand
        if (pointer.type != PointerType) {
            throw IllegalArgumentException("GEP pointer operand must be of type 'ptr', got: ${pointer.type}")
        }
        
        // Validate indices
        if (indices.isEmpty()) {
            throw IllegalArgumentException("GEP instruction must have at least one index")
        }
        
        // All indices must be integer types
        indices.forEachIndexed { index, value ->
            if (!value.type.isIntegerType()) {
                throw IllegalArgumentException("GEP index $index must be integer type, got: ${value.type}")
            }
        }
        
        // Validate element type navigation
        try {
            getFinalElementType()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid GEP index sequence: ${e.message}", e)
        }
    }
    
    /**
     * Calculates the final element type after applying all indices.
     * This is useful for type checking and validation.
     *
     * The first index is a pointer offset and should be skipped during type navigation.
     * Type navigation starts from index 1 onwards.
     */
    fun getFinalElementType(): Type {
        var currentType = elementType
        
        // Skip the first index (index 0) as it's a pointer offset
        // Start type navigation from index 1 onwards
        for ((index, indexValue) in indices.withIndex()) {
            if (index == 0) {
                // Skip the first index as it's a pointer offset
                continue
            }
            
            when {
                currentType.isArrayType() -> {
                    // For arrays, any index selects an element of the array's element type
                    currentType = (currentType as ArrayType).elementType
                }
                currentType.isStructType() -> {
                    // For structs, we need the constant index value to determine the field
                    val structType = currentType as StructType
                    val fieldIndex = getStructFieldIndex(indexValue)
                    
                    // Handle both anonymous and named structs
                    val elementTypes = when (structType) {
                        is StructType.AnonymousStructType -> structType.elementTypes
                        is StructType.NamedStructType -> {
                            if (structType.isOpaque()) {
                                throw IllegalArgumentException("Cannot index into opaque struct type '${structType.name}'")
                            }
                            structType.elementTypes!!
                        }
                    }
                    
                    if (fieldIndex < 0 || fieldIndex >= elementTypes.size) {
                        throw IllegalArgumentException("Struct field index $fieldIndex out of bounds for struct with ${elementTypes.size} fields")
                    }
                    
                    currentType = elementTypes[fieldIndex]
                }
                else -> {
                    // For primitive types, further indexing is not valid
                    throw IllegalArgumentException("Cannot index into primitive type $currentType at index $index")
                }
            }
        }
        
        return currentType
    }
    
    /**
     * Extracts the struct field index from a constant integer value.
     * Returns the field index as an integer.
     */
    private fun getStructFieldIndex(indexValue: Value): Int {
        return when (indexValue) {
            is IntConstant -> indexValue.value.toInt()
            else -> {
                // For non-constant indices, we can't determine the field at compile time
                // In a real implementation, this would require more sophisticated analysis
                throw IllegalArgumentException("Struct field index must be a constant integer value")
            }
        }
    }
    
    
    /**
     * Gets the number of indices in this GEP instruction.
     */
    fun getNumIndices(): Int = indices.size
    
    /**
     * Gets the index at the specified position.
     */
    fun getIndex(index: Int): Value {
        if (index < 0 || index >= indices.size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for GEP with ${indices.size} indices")
        }
        return indices[index]
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitGetElementPtrInst(this)
    
    companion object {
        /**
         * Creates a regular GEP instruction.
         */
        fun create(
            name: String?,
            elementType: Type,
            pointer: Value,
            indices: List<Value>
        ): GetElementPtrInst {
            return GetElementPtrInst(name, elementType, pointer, indices, false)
        }
        
        /**
         * Creates an in-bounds GEP instruction.
         * In-bounds GEP operations provide stronger guarantees about pointer arithmetic.
         */
        fun createInBounds(
            name: String?,
            elementType: Type,
            pointer: Value,
            indices: List<Value>
        ): GetElementPtrInst {
            return GetElementPtrInst(name, elementType, pointer, indices, true)
        }
        
        /**
         * Creates a GEP instruction for simple array indexing.
         * Convenience method for the common case of array[element] access.
         */
        fun createArrayIndex(
            name: String?,
            arrayType: ArrayType,
            arrayPointer: Value,
            elementIndex: Value
        ): GetElementPtrInst {
            return create(name, arrayType, arrayPointer, listOf(
                IntConstant(0L, IntegerType.I64), // First index selects the array itself
                elementIndex                      // Second index selects the element
            ))
        }
        
        /**
         * Creates a GEP instruction for struct field access.
         * Convenience method for the common case of struct.field access.
         */
        fun createStructField(
            name: String?,
            structType: StructType,
            structPointer: Value,
            fieldIndex: Int,
            inBounds: Boolean = true
        ): GetElementPtrInst {
            // Handle both anonymous and named structs
            val elementTypes = when (structType) {
                is StructType.AnonymousStructType -> structType.elementTypes
                is StructType.NamedStructType -> {
                    if (structType.isOpaque()) {
                        throw IllegalArgumentException("Cannot access field of opaque struct type '${structType.name}'")
                    }
                    structType.elementTypes!!
                }
            }
            
            if (fieldIndex < 0 || fieldIndex >= elementTypes.size) {
                throw IllegalArgumentException("Field index $fieldIndex out of bounds for struct with ${elementTypes.size} fields")
            }
            
            val method = if (inBounds) ::createInBounds else ::create
            return method(name, structType, structPointer, listOf(
                IntConstant(0L, IntegerType.I64),        // First index selects the struct itself
                IntConstant(fieldIndex.toLong(), IntegerType.I32) // Second index selects the field
            ))
        }
    }
}
