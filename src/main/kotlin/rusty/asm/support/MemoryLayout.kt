package rusty.asm.support

import space.norb.llvm.core.Type
import space.norb.llvm.types.*
import kotlin.math.max

sealed class MemoryLayout {
    open val offset: UInt = 0u
    open val size: UInt = 0u

    // Types of Memory Layouts
    data class Primitive(override val offset: UInt, override val size: UInt) : MemoryLayout()
    data class Struct(override val offset: UInt, val fields: List<MemoryLayout>, override val size: UInt) :
        MemoryLayout()

    data class Array(override val offset: UInt, val elementSize: UInt, val length: UInt, override val size: UInt) :
        MemoryLayout()

    companion object {
        // Round up to alignment boundary, in bytes.
        private fun upperAlign(offset: UInt, align: UInt): UInt {
            if (align == 0u) throw IllegalArgumentException("Cannot zero-align memory")
            return (offset + align - 1u) / align * align
        }

        // All alignment is counted in bytes.
        fun fromType(type: Type, offset: UInt = 0U): MemoryLayout {
            return when (type) {
                is VoidType -> MemoryLayout.Primitive(0u, 0u) // Void has no size
                is LabelType -> throw IllegalArgumentException("Label types do not have a memory layout")
                is MetadataType -> throw IllegalArgumentException("Metadata types do not have a memory layout")
                is IntegerType -> {
                    // Note that in llvm-ir, booleans are treated as i1
                    // We should 1-align booleans and 8-align all other integers (bytes and i/u-32/size)
                    val align = when (type.bitWidth) {
                        1, 8 -> 1u  // 1-align booleans and bytes
                        32 -> 4u    // 4-align i/u-32/size
                        else -> throw IllegalArgumentException("Unsupported integer bit width: ${type.bitWidth}")
                    }
                    val alignedOffset = upperAlign(offset, align)
                    MemoryLayout.Primitive(alignedOffset, type.bitWidth.toUInt())
                }

                is FloatingPointType.FloatType -> throw IllegalArgumentException("Float types are not supported in Rusty")
                is FloatingPointType.DoubleType -> throw IllegalArgumentException("Double types are not supported in Rusty")
                is PointerType -> {
                    // We are targeting rv32i so register size is 32 bits.
                    val align = 4u    // 4-align pointers
                    val alignedOffset = upperAlign(offset, align)
                    MemoryLayout.Primitive(alignedOffset, align)
                }

                is FunctionType -> throw IllegalArgumentException("Function types do not have a memory layout")
                is ArrayType -> {
                    val elementLayout = fromType(type.elementType, 0u)
                    val elementSize = elementLayout.size
                    val alignedOffset = upperAlign(offset, align = elementSize)
                    MemoryLayout.Array(
                        alignedOffset,
                        elementSize,
                        type.numElements.toUInt(),
                        elementSize * type.numElements.toUInt()
                    )
                }

                is StructType.AnonymousStructType -> {
                    var currentOffset = offset
                    val fieldLayouts = type.elementTypes.map { elementType ->
                        val fieldLayout = fromType(elementType, currentOffset)
                        currentOffset = fieldLayout.offset + fieldLayout.size
                        fieldLayout
                    }
                    val structSize = fieldLayouts.lastOrNull()?.let { it.offset + it.size } ?: 0u
                    MemoryLayout.Struct(offset, fieldLayouts, structSize)
                }

                is StructType.NamedStructType -> {
                    var currentOffset = offset
                    val fieldLayouts = type.elementTypes?.map { elementType ->
                        val fieldLayout = fromType(elementType, currentOffset)
                        currentOffset = fieldLayout.offset + fieldLayout.size
                        fieldLayout
                    } ?: emptyList()
                    val structSize = fieldLayouts.lastOrNull()?.let { it.offset + it.size } ?: 0u
                    MemoryLayout.Struct(offset, fieldLayouts, structSize)
                }

                else -> throw IllegalArgumentException("Unsupported type: ${type::class}")
            }
        }
    }
}
