package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SymbolTable

data class SymbolAndScope(val symbol: SemanticSymbol, val scope: Scope)

fun sequentialLookup(identifier: String, scope: Scope, symbolTableGetter: (Scope) -> SymbolTable): SymbolAndScope? {
    var scopePointer: Scope? = scope
    while (scopePointer != null) {
        val symbol = symbolTableGetter(scopePointer).symbols[identifier]
        if (symbol != null) {
            return SymbolAndScope(symbol, scopePointer)
        }
        scopePointer = scopePointer.parent
    }
    return null
}

class ProgressiveTypeInferrer(start: SemanticType = SemanticType.NeverType) {
    var type: SemanticType = start

    fun register(newType: SemanticType) {
        // Treat NeverType as a no-op placeholder
        if (newType is SemanticType.NeverType) return

        // Ignore wildcard contributions; they don't constrain the type
        if (newType is SemanticType.WildcardType) return

        // First meaningful type wins if we have none yet
        if (type is SemanticType.NeverType) {
            type = newType
            return
        }

        // If already equal, nothing to do
        if (type == newType) return

        // Local helpers for int-family detection
        fun isSignedConcrete(t: SemanticType) = t is SemanticType.I32Type || t is SemanticType.ISizeType
        fun isUnsignedConcrete(t: SemanticType) = t is SemanticType.U32Type || t is SemanticType.USizeType
        fun isAnySigned(t: SemanticType) = t is SemanticType.AnySignedIntType
        fun isAnyInt(t: SemanticType) = t is SemanticType.AnyIntType
        fun isIntFamily(t: SemanticType) = isSignedConcrete(t) || isUnsignedConcrete(t) || isAnySigned(t) || isAnyInt(t)

        fun unifyInt(cur: SemanticType, new: SemanticType): SemanticType {
            return when {
                cur == new -> cur
                cur is SemanticType.AnyIntType -> new
                cur is SemanticType.AnySignedIntType -> {
                    if (isSignedConcrete(new)) new
                    else if (new is SemanticType.AnyIntType) new
                    else throw CompileError("Cannot infer common integral type between $cur and $new")
                }
                isSignedConcrete(cur) && isAnySigned(new) -> cur
                isSignedConcrete(cur) && isAnyInt(new) -> cur
                isUnsignedConcrete(cur) && isAnyInt(new) -> cur

                else -> throw CompileError("Cannot infer common integral type between $cur and $new")
            }
        }

        // Unify based on current accumulator 'type'
        when (val cur = type) {
            // Integer families
            is SemanticType.I32Type,
            is SemanticType.ISizeType,
            is SemanticType.U32Type,
            is SemanticType.USizeType,
            is SemanticType.AnyIntType,
            is SemanticType.AnySignedIntType -> {
                if (!isIntFamily(newType))
                    throw CompileError("Cannot infer common type between $cur and $newType")
                type = unifyInt(cur, newType)
            }

            // Scalars that only unify with themselves
            is SemanticType.BoolType,
            is SemanticType.CharType,
            is SemanticType.StrType,
            is SemanticType.CStrType,
            is SemanticType.UnitType -> {
                if (cur::class != newType::class)
                    throw CompileError("Cannot infer common type between $cur and $newType")
                // keep cur
            }

            // Arrays: lengths must be compatible; element types unify recursively
            is SemanticType.ArrayType -> {
                if (newType !is SemanticType.ArrayType)
                    throw CompileError("Cannot infer common type between $cur and $newType")

                val curLen = cur.length.getOrNull()?.value
                val newLen = newType.length.getOrNull()?.value
                if (curLen != null && newLen != null && curLen != newLen)
                    throw CompileError("Array length mismatch: $curLen vs $newLen")

                val curElem = cur.elementType.getOrNull()
                    ?: throw CompileError("Unresolved array element type: $cur")
                val newElem = newType.elementType.getOrNull()
                    ?: throw CompileError("Unresolved array element type: $newType")

                val inner = ProgressiveTypeInferrer().also { it.type = curElem; it.register(newElem) }.type

                // Prefer a known length if any is known
                val chosenLen = when {
                    curLen != null -> cur.length
                    newLen != null -> newType.length
                    else -> cur.length
                }
                type = SemanticType.ArrayType(Slot(inner), chosenLen)
            }

            // Structs: identifiers and field sets must match; fields unify pairwise
            is SemanticType.StructType -> {
                if (newType !is SemanticType.StructType)
                    throw CompileError("Cannot infer common type between $cur and $newType")
                if (cur.identifier != newType.identifier)
                    throw CompileError("Cannot unify different structs: ${cur.identifier} vs ${newType.identifier}")
                if (cur.fields.keys != newType.fields.keys)
                    throw CompileError("Struct field mismatch on ${cur.identifier}")

                val unifiedFields = cur.fields.mapValues { (name, slotA) ->
                    val a = slotA.getOrNull() ?: throw CompileError("Unresolved field '$name' in ${cur.identifier}")
                    val b = newType.fields[name]?.getOrNull()
                        ?: throw CompileError("Unresolved field '$name' in ${newType.identifier}")
                    val fieldT = ProgressiveTypeInferrer().also { it.type = a; it.register(b) }.type
                    Slot(fieldT)
                }
                type = SemanticType.StructType(cur.identifier, unifiedFields)
            }

            // Enums: only same identifier allowed
            is SemanticType.EnumType -> {
                if (newType !is SemanticType.EnumType)
                    throw CompileError("Cannot infer common type between $cur and $newType")
                if (cur.identifier != newType.identifier)
                    throw CompileError("Cannot unify different enums: ${cur.identifier} vs ${newType.identifier}")
                // keep the enum type (identical by identifier)
            }

            // References: unify inner types; mutability is the intersection (both mutable => mutable, else immutable)
            is SemanticType.ReferenceType -> {
                if (newType !is SemanticType.ReferenceType)
                    throw CompileError("Cannot infer common type between $cur and $newType")
                val curInner = cur.type.getOrNull() ?: throw CompileError("Unresolved reference target: $cur")
                val newInner = newType.type.getOrNull() ?: throw CompileError("Unresolved reference target: $newType")
                val inner = ProgressiveTypeInferrer().also { it.type = curInner; it.register(newInner) }.type
                val m1 = cur.isMutable.getOrNull() == true
                val m2 = newType.isMutable.getOrNull() == true
                type = SemanticType.ReferenceType(Slot(inner), Slot(m1 && m2))
            }

            is SemanticType.TraitType -> {
                throw CompileError("Cannot infer common type for trait objects: $cur and $newType")
            }

            is SemanticType.WildcardType -> {
                // If accumulator is wildcard, adopt the new type
                type = newType
            }

            is SemanticType.FunctionHeader -> throw CompileError("Cannot infer common type for function header: $cur")
            else -> {}
        }
    }

    companion object {
        @Suppress("unused")
        fun List<SemanticType>.inferCommonType(start: SemanticType = SemanticType.WildcardType): SemanticType {
            val pti = ProgressiveTypeInferrer(start)
            for (t in this) {
                pti.register(t)
            }
            return pti.type
        }
    }
}