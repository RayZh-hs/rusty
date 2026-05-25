package space.norb.llvm.instructions.casts

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.IntegerType

/**
 * Zero extend integer to larger type instruction.
 * 
 * The zext instruction zero-extends an integer value to a larger integer type.
 * The high-order bits of the result are filled with zeros.
 * 
 * LLVM IR syntax:
 * ```
 * <result> = zext <ty> <value> to <ty2>
 * ```
 * 
 * Example:
 * ```
 * %zext = zext i8 %val to i32  ; Zero-extend 8-bit value to 32-bit
 * ```
 * 
 * @param name The name of the instruction result
 * @param type The destination type (must be larger integer type)
 * @param value The source value to zero-extend (must be integer type)
 */
class ZExtInst(
    name: String?,
    type: Type,
    value: Value
) : CastInst(name, type, value) {
    
    init {
        // Validate that both source and destination are integer types
        if (value.type !is IntegerType) {
            throw IllegalArgumentException("ZExtInst source must be an integer type, got ${value.type}")
        }
        if (type !is IntegerType) {
            throw IllegalArgumentException("ZExtInst destination must be an integer type, got $type")
        }
        
        // Validate that destination type is larger than source type
        val srcBits = (value.type as IntegerType).bitWidth
        val dstBits = type.bitWidth
        
        if (dstBits <= srcBits) {
            throw IllegalArgumentException("ZExtInst destination type ($dstBits bits) must be larger than source type ($srcBits bits)")
        }
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitZExtInst(this)
    
    override fun getOpcodeName(): String = "zext"
    
    override fun mayLoseInformation(): Boolean = false
    
    /**
     * Gets the source value being zero-extended.
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
         * Creates a new ZExtInst with proper validation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to zero-extend
         * @param destType The destination integer type (must be larger)
         * @return A new ZExtInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destType: IntegerType): ZExtInst {
            return ZExtInst(name, destType, value)
        }
        
        /**
         * Creates a new ZExtInst with automatic type creation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to zero-extend
         * @param destBits The bit width of the destination type
         * @return A new ZExtInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destBits: Int): ZExtInst {
            val destType = IntegerType(destBits)
            return ZExtInst(name, destType, value)
        }
    }
}