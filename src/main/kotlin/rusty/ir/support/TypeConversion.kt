package rusty.ir.support

import rusty.core.CompileError
import rusty.ir.support.IRContext
import rusty.semantic.support.SemanticType
import space.norb.llvm.core.Type as IRType
import space.norb.llvm.types.TypeUtils
import space.norb.llvm.types.ArrayType

/**
 * Map a resolved [SemanticType] to its LLVM IR counterpart based on the design in docs/ir-gen.md.
 *
 * The mapping intentionally collapses many high-level constructs to pointers so IR remains
 * layout-stable and simple to work with.
 */
fun SemanticType.toIRType(): IRType {
    return when (this) {
        // Integer-like primitives share the same IR width
        SemanticType.I32Type,
        SemanticType.U32Type,
        SemanticType.ISizeType,
        SemanticType.USizeType -> TypeUtils.I32

        // Booleans and chars keep their minimal widths
        SemanticType.BoolType -> TypeUtils.I1
        SemanticType.CharType -> TypeUtils.I8

        // Strings degrade to the opaque pointer representation
        SemanticType.StrType,
        SemanticType.CStrType -> TypeUtils.PTR

        // Zero-sized or divergent constructs are padded to a single byte
        SemanticType.UnitType,
        SemanticType.NeverType -> TypeUtils.I8

        // Exit returns an i32 code in IR
        SemanticType.ExitType -> TypeUtils.I32

        is SemanticType.ArrayType -> {
            elementType.getOrNull()
                ?: throw IllegalStateException("Array element type is not resolved: $this")
            // Length must be resolved before lowering so layout decisions are stable.
            length.getOrNull()
                ?: throw IllegalStateException("Array length is not resolved: $this")
            TypeUtils.PTR
        }

        is SemanticType.ReferenceType -> {
            type.getOrNull()
                ?: throw IllegalStateException("Reference target type is not resolved: $this")
            TypeUtils.PTR
        }

        is SemanticType.StructType -> TypeUtils.PTR

        is SemanticType.EnumType -> TypeUtils.I32 // Enums are represented as integers in IR
        is SemanticType.TraitType -> throw CompileError("Traits have been removed from the IR-Generation phase.")

        is SemanticType.FunctionHeader -> {
            val params = buildList {
                selfParamType?.let { add(it.toIRType()) }
                addAll(paramTypes.map { it.toIRType() })
            }
            IRType.getFunctionType(returnType.toIRType(), params)
        }

        SemanticType.AnyIntType,
        SemanticType.AnyUnsignedIntType,
        SemanticType.AnySignedIntType,
        SemanticType.WildcardType -> TypeUtils.I32
    }
}

fun SemanticType.toStorageIRType(): IRType {
    return when (this) {
        is SemanticType.StructType -> IRContext.structTypeLookup[identifier]
            ?: throw IllegalStateException("Struct IR type missing for $identifier")
        is SemanticType.ArrayType -> {
            val element = elementType.getOrNull()
                ?: throw IllegalStateException("Array element type is not resolved: $this")
            val len = length.getOrNull()
                ?: throw IllegalStateException("Array length is not resolved: $this")
            val lengthInt = len.value.toLong()
            require(lengthInt <= Int.MAX_VALUE) { "Array length too large for IR: $lengthInt" }
            ArrayType(lengthInt.toInt(), element.toIRType())
        }
        else -> this.toIRType()
    }
}
