package space.norb.llvm.builder

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.PointerType
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.VoidType
import space.norb.llvm.types.FloatingPointType
import space.norb.llvm.values.constants.IntConstant
import space.norb.llvm.values.constants.FloatConstant
import space.norb.llvm.values.constants.NullPointerConstant

/**
 * Utility functions for IR building.
 */
object BuilderUtils {
    
    // Type creation utilities
    fun getIntType(bits: Int): IntegerType = IntegerType(bits)
    
    fun getVoidType(): Type = VoidType
    
    fun getPointerType(): Type = PointerType
    
    fun getFloatType(): Type = FloatingPointType.FloatType
    
    fun getDoubleType(): Type = FloatingPointType.DoubleType
    
    fun getFunctionType(returnType: Type, paramTypes: List<Type>, isVarArg: Boolean = false, paramNames: List<String>? = null): FunctionType {
        return FunctionType(returnType, paramTypes, isVarArg, paramNames)
    }
    
    // Constant creation utilities
    fun getIntConstant(value: Long, type: IntegerType): IntConstant {
        return IntConstant(value, type)
    }
    
    fun getIntConstant(value: Int, type: IntegerType): IntConstant {
        return IntConstant(value.toLong(), type)
    }
    
    fun getIntConstant(value: Long, bits: Int): IntConstant {
        return IntConstant(value, IntegerType(bits))
    }
    
    fun getIntConstant(value: Int, bits: Int): IntConstant {
        return IntConstant(value.toLong(), IntegerType(bits))
    }
    
    fun getFloatConstant(value: Double, type: FloatingPointType): FloatConstant {
        return FloatConstant(value, type)
    }
    
    fun getFloatConstant(value: Double): FloatConstant {
        return FloatConstant(value, FloatingPointType.DoubleType)
    }
    
    fun getFloatConstant(value: Float): FloatConstant {
        return FloatConstant(value.toDouble(), FloatingPointType.FloatType)
    }
    
    fun getNullPointer(): NullPointerConstant {
        return NullPointerConstant.createUntyped()
    }
    
    fun getNullPointer(elementType: Type): NullPointerConstant {
        return NullPointerConstant.create(elementType)
    }
    
    // Type checking utilities
    fun isIntegerType(type: Type): Boolean = type.isIntegerType()
    
    fun isFloatingPointType(type: Type): Boolean = type.isFloatingPointType()
    
    fun isPointerType(type: Type): Boolean = type.isPointerType()
    
    fun isFunctionType(type: Type): Boolean = type.isFunctionType()
    
    // Type conversion utilities
    fun getCommonType(type1: Type, type2: Type): Type? {
        return when {
            type1 == type2 -> type1
            type1.isPointerType() && type2.isPointerType() -> PointerType
            type1.isIntegerType() && type2.isIntegerType() -> {
                val bits1 = type1.getPrimitiveSizeInBits() ?: return null
                val bits2 = type2.getPrimitiveSizeInBits() ?: return null
                IntegerType(maxOf(bits1, bits2))
            }
            else -> null
        }
    }
    
    // Convenience methods for common operations
    fun createZeroValue(type: Type): Value {
        return when {
            type.isIntegerType() -> getIntConstant(0, type as IntegerType)
            type.isFloatingPointType() -> getFloatConstant(0.0, type as FloatingPointType)
            type.isPointerType() -> getNullPointer()
            else -> throw IllegalArgumentException("Cannot create zero value for type: $type")
        }
    }
    
    fun createOneValue(type: Type): Value {
        return when {
            type.isIntegerType() -> getIntConstant(1, type as IntegerType)
            type.isFloatingPointType() -> getFloatConstant(1.0, type as FloatingPointType)
            else -> throw IllegalArgumentException("Cannot create one value for type: $type")
        }
    }
    
    fun createAllOnesValue(type: Type): Value {
        return when {
            type.isIntegerType() -> {
                val bits = type.getPrimitiveSizeInBits() ?: throw IllegalArgumentException("Unknown bit width")
                getIntConstant((1L shl bits) - 1, type as IntegerType)
            }
            type.isFloatingPointType() -> {
                // For floating point, all ones represents NaN
                getFloatConstant(Double.NaN, type as FloatingPointType)
            }
            else -> throw IllegalArgumentException("Cannot create all-ones value for type: $type")
        }
    }
    
    // Size utilities
    fun getTypeSizeInBits(type: Type): Int? {
        return type.getPrimitiveSizeInBits()
    }
    
    fun isTypeLargerThan(type1: Type, type2: Type): Boolean? {
        val size1 = type1.getPrimitiveSizeInBits() ?: return null
        val size2 = type2.getPrimitiveSizeInBits() ?: return null
        return size1 > size2
    }
    
    // Validation utilities
    fun validateBinaryOperands(lhs: Value, rhs: Value) {
        if (lhs.type != rhs.type) {
            throw IllegalArgumentException("Binary operand types must match: ${lhs.type} vs ${rhs.type}")
        }
    }
    
    fun validatePointerType(pointer: Value) {
        if (!pointer.type.isPointerType()) {
            throw IllegalArgumentException("Expected pointer type, got: ${pointer.type}")
        }
    }
    
    fun validateIntegerType(value: Value) {
        if (!value.type.isIntegerType()) {
            throw IllegalArgumentException("Expected integer type, got: ${value.type}")
        }
    }
    
    fun validateFloatingPointType(value: Value) {
        if (!value.type.isFloatingPointType()) {
            throw IllegalArgumentException("Expected floating-point type, got: ${value.type}")
        }
    }
}