package space.norb.llvm.types

import space.norb.llvm.core.Type

/**
 * Primitive LLVM types including void, integers, and floating-point types.
 */
object VoidType : Type() {
    override fun toString(): String = "void"
    override fun isPrimitiveType(): Boolean = true
    override fun isDerivedType(): Boolean = false
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = null
}

object LabelType : Type() {
    override fun toString(): String = "label"
    override fun isPrimitiveType(): Boolean = true
    override fun isDerivedType(): Boolean = false
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = null
}

object MetadataType : Type() {
    override fun toString(): String = "metadata"
    override fun isPrimitiveType(): Boolean = true
    override fun isDerivedType(): Boolean = false
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = null
}

data class IntegerType(val bitWidth: Int) : Type() {
    override fun toString(): String = "i$bitWidth"
    override fun isPrimitiveType(): Boolean = true
    override fun isDerivedType(): Boolean = false
    override fun isIntegerType(): Boolean = true
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = bitWidth
    
    companion object {
        // Common integer type constants
        val I1: IntegerType = IntegerType(1)
        val I8: IntegerType = IntegerType(8)
        val I16: IntegerType = IntegerType(16)
        val I32: IntegerType = IntegerType(32)
        val I64: IntegerType = IntegerType(64)
        val I128: IntegerType = IntegerType(128)
    }
}

sealed class FloatingPointType : Type() {
    override fun isPrimitiveType(): Boolean = true
    override fun isDerivedType(): Boolean = false
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = true
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    
    object FloatType : FloatingPointType() {
        override fun toString(): String = "float"
        override fun getPrimitiveSizeInBits(): Int? = 32
    }
    
    object DoubleType : FloatingPointType() {
        override fun toString(): String = "double"
        override fun getPrimitiveSizeInBits(): Int? = 64
    }
}