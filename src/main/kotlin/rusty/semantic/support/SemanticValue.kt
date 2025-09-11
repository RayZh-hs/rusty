package rusty.semantic.support

import rusty.core.utils.Slot
import kotlin.reflect.KClass

sealed class SemanticValue {
    private annotation class Primitive
    private annotation class Virtual
    abstract val type: SemanticType

    @Primitive data class I32Value(val value: Int) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.I32Type
    }
    @Primitive data class U32Value(val value: UInt) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.U32Type
    }
    @Primitive data class ISizeValue(val value: Int) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.ISizeType
    }
    @Primitive data class USizeValue(val value: UInt) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.USizeType
    }
    @Primitive @Virtual class AnyIntValue(val value: Int) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.AnyIntType
    }
    @Primitive @Virtual class AnySignedIntValue(val value: Int) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.AnySignedIntType
    }
    @Primitive data class CharValue(val value: Char) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.CharType
    }
    @Primitive data class StringValue(val value: String) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.StringType
    }
    @Primitive data class CStringValue(val value: String) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.CStringType
    }
    @Primitive data class BoolValue(val value: Boolean) : SemanticValue() {
        override val type: SemanticType get() = SemanticType.BoolType
    }
    @Primitive data object UnitValue : SemanticValue() {
        override val type: SemanticType get() = SemanticType.UnitType
    }

    data class ArrayValue(override val type: SemanticType.ArrayType, val elementType: SemanticType, val elements: List<SemanticValue>, val repeat: SemanticValue.USizeValue) : SemanticValue()
    data class StructValue(override val type: SemanticType.StructType, val fields: Map<String, SemanticValue>) : SemanticValue()
    data class EnumValue(override val type: SemanticType.EnumType, val field: String) : SemanticValue()

    data class ReferenceValue(override val type: SemanticType.ReferenceType, val referenced: SemanticValue) : SemanticValue()
}

fun List<SemanticValue>.commonKClass(): KClass<out SemanticValue>? {
    if (isEmpty()) return null
    val firstClass = first()::class
    return if (all { it::class == firstClass }) firstClass else null
}

@Deprecated("Use PTI (ProgressiveTypeInferrer) instead")
fun List<SemanticValue>.commonSemanticType(): SemanticType? {
    if (isEmpty()) return null

    // If all values are the same SemanticValue subclass, return the corresponding precise type.
    when (commonKClass()) {
        SemanticValue.I32Value::class -> return SemanticType.I32Type
        SemanticValue.U32Value::class -> return SemanticType.U32Type
        SemanticValue.ISizeValue::class -> return SemanticType.ISizeType
        SemanticValue.USizeValue::class -> return SemanticType.USizeType
        SemanticValue.AnyIntValue::class -> return SemanticType.AnyIntType
        SemanticValue.AnySignedIntValue::class -> return SemanticType.AnySignedIntType
        SemanticValue.CharValue::class -> return SemanticType.CharType
        SemanticValue.StringValue::class -> return SemanticType.StringType
        SemanticValue.CStringValue::class -> return SemanticType.CStringType
        SemanticValue.BoolValue::class -> return SemanticType.BoolType
        SemanticValue.UnitValue::class -> return SemanticType.UnitType

        SemanticValue.StructValue::class -> {
            val first = this.first() as SemanticValue.StructValue
            val sameStruct = this.all { (it as SemanticValue.StructValue).type == first.type }
            return if (sameStruct) first.type else null
        }

        SemanticValue.EnumValue::class -> {
            val first = this.first() as SemanticValue.EnumValue
            val sameEnum = this.all { (it as SemanticValue.EnumValue).type == first.type }
            return if (sameEnum) first.type else null
        }

        SemanticValue.ArrayValue::class -> {
            val first = this.first() as SemanticValue.ArrayValue
            val sameElemType = this.all { (it as SemanticValue.ArrayValue).elementType == first.elementType }
            val sameLength = this.all { (it as SemanticValue.ArrayValue).repeat == first.repeat }
            return if (sameElemType && sameLength) {
                SemanticType.ArrayType(Slot(first.elementType), Slot(first.repeat))
            } else null
        }

        SemanticValue.ReferenceValue::class -> {
            // Unify referenced values, then produce a reference to the unified type (assume immutable by default).
            val inner = this.map { (it as SemanticValue.ReferenceValue).referenced }.commonSemanticType() ?: return null
            return SemanticType.ReferenceType(Slot(inner), Slot(false))
        }

        else -> {}
    }

    // Mixed-type handling for integers: find a common int supertype.
    val allAreInts = this.all {
        it is SemanticValue.I32Value ||
                it is SemanticValue.U32Value ||
                it is SemanticValue.ISizeValue ||
                it is SemanticValue.USizeValue ||
                it is SemanticValue.AnyIntValue ||
                it is SemanticValue.AnySignedIntValue
    }
    if (allAreInts) {
        val hasAnyInt = this.any { it is SemanticValue.AnyIntValue }
        val hasUnsigned = this.any { it is SemanticValue.U32Value || it is SemanticValue.USizeValue }
        val hasSigned = this.any { it is SemanticValue.I32Value || it is SemanticValue.ISizeValue || it is SemanticValue.AnySignedIntValue }

        return when {
            hasAnyInt -> SemanticType.AnyIntType
            hasSigned && hasUnsigned -> SemanticType.AnyIntType
            hasSigned -> SemanticType.AnySignedIntType
            else -> SemanticType.AnyIntType // only unsigned present
        }
    }

    // No reasonable common type.
    return null
}