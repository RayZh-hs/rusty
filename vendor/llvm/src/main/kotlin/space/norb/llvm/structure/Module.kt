package space.norb.llvm.structure

import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.types.FunctionType
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.llvm.values.Metadata
import space.norb.llvm.types.StructType
import space.norb.llvm.types.createNamedStructType
import space.norb.llvm.types.createOpaqueStructType
import space.norb.llvm.visitors.IRPrinter
import space.norb.llvm.enums.LinkageType

/**
 * Module
 *
 * Represents an LLVM IR module, which is a container for functions, global variables, and metadata.
 *
 * Each module has a unique name and can contain multiple functions and global variables.
 * The module also maintains registries for named and anonymous struct types to ensure proper management and deduplication of struct types within the module.
 *
 * @param name The name of the module, which serves as its identifier. Module names should be unique within a larger compilation context to avoid naming conflicts.
 */
class Module(val name: String) {
    val functions: MutableList<Function> = mutableListOf()
    val globalVariables: MutableList<GlobalVariable> = mutableListOf()
    val namedMetadata: MutableMap<String, Metadata> = mutableMapOf()

    // Struct type registries
    private val namedStructTypes: MutableMap<String, StructType.NamedStructType> = mutableMapOf()
    private val anonymousStructTypes: MutableSet<StructType.AnonymousStructType> = mutableSetOf()

