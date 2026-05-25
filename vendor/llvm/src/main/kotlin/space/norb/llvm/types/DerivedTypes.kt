package space.norb.llvm.types

import space.norb.llvm.core.Type

/**
 * Derived LLVM types including pointers, functions, arrays, and structs.
 *
 * ## LLVM IR Compliance Notice
 *
 * This implementation uses the new un-typed pointer model which complies with the
 * latest LLVM IR standard. All pointers are of a single type (similar to `void*` in C).
 * Type information is conveyed through other mechanisms rather than being embedded
 * in the pointer type itself.
 *
 * ### Current Implementation (LLVM IR Compliant)
 *
 * This PointerType object implements un-typed pointers where all pointers are of a single type.
 * This is the current LLVM IR standard.
 *
 * Key characteristics of the current implementation:
 * - All pointers are of a single type regardless of pointee type
 * - String representation is simply "ptr"
 * - Type checking and validation rely on context rather than pointer type
 * - Pointer operations like GEP use explicit type information
 */
/**
 * Pointer type implementation compliant with the latest LLVM IR standard.
 *
 * This represents the un-typed pointer model where all pointers are of a single type
 * (similar to `void*` in C). Type information is conveyed through other mechanisms.
 */
object PointerType : Type() {
    override fun toString(): String = "ptr"
    override fun isPrimitiveType(): Boolean = false
    override fun isDerivedType(): Boolean = true
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = true
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = 64 // Assume 64-bit pointers
}

data class FunctionType(
    var returnType: Type,
    var paramTypes: List<Type>,
    var isVarArg: Boolean = false,
    var paramNames: List<String>? = null
) : Type() {
    override fun toString(): String {
        val params = paramTypes.joinToString(", ") { it.toString() }
        val varArg = if (isVarArg) {
            if (paramTypes.isEmpty()) "..." else ", ..."
        } else ""
        return "${returnType.toString()} ($params$varArg)"
    }
    override fun isPrimitiveType(): Boolean = false
    override fun isDerivedType(): Boolean = true
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = true
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = null
    
    /**
     * Get the parameter name at the specified index.
     * Falls back to "arg$index" when no custom name is provided.
     */
    fun getParameterName(index: Int): String {
        return paramNames?.getOrNull(index) ?: "arg$index"
    }
}

data class ArrayType(var numElements: Int, var elementType: Type) : Type() {
    override fun toString(): String = "[$numElements x ${elementType.toString()}]"
    override fun isPrimitiveType(): Boolean = false
    override fun isDerivedType(): Boolean = true
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = true
    override fun isStructType(): Boolean = false
    override fun getPrimitiveSizeInBits(): Int? = null
}

/**
 * Sealed hierarchy representing all struct types in LLVM IR.
 * 
 * This hierarchy supports both named and anonymous structs:
 * - Anonymous structs are defined by their structure (element types and packing)
 * - Named structs are identified by their name and may be opaque (no elements defined yet)
 */
sealed class StructType : Type() {
    override fun isPrimitiveType(): Boolean = false
    override fun isDerivedType(): Boolean = true
    override fun isIntegerType(): Boolean = false
    override fun isFloatingPointType(): Boolean = false
    override fun isPointerType(): Boolean = false
    override fun isFunctionType(): Boolean = false
    override fun isArrayType(): Boolean = false
    override fun isStructType(): Boolean = true
    override fun getPrimitiveSizeInBits(): Int? = null
    
    /**
     * Returns the definition string representation for this struct type.
     * For named structs, this returns the definition form (e.g., "%name = type { i32, i64 }").
     * For anonymous structs, this returns the inline form (e.g., "{ i32, i64 }").
     */
    abstract fun toDefinitionString(): String
    
    /**
     * Returns the inline usage string representation for this struct type.
     * For named structs, this returns the name reference (e.g., "%name").
     * For anonymous structs, this returns the inline form (e.g., "{ i32, i64 }").
     */
    abstract fun toInlineString(): String
    
    /**
     * Anonymous struct type defined by its element types and packing.
     * 
     * Equality is based on structural equality - two anonymous structs are equal
     * if they have the same element types in the same order and the same packing.
     */
    data class AnonymousStructType(
        val elementTypes: List<Type>, 
        val isPacked: Boolean = false
    ) : StructType() {
        
        override fun toString(): String = toInlineString()
        
        override fun toDefinitionString(): String {
            val elements = elementTypes.joinToString(", ") { it.toString() }
            return if (isPacked) "<{ $elements }>" else "{ $elements }"
        }
        
        override fun toInlineString(): String = toDefinitionString()
    }
    
    /**
     * Named struct type identified by a unique name.
     * 
     * Named structs may be opaque (no elements defined yet) or complete
     * (with defined element types). Equality is based on name equality.
     */
    data class NamedStructType(
        val name: String,
        var elementTypes: List<Type>? = null, // null for opaque structs
        var isPacked: Boolean = false
    ) : StructType() {
        
        init {
            require(name.isNotBlank()) { "Struct name cannot be blank" }
            require(elementTypes?.isNotEmpty() != false) {
                "Element types must be null for opaque structs or non-empty for complete structs" 
            }
        }
        
        /**
         * Checks if this named struct is opaque (has no element types defined).
         */
        fun isOpaque(): Boolean = elementTypes == null
        
        /**
         * Checks if this named struct is complete (has element types defined).
         */
        fun isComplete(): Boolean = elementTypes != null

        /**
         * Completes this named struct in place.
         */
        fun complete(elementTypes: List<Type>, isPacked: Boolean = false): NamedStructType {
            require(isOpaque()) { "Struct type '$name' is already complete" }
            require(elementTypes.isNotEmpty()) { "Element types must be non-empty for complete structs" }

            this.elementTypes = elementTypes
            this.isPacked = isPacked
            return this
        }
        
        override fun toString(): String = toInlineString()
        
        override fun toDefinitionString(): String {
            val elements = elementTypes?.joinToString(", ") { it.toString() } ?: ""
            val body = if (isOpaque()) {
                "opaque"
            } else {
                if (isPacked) "<{ $elements }>" else "{ $elements }"
            }
            return "%$name = type $body"
        }
        
        override fun toInlineString(): String = "%$name"
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NamedStructType) return false
            return name == other.name
        }
        
        override fun hashCode(): Int {
            return name.hashCode()
        }
    }
}

/**
 * Factory function for creating anonymous struct types.
 * This maintains backward compatibility with existing code.
 */
@Deprecated("Use StructType.AnonymousStructType constructor directly", ReplaceWith("StructType.AnonymousStructType(elementTypes, isPacked)"))
fun StructType(elementTypes: List<Type>, isPacked: Boolean = false): StructType.AnonymousStructType {
    return StructType.AnonymousStructType(elementTypes, isPacked)
}

/**
 * Factory function for creating named struct types.
 */
fun createNamedStructType(
    name: String, 
    elementTypes: List<Type>? = null, 
    isPacked: Boolean = false
): StructType.NamedStructType {
    return StructType.NamedStructType(name, elementTypes, isPacked)
}

/**
 * Factory function for creating opaque named struct types.
 */
fun createOpaqueStructType(name: String): StructType.NamedStructType {
    return StructType.NamedStructType(name, null, false)
}
