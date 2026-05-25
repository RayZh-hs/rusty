package space.norb.llvm.values.globals

import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.core.MetadataCapable
import space.norb.llvm.values.Metadata
import space.norb.llvm.types.PointerType
import space.norb.llvm.types.PointerCastingUtils
import space.norb.llvm.types.TypeUtils
import space.norb.llvm.structure.Module
import space.norb.llvm.enums.LinkageType

/**
 * Global variable in LLVM IR.
 *
 * This implementation uses un-typed pointers, complying with the latest LLVM IR standard.
 * All pointers are of a single type regardless of the element type they point to.
 *
 * IR output examples:
 * ```
 * @myGlobal = global i32 0           ; Global variable with ptr type
 * @myArray = global [10 x i32] zeroinitializer  ; Global array with ptr type
 * ```
 *
 * Key characteristics:
 * - Global variables have un-typed pointers
 * - Element type is inferred from the initializer or explicit type parameter
 * - Type information is preserved through the initializer or explicit type parameter
 */
class GlobalVariable private constructor(
    override val name: String?,
    override val type: Type,
    val module: Module,
    val initializer: Constant? = null,
    val isConstantValue: Boolean = false,
    val linkage: LinkageType = LinkageType.EXTERNAL,
    val elementType: Type? = null
) : Constant(name, type), MetadataCapable {
    
    private val metadataAttachments = mutableMapOf<String, Metadata>()

    override fun getAllMetadata(): Map<String, Metadata> = metadataAttachments.toMap()

    override fun getMetadata(kind: String): Metadata? = metadataAttachments[kind]

    override fun setMetadata(kind: String, metadata: Metadata) {
        metadataAttachments[kind] = metadata
    }

    override fun removeMetadata(kind: String) {
        metadataAttachments.remove(kind)
    }

    /**
     * Checks if this global variable has an initializer.
     *
     * @return true if global variable has an initializer, false otherwise
     */
    fun hasInitializer(): Boolean = initializer != null
    
    /**
     * Checks if this global variable is a constant (immutable).
     *
     * @return true if global variable is constant, false otherwise
     */
    override fun isConstant(): Boolean = isConstantValue
    
    /**
     * Checks if this GlobalVariable is equal to another object.
     * GlobalVariables are equal if they have the same name, type, module, initializer,
     * isConstantValue, linkage, and elementType.
     *
     * @param other The object to compare with
     * @return true if the other object is a GlobalVariable with the same properties, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlobalVariable) return false
        
        return name == other.name &&
               type == other.type &&
               module == other.module &&
               initializer == other.initializer &&
               isConstantValue == other.isConstantValue &&
               linkage == other.linkage &&
               elementType == other.elementType
    }
    
    /**
     * Returns the hash code for this GlobalVariable.
     * The hash code is based on all the properties that are used in equals().
     *
     * @return The hash code for this GlobalVariable
     */
    override fun hashCode(): Int {
        var result = (name?.hashCode() ?: 0)
        result = 31 * result + type.hashCode()
        result = 31 * result + module.hashCode()
        result = 31 * result + (initializer?.hashCode() ?: 0)
        result = 31 * result + isConstantValue.hashCode()
        result = 31 * result + linkage.hashCode()
        result = 31 * result + (elementType?.hashCode() ?: 0)
        return result
    }
    
    /**
     * Returns the string representation of this GlobalVariable in LLVM IR format.
     * Contains the name and type information as expected by the tests.
     *
     * @return String representation containing name and type information
     */
    override fun toString(): String {
        return "@$name = ${linkage.name.lowercase()} ${if (isConstantValue) "constant" else "global"} ${type.toString()}"
    }
    
    override fun getParent(): Any? {
        // Global variables belong to modules
        return module
    }
    
    companion object {
        /**
         * Creates a global variable with an un-typed pointer.
         *
         * @param name The name of the global variable
         * @param module The module containing this global variable
         * @param initializer The optional initializer constant
         * @param isConstantValue Whether this global variable is constant
         * @param linkage The linkage type of the global variable
         * @return A global variable with an un-typed pointer
         */
        fun create(
            name: String?,
            module: Module,
            initializer: Constant? = null,
            isConstantValue: Boolean = false,
            linkage: LinkageType = LinkageType.EXTERNAL
        ): GlobalVariable {
            val pointerType = PointerType
            
            // Infer element type from initializer if provided
            // For NullPointerConstant, use the preserved element type
            val elementType = when {
                initializer is space.norb.llvm.values.constants.NullPointerConstant -> {
                    initializer.elementType
                }
                else -> initializer?.type
            }
            
            return GlobalVariable(
                name = name,
                type = pointerType,
                module = module,
                initializer = initializer,
                isConstantValue = isConstantValue,
                linkage = linkage,
                elementType = elementType
            )
        }
        
        /**
         * Creates a global variable with the specified element type.
         *
         * This method creates a global variable with an explicit element type.
         * Use this when you need to specify the element type directly.
         *
         * @param name The name of the global variable
         * @param elementType The element type of the global variable
         * @param module The module containing this global variable
         * @param initializer The optional initializer constant
         * @param isConstantValue Whether this global variable is constant
         * @param linkage The linkage type of the global variable
         * @return A global variable with an un-typed pointer
         */
        fun createWithElementType(
            name: String?,
            elementType: Type,
            module: Module,
            initializer: Constant? = null,
            isConstantValue: Boolean = false,
            linkage: LinkageType = LinkageType.EXTERNAL
        ): GlobalVariable {
            val pointerType = PointerType
            
            return GlobalVariable(
                name = name,
                type = pointerType,
                module = module,
                initializer = initializer,
                isConstantValue = isConstantValue,
                linkage = linkage,
                elementType = elementType
            )
        }
    }
}