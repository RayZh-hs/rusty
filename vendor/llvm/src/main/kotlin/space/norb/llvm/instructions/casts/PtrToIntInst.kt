package space.norb.llvm.instructions.casts

import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.types.IntegerType
import space.norb.llvm.visitors.IRVisitor

/**
 * Pointer to integer conversion instruction.
 *
 * The ptrtoint instruction converts an un-typed pointer value to an integer
 * type that is large enough to represent the pointer address.
 *
 * LLVM IR syntax:
 * ```
 * <result> = ptrtoint <ptrty> <ptrval> to <intty>
 * ```
 *
 * Example:
 * ```
 * %addr = ptrtoint ptr %ptr to i64
 * ```
 *
 * @param name The name of the instruction result
 * @param type The destination integer type (must be wide enough for pointer)
 * @param value The source pointer value to convert
 */
class PtrToIntInst(
    name: String?,
    type: Type,
    value: Value
) : CastInst(name, type, value) {

    init {
        if (!value.type.isPointerType()) {
            throw IllegalArgumentException("PtrToIntInst source must be a pointer type, got ${value.type}")
        }
        if (type !is IntegerType) {
            throw IllegalArgumentException("PtrToIntInst destination must be an integer type, got $type")
        }

        val pointerBitWidth = value.type.getPrimitiveSizeInBits()
            ?: throw IllegalArgumentException("PtrToIntInst pointer source must have a defined size")
        if (type.bitWidth < pointerBitWidth) {
            throw IllegalArgumentException(
                "PtrToIntInst destination integer width (${type.bitWidth}) must be >= pointer width ($pointerBitWidth)"
            )
        }
    }

    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitPtrToIntInst(this)

    override fun getOpcodeName(): String = "ptrtoint"

    override fun mayLoseInformation(): Boolean = false

    /**
     * Gets the source pointer value being converted.
     */
    fun getSourceValue(): Value = value

    /**
     * Gets the destination integer type.
     */
    fun getDestinationType(): IntegerType = type as IntegerType

    /**
     * Gets the pointer bit width used during validation.
     */
    fun getPointerBitWidth(): Int =
        value.type.getPrimitiveSizeInBits()
            ?: throw IllegalStateException("Pointer type size should be defined after validation")

    companion object {
        /**
         * Creates a new PtrToIntInst with proper validation.
         */
        fun create(name: String?, value: Value, destType: IntegerType): PtrToIntInst {
            return PtrToIntInst(name, destType, value)
        }

        /**
         * Creates a new PtrToIntInst by specifying the destination width.
         */
        fun create(name: String?, value: Value, destBitWidth: Int): PtrToIntInst {
            val destType = IntegerType(destBitWidth)
            return PtrToIntInst(name, destType, value)
        }
    }
}