    var targetTriple: String? = null
    var dataLayout: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Module) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "Module(name=$name)"
    }

    // IR Printing Shorthand
    fun toIRString(): String {
        val irPrinter = IRPrinter()
        return irPrinter.print(this)
    }

    fun emitIR() = toIRString()

    // Global variable management APIs

    fun registerGlobalVariable(globalVariable: GlobalVariable): GlobalVariable {
        globalVariables.add(globalVariable)
        return globalVariable
    }

    fun registerGlobalVariable(
        name: String,
        initialValue: Constant? = null,
        isConstant: Boolean = false,
        linkage: LinkageType = LinkageType.EXTERNAL,
    ): GlobalVariable {
        val globalVariable = GlobalVariable.create(name, this, initialValue, isConstant, linkage)
        globalVariables.add(globalVariable)
        return globalVariable
    }

    // Function management APIs

    fun registerFunction(function: Function): Function {
        functions.add(function)
        return function
    }

    fun registerFunction(
        name: String,
        type: FunctionType,
        linkage: LinkageType = LinkageType.EXTERNAL,
        isDeclaration: Boolean = false
    ): Function {
        val function = Function(name, type, this, linkage, isDeclaration)
        functions.add(function)
        return function
    }

    fun registerFunction(
        name: String,
        returnType: Type,
        parameterTypes: List<Type>,
        isVarArg: Boolean = false,
        linkage: LinkageType = LinkageType.EXTERNAL,
        isDeclaration: Boolean = false
    ): Function {
        val functionType = FunctionType(returnType, parameterTypes, isVarArg)
        return registerFunction(name, functionType, linkage, isDeclaration)
    }

    /**
     * Declares an external function (like printf) that will be linked later.
     * This is a convenience method for declaring functions with EXTERNAL linkage.
     *
     * @param name The name of the external function
     * @param type The function type
     * @return The declared function
     */
    fun declareExternalFunction(name: String, type: FunctionType): Function {
        return registerFunction(name, type, LinkageType.EXTERNAL, isDeclaration = true)
    }

    /**
     * Declares an external function (like printf) that will be linked later.
     * This is a convenience method for declaring functions with EXTERNAL linkage.
     *
     * @param name The name of the external function
     * @param returnType The return type of the function
     * @param parameterTypes The parameter types of the function
     * @param isVarArg Whether the function is variadic
     * @return The declared function
     */
    fun declareExternalFunction(
        name: String,
        returnType: Type,
        parameterTypes: List<Type>,
        isVarArg: Boolean = false
    ): Function {
        val functionType = FunctionType(returnType, parameterTypes, isVarArg)
        return declareExternalFunction(name, functionType)
    }

    // Struct type management APIs

    /**
     * Registers a named struct type with this module.
     * 
     * @param name The unique name for the struct type
     * @param elementTypes The element types, or null for an opaque struct
     * @param isPacked Whether this is a packed struct
     * @return The registered named struct type
     * @throws IllegalArgumentException if a struct with the same name already exists
     */
    fun registerNamedStructType(
        name: String,
        elementTypes: List<space.norb.llvm.core.Type>? = null,
        isPacked: Boolean = false
    ): StructType.NamedStructType {
        require(name !in namedStructTypes) { "Struct type with name '$name' already exists in module" }

        val structType = if (elementTypes != null) {
            createNamedStructType(name, elementTypes, isPacked)
        } else {
            createOpaqueStructType(name)
        }

        namedStructTypes[name] = structType
        return structType
    }

    /**
     * Registers an opaque named struct type with this module.
     * 
     * @param name The unique name for the opaque struct type
     * @return The registered opaque named struct type
     * @throws IllegalArgumentException if a struct with the same name already exists
     */
    fun registerOpaqueStructType(name: String): StructType.NamedStructType {
        return registerNamedStructType(name, null, false)
    }

    /**
     * Completes a previously registered opaque struct type by defining its element types.
     * 
     * @param name The name of the opaque struct to complete
     * @param elementTypes The element types to define
     * @param isPacked Whether this is a packed struct
     * @return The updated named struct type
     * @throws IllegalArgumentException if no opaque struct with the given name exists
     * @throws IllegalArgumentException if the struct is already complete
     */
    fun completeOpaqueStructType(
        name: String,
        elementTypes: List<space.norb.llvm.core.Type>,
        isPacked: Boolean = false
    ): StructType.NamedStructType {
        val existingStruct = namedStructTypes[name]
            ?: throw IllegalArgumentException("No struct type with name '$name' exists in module")

        require(existingStruct.isOpaque()) { "Struct type '$name' is already complete" }

        return existingStruct.complete(elementTypes, isPacked)
    }

    /**
     * Gets a named struct type by name.
     * 
     * @param name The name of the struct type
     * @return The named struct type, or null if not found
     */
    fun getNamedStructType(name: String): StructType.NamedStructType? {
        return namedStructTypes[name]
    }

    /**
     * Checks if a named struct type with the given name exists in this module.
     * 
     * @param name The name to check
     * @return true if the struct type exists, false otherwise
     */
    fun hasNamedStructType(name: String): Boolean {
        return name in namedStructTypes
    }

    /**
     * Gets or creates an anonymous struct type, ensuring deduplication.
     * 
     * @param elementTypes The element types of the anonymous struct
     * @param isPacked Whether this is a packed struct
     * @return The anonymous struct type (existing or newly created)
     */
    fun getOrCreateAnonymousStructType(
        elementTypes: List<space.norb.llvm.core.Type>,
        isPacked: Boolean = false
    ): StructType.AnonymousStructType {
        val newStruct = StructType.AnonymousStructType(elementTypes, isPacked)

        // Find existing struct with same structure
        val existingStruct = anonymousStructTypes.find { it == newStruct }
        return existingStruct ?: newStruct.also { anonymousStructTypes.add(it) }
    }

    /**
     * Returns all named struct types registered in this module.
     * The iteration order is deterministic (sorted by name).
     * 
     * @return A list of all named struct types in name order
     */
    fun getAllNamedStructTypes(): List<StructType.NamedStructType> {
        return namedStructTypes.values.sortedBy { it.name }
    }

    /**
     * Returns all anonymous struct types used in this module.
     * The iteration order is deterministic (sorted by string representation).
     * 
     * @return A list of all anonymous struct types
     */
    fun getAllAnonymousStructTypes(): List<StructType.AnonymousStructType> {
        return anonymousStructTypes.sortedBy { it.toString() }
    }

    /**
     * Returns all struct types (both named and anonymous) in this module.
     * Named structs appear first in name order, followed by anonymous structs.
     * 
     * @return A list of all struct types in deterministic order
     */
    fun getAllStructTypes(): List<StructType> {
        return getAllNamedStructTypes() + getAllAnonymousStructTypes()
    }
}
