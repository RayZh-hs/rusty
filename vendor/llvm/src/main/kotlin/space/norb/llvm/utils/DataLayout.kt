package space.norb.llvm.utils

import space.norb.llvm.core.Type

/**
 * Parsed subset of an LLVM target data layout string.
 *
 * This class currently models the layout properties this library needs for type
 * sizing: optional pointer size/alignment plus ABI alignments for integer and
 * floating-point types. Alignment values returned by this API are in bytes.
 */
class DataLayout(layout: String = "") {
    val layout: String = layout

    var pointerSizeInBits: Int? = null
        private set

    var pointerABIAlignment: Int? = null
        private set

    private val integerABIAlignments: MutableMap<Int, Int> = mutableMapOf()
    private val floatingPointABIAlignments: MutableMap<Int, Int> = mutableMapOf()

    init {
        parse(layout)
    }

    /**
     * Returns the ABI alignment of [type] in bytes.
     */
    fun getABITypeAlignment(type: Type): Int = type.computeLayout(this).alignment

    /**
     * Returns the allocation size of [type] in bits.
     */
    fun getTypeSizeInBits(type: Type): Long = type.computeLayout(this).sizeInBytes * 8

    internal fun integerABIAlignment(bitWidth: Int, fallback: Int): Int =
        integerABIAlignments[bitWidth] ?: fallback

    internal fun floatingPointABIAlignment(bitWidth: Int, fallback: Int): Int =
        floatingPointABIAlignments[bitWidth] ?: fallback

    private fun parse(layout: String) {
        if (layout.isBlank()) return

        for (component in layout.split("-")) {
            when {
                component.startsWith("p") -> parsePointerLayout(component)
                component.startsWith("i") -> parseABIAlignment(component, "i", integerABIAlignments)
                component.startsWith("f") -> parseABIAlignment(component, "f", floatingPointABIAlignments)
            }
        }
    }

    private fun parsePointerLayout(component: String) {
        val fields = component.split(":")
        if (fields.size < 2) return

        val addressSpace = fields[0].removePrefix("p")
        if (addressSpace.isNotEmpty() && addressSpace != "0") return

        val sizeInBits = fields[1].toIntOrNull() ?: return
        val alignmentInBits = fields.getOrNull(2)?.toIntOrNull() ?: sizeInBits

        if (sizeInBits > 0 && sizeInBits % 8 == 0) {
            pointerSizeInBits = sizeInBits
            pointerABIAlignment = alignmentInBits.toByteAlignment()
        }
    }

    private fun parseABIAlignment(
        component: String,
        prefix: String,
        destination: MutableMap<Int, Int>
    ) {
        val fields = component.split(":")
        if (fields.size < 2) return

        val bitWidth = fields[0].removePrefix(prefix).toIntOrNull() ?: return
        val alignment = fields[1].toIntOrNull()?.toByteAlignment() ?: return
        destination[bitWidth] = alignment
    }

    private fun Int.toByteAlignment(): Int? {
        if (this <= 0) return null
        return maxOf(1, this / 8)
    }
}
