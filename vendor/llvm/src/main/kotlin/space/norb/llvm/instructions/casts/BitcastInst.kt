package space.norb.llvm.instructions.casts

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.PointerType

/**
 * Bitwise cast between types instruction.
 * 
 * The bitcast instruction converts a value to a different type while preserving
 * the underlying bit pattern. The source and destination types must have the same
 * size in bits.
 * 
 * LLVM IR syntax:
 * ```
 * <result> = bitcast <ty> <value> to <ty2>
 * ```
 * 
 * Examples:
 * ```
 * %int_cast = bitcast i32 %val to float    ; Cast between same-sized types
 * %ptr_to_int = bitcast ptr %ptr to i64    ; Cast pointer to integer
 * ```
 * 
 * @param name The name of the instruction result
 * @param type The destination type (must have same size as source type)
 * @param value The source value to bitcast
 */
class BitcastInst(
    name: String?,
    type: Type,
    value: Value
) : CastInst(name, type, value) {
    
    init {
        // Special handling for function-to-pointer casts
        if (value.type.isFunctionType() && type.isPointerType()) {
            // Function to pointer casts are allowed and don't need size validation
            // Skip the rest of the validation
        } else {
            // Get size of both types
            val srcSize = value.type.getPrimitiveSizeInBits()
            val dstSize = type.getPrimitiveSizeInBits()
            
            // Validate that both types have a size
            if (srcSize == null) {
                throw IllegalArgumentException("BitcastInst source type must have a defined size, got ${value.type}")
            }
            if (dstSize == null) {
                throw IllegalArgumentException("BitcastInst destination type must have a defined size, got $type")
            }
            
            // Validate that both types have the same size
            if (srcSize != dstSize) {
                throw IllegalArgumentException("BitcastInst source and destination types must have the same size: source ($srcSize bits) != destination ($dstSize bits)")
            }
        }
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitBitcastInst(this)
    
    override fun getOpcodeName(): String = "bitcast"
    
    override fun mayLoseInformation(): Boolean = false
    
    override fun isNoopCast(): Boolean {
        // Bitcast is a no-op if source and destination types are the same
        // (though this should be caught by the base class validation)
        return value.type == type
    }
    
    /**
     * Gets the source value being bitcast.
     */
    fun getSourceValue(): Value = value
    
    /**
     * Gets the source type.
     */
    fun getSourceType(): Type = value.type
    
    /**
     * Gets the destination type.
     */
    fun getDestinationType(): Type = type
    
    /**
     * Gets the size in bits of the types being cast.
     */
    fun getSizeInBits(): Int {
        return value.type.getPrimitiveSizeInBits() 
            ?: type.getPrimitiveSizeInBits() 
            ?: throw IllegalStateException("Type size should be defined after validation")
    }
    
    /**
     * Checks if this is a pointer-to-pointer cast.
     */
    fun isPointerToPointerCast(): Boolean {
        return value.type.isPointerType() && type.isPointerType()
    }
    
    /**
     * Checks if this is a pointer-to-integer cast.
     */
    fun isPointerToIntegerCast(): Boolean {
        return value.type.isPointerType() && type.isIntegerType()
    }
    
    /**
     * Checks if this is an integer-to-pointer cast.
     */
    fun isIntegerToPointerCast(): Boolean {
        return value.type.isIntegerType() && type.isPointerType()
    }
    
    companion object {
        /**
         * Creates a new BitcastInst with proper validation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to bitcast
         * @param destType The destination type (must have same size as source type)
         * @return A new BitcastInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destType: Type): BitcastInst {
            return BitcastInst(name, destType, value)
        }
        
        /**
         * Creates a pointer-to-pointer bitcast.
         * 
         * @param name The name of the instruction result
         * @param value The source pointer value
         * @return A new BitcastInst instance that casts to the un-typed pointer type
         * @throws IllegalArgumentException if validation fails
         */
        fun createPointerCast(name: String?, value: Value): BitcastInst {
            return BitcastInst(name, PointerType, value)
        }
        
        /**
         * Creates an integer-to-pointer bitcast.
         * 
         * @param name The name of the instruction result
         * @param value The source integer value
         * @return A new BitcastInst instance that casts to pointer type
         * @throws IllegalArgumentException if validation fails
         */
        fun createIntToPointerCast(name: String?, value: Value): BitcastInst {
            return BitcastInst(name, PointerType, value)
        }
        
        /**
         * Creates a pointer-to-integer bitcast.
         * 
         * @param name The name of the instruction result
         * @param value The source pointer value
         * @param intType The destination integer type (must match pointer size)
         * @return A new BitcastInst instance that casts to integer type
         * @throws IllegalArgumentException if validation fails
         */
        fun createPointerToIntCast(name: String?, value: Value, intType: Type): BitcastInst {
            return BitcastInst(name, intType, value)
        }
    }
}