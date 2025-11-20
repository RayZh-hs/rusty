package rusty.ir.support

import rusty.semantic.support.SemanticType
import space.norb.llvm.core.Type as IRType
import space.norb.llvm.types.TypeUtils

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
        SemanticType.NeverType,
        SemanticType.ExitType -> TypeUtils.I8

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

        is SemanticType.EnumType,
        is SemanticType.TraitType -> TypeUtils.PTR

        is SemanticType.FunctionHeader -> {
            val params = buildList {
                selfParamType?.let { add(it.toIRType()) }
                addAll(paramTypes.map { it.toIRType() })
            }
            IRType.Companion.getFunctionType(returnType.toIRType(), params)
        }

        SemanticType.AnyIntType,
        SemanticType.AnyUnsignedIntType,
        SemanticType.AnySignedIntType,
        SemanticType.WildcardType -> error("Type inference not resolved before IR lowering: $this")
    }
}
