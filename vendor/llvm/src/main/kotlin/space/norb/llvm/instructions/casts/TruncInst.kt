package space.norb.llvm.instructions.casts

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.IntegerType

/**
 * Truncate integer to smaller type instruction.
 * 
 * The trunc instruction truncates an integer value to a smaller integer type.
 * The high-order bits of the value are discarded.
 * 
 * LLVM IR syntax:
 * ```
 * <result> = trunc <ty> <value> to <ty2>
 * ```
 * 
 * Example:
 * ```
 * %trunc = trunc i32 %val to i8    ; Truncate 32-bit value to 8-bit
 * ```
 * 
 * @param name The name of the instruction result
 * @param type The destination type (must be smaller integer type)
 * @param value The source value to truncate (must be integer type)
 */
class TruncInst(
    name: String?,
    type: Type,
    value: Value
) : CastInst(name, type, value) {
    
    init {
        // Validate that both source and destination are integer types
        if (value.type !is IntegerType) {
            throw IllegalArgumentException("TruncInst source must be an integer type, got ${value.type}")
        }
        if (type !is IntegerType) {
            throw IllegalArgumentException("TruncInst destination must be an integer type, got $type")
        }
        
        // Validate that destination type is smaller than source type
        val srcBits = (value.type as IntegerType).bitWidth
        val dstBits = type.bitWidth
        
        if (dstBits >= srcBits) {
            throw IllegalArgumentException("TruncInst destination type ($dstBits bits) must be smaller than source type ($srcBits bits)")
        }
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitTruncInst(this)
    
    override fun getOpcodeName(): String = "trunc"
    
    override fun mayLoseInformation(): Boolean = true
    
    /**
     * Gets the source value being truncated.
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
         * Creates a new TruncInst with proper validation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to truncate
         * @param destType The destination integer type (must be smaller)
         * @return A new TruncInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destType: IntegerType): TruncInst {
            return TruncInst(name, destType, value)
        }
        
        /**
         * Creates a new TruncInst with automatic type creation.
         * 
         * @param name The name of the instruction result
         * @param value The source value to truncate
         * @param destBits The bit width of the destination type
         * @return A new TruncInst instance
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, value: Value, destBits: Int): TruncInst {
            val destType = IntegerType(destBits)
            return TruncInst(name, destType, value)
        }
    }
}