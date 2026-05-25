package space.norb.llvm.utils

import space.norb.llvm.core.Type
import space.norb.llvm.structure.Module
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.FloatingPointType
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.LabelType
import space.norb.llvm.types.MetadataType
import space.norb.llvm.types.PointerType
import space.norb.llvm.types.StructType
import space.norb.llvm.types.VoidType

/**
 * Utility helpers for computing byte sizes of LLVM types with alignment-aware layouts.
 *
 * The calculation mirrors a simplified data layout where each type is aligned to its natural
 * size (in bytes). Structs honour field alignment unless marked as packed, and the total struct
 * size is rounded up to the struct alignment to make arrays of structs correctly aligned.
 */
data class Layout(val sizeInBytes: Long, val alignment: Int)

/**
 * Computes the size of the given type in bytes, taking standard padding rules into account.
 *
 * @return The size in bytes
 * @throws IllegalArgumentException If the type cannot be sized (e.g., functions or opaque structs)
 */
fun Type.getSizeInBytes(pointerWidthBits: Int? = null): Long =
    computeLayout(pointerWidthBits).sizeInBytes

/**
 * Computes the size of the given type in bytes, resolving opaque named structs through [module].
 * 
 * An explicit [pointerWidthBits] overrides the module data layout. If neither provides pointer
 * sizing information, pointer-bearing layouts fail when the pointer size is needed.
 */
fun Type.getSizeInBytes(module: Module, pointerWidthBits: Int? = null): Long {
    return computeLayout(module, pointerWidthBits).sizeInBytes
}

/**
 * Computes the size of the given type in bytes using a parsed target data layout.
 */
fun Type.getSizeInBytes(dataLayout: DataLayout, module: Module? = null): Long =
    computeLayout(dataLayout, module).sizeInBytes

/**
 * Computes both size and alignment for the receiver type.
 */
fun Type.computeLayout(pointerWidthBits: Int? = null): Layout {
    validatePointerWidth(pointerWidthBits)
    return computeLayoutInternal(pointerWidthBits, pointerWidthBits?.div(8), null, null)
}

/**
 * Computes both size and alignment for the receiver type, resolving opaque named structs through [module].
 *
 * An explicit [pointerWidthBits] overrides the module data layout.
 */
fun Type.computeLayout(module: Module, pointerWidthBits: Int? = null): Layout {
    val dataLayout = module.dataLayout?.let { DataLayout(it) }
    val resolvedPointerWidthBits = resolvePointerWidth(pointerWidthBits, dataLayout)
    val resolvedPointerAlignment = resolvePointerAlignment(pointerWidthBits, dataLayout)
    return computeLayoutInternal(resolvedPointerWidthBits, resolvedPointerAlignment, module, dataLayout)
}

/**
 * Computes both size and alignment for the receiver type using a parsed target data layout.
 */
fun Type.computeLayout(dataLayout: DataLayout, module: Module? = null): Layout {
    val resolvedPointerWidthBits = resolvePointerWidth(null, dataLayout)
    return computeLayoutInternal(resolvedPointerWidthBits, dataLayout.pointerABIAlignment, module, dataLayout)
}

internal fun Type.computeLayoutInternal(
    pointerWidthBits: Int?,
    pointerAlignmentBytes: Int?,
    module: Module?,
    dataLayout: DataLayout?
): Layout {
    return when (this) {
        is IntegerType -> {
            val bytes = maxOf(1, (this.bitWidth + 7) / 8)
            val alignment = dataLayout?.integerABIAlignment(this.bitWidth, nextPowerOfTwo(bytes))
                ?: nextPowerOfTwo(bytes)
            Layout(bytes.toLong(), alignment)
        }
        is FloatingPointType.FloatType -> Layout(4, dataLayout?.floatingPointABIAlignment(32, 4) ?: 4)
        is FloatingPointType.DoubleType -> Layout(8, dataLayout?.floatingPointABIAlignment(64, 8) ?: 8)
        is PointerType -> pointerLayout(pointerWidthBits, pointerAlignmentBytes)
        is ArrayType -> computeArrayLayout(this, pointerWidthBits, pointerAlignmentBytes, module, dataLayout)
        is StructType.AnonymousStructType ->
            computeStructLayout(this.elementTypes, this.isPacked, pointerWidthBits, pointerAlignmentBytes, module, dataLayout)
        is StructType.NamedStructType -> {
            val resolvedStruct = if (this.elementTypes == null && module != null) {
                module.getNamedStructType(this.name) ?: this
            } else {
                this
            }
            val elements = resolvedStruct.elementTypes
                ?: throw IllegalArgumentException("Cannot compute size of opaque named struct: ${this.name}")
            computeStructLayout(
                elements,
                resolvedStruct.isPacked,
                pointerWidthBits,
                pointerAlignmentBytes,
                module,
                dataLayout
            )
        }
        is FunctionType -> throw IllegalArgumentException("Cannot compute size of function type: $this")
        is VoidType, is LabelType, is MetadataType ->
            throw IllegalArgumentException("Cannot compute size of unsized type: $this")
        else -> throw IllegalArgumentException("Unsupported type for size calculation: ${this::class.simpleName}")
    }
}

