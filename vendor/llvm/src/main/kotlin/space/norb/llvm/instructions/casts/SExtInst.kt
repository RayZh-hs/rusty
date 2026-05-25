package space.norb.llvm.instructions.casts

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.IntegerType

/**
 * Sign extend integer to larger type instruction.
 * 
 * The sext instruction sign-extends an integer value to a larger integer type.
 * The high-order bits of the result are filled with the sign bit of the source value.
 * 
 * LLVM IR syntax:
 * ```
 * <result> = sext <ty> <value> to <ty2>
 * ```
 * 
 * Example:
 * ```
 * %sext = sext i8 %val to i32  ; Sign-extend 8-bit value to 32-bit
 * ```
 * 
 * @param name The name of the instruction result
 * @param type The destination type (must be larger integer type)
 * @param value The source value to sign-extend (must be integer type)
 */
class SExtInst(
    name: String?,
    type: Type,
    value: Value
) : CastInst(name, type, value) {
    
    init {
        // Validate that both source and destination are integer types
        if (value.type !is IntegerType) {
            throw IllegalArgumentException("SExtInst source must be an integer type, got ${value.type}")
        }
        if (type !is IntegerType) {
            throw IllegalArgumentException("SExtInst destination must be an integer type, got $type")
        }
        
        // Validate that destination type is larger than source type
        val srcBits = (value.type as IntegerType).bitWidth
        val dstBits = type.bitWidth
        
        if (dstBits <= srcBits) {
            throw IllegalArgumentException("SExtInst destination type ($dstBits bits) must be larger than source type ($srcBits bits)")
        }
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitSExtInst(this)
    
    override fun getOpcodeName(): String = "sext"
    
    override fun mayLoseInformation(): Boolean = false
    
    /**
     * Gets the source value being sign-extended.
     */
    fun getSourceValue(): Value = value
    
    /**
     * Gets the source integer type.
     */
    fun getSourceType(): IntegerType = value.type as IntegerType
    
    /**
     * Gets the destination integer type.
     */
    fun getDestinationType(): IntegerType = type as IntegerType
    
    /**
     * Gets the bit width of the source type.
     */
    fun getSourceBitWidth(): Int = getSourceType().bitWidth
    
    /**
     * Gets the bit width of the destination type.
     */
    fun getDestinationBitWidth(): Int = getDestinationType().bitWidth
    
    companion object {
        /**
         * Creates a new SExtInst with proper validation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to sign-extend
         * @param destType The destination integer type (must be larger)
         * @return A new SExtInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destType: IntegerType): SExtInst {
            return SExtInst(name, destType, value)
        }
        
        /**
         * Creates a new SExtInst with automatic type creation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to sign-extend
         * @param destBits The bit width of the destination type
         * @return A new SExtInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destBits: Int): SExtInst {
            val destType = IntegerType(destBits)
            return SExtInst(name, destType, value)
        }
    }
}