// Helper function to compute the next power of two for alignment purposes
private fun nextPowerOfTwo(v: Int): Int {
    var n = v - 1
    n = n or (n ushr 1)
    n = n or (n ushr 2)
    n = n or (n ushr 4)
    n = n or (n ushr 8)
    n = n or (n ushr 16)
    return n + 1
}

private fun validatePointerWidth(pointerWidthBits: Int?) {
    if (pointerWidthBits == null) return
    require(pointerWidthBits == 32 || pointerWidthBits == 64) {
        "Only 32-bit and 64-bit pointer sizes are supported (got $pointerWidthBits)"
    }
}

private fun resolvePointerWidth(pointerWidthBits: Int?, dataLayout: DataLayout?): Int? {
    validatePointerWidth(pointerWidthBits)

    val layoutPointerWidth = dataLayout?.pointerSizeInBits
    validatePointerWidth(layoutPointerWidth)

    return pointerWidthBits ?: layoutPointerWidth
}

private fun resolvePointerAlignment(pointerWidthBits: Int?, dataLayout: DataLayout?): Int? {
    return if (pointerWidthBits != null) {
        pointerWidthBits / 8
    } else {
        dataLayout?.pointerABIAlignment
    }
}

private fun pointerLayout(pointerWidthBits: Int?, pointerAlignmentBytes: Int?): Layout {
    val resolvedPointerWidthBits = pointerWidthBits
        ?: throw IllegalArgumentException(
            "Pointer size must be specified explicitly or provided by the data layout"
        )
    val pointerBytes = resolvedPointerWidthBits / 8
    return Layout(pointerBytes.toLong(), pointerAlignmentBytes ?: pointerBytes)
}

private fun computeArrayLayout(
    arrayType: ArrayType,
    pointerWidthBits: Int?,
    pointerAlignmentBytes: Int?,
    module: Module?,
    dataLayout: DataLayout?
): Layout {
    require(arrayType.numElements >= 0) { "Array element count must be non-negative" }
    val elementLayout = arrayType.elementType.computeLayoutInternal(
        pointerWidthBits,
        pointerAlignmentBytes,
        module,
        dataLayout
    )
    val stride = alignTo(elementLayout.sizeInBytes, elementLayout.alignment.toLong())
    return Layout(stride * arrayType.numElements, elementLayout.alignment)
}

private fun computeStructLayout(
    elementTypes: List<Type>,
    isPacked: Boolean,
    pointerWidthBits: Int?,
    pointerAlignmentBytes: Int?,
    module: Module?,
    dataLayout: DataLayout?
): Layout {
    if (elementTypes.isEmpty()) return Layout(0, 1)

    var offset = 0L
    var structAlignment = if (isPacked) 1 else 1

    for (elementType in elementTypes) {
        val elementLayout = elementType.computeLayoutInternal(
            pointerWidthBits,
            pointerAlignmentBytes,
            module,
            dataLayout
        )
        val elementAlignment = if (isPacked) 1 else elementLayout.alignment

        offset = alignTo(offset, elementAlignment.toLong())
        offset += elementLayout.sizeInBytes
        structAlignment = maxOf(structAlignment, elementAlignment)
    }

    val finalAlignment = if (isPacked) 1 else structAlignment
    val finalSize = if (isPacked) offset else alignTo(offset, finalAlignment.toLong())
    return Layout(finalSize, finalAlignment)
}

private fun alignTo(value: Long, alignment: Long): Long {
    if (alignment <= 1) return value
    val remainder = value % alignment
    return if (remainder == 0L) value else value + (alignment - remainder)
}
