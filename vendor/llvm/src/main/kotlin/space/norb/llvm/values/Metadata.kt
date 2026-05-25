package space.norb.llvm.values

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.types.MetadataType
import space.norb.llvm.visitors.IRVisitor

/**
 * Abstract base class for all LLVM metadata values.
 *
 * Metadata in LLVM IR is used to store additional information about the program
 * that doesn't affect the actual execution but is useful for debugging, optimization,
 * and other tools. Metadata is distinct from regular values - it cannot be used
 * as operands to most instructions and has special handling in the IR.
 *
 * Common types of metadata include:
 * - MDString: String metadata
 * - MDNode: Complex metadata nodes containing other metadata
 * - ConstantAsMetadata: Wraps regular constants as metadata
 *
 * Metadata values have the following properties:
 * - They have the metadata type
 * - They cannot be used as operands to most instructions
 * - They are primarily used for debugging and optimization information
 * - They can be attached to instructions, functions, and global variables
 *
 * This abstract class provides the common interface for all metadata implementations.
 */
abstract class Metadata : Value {
    
    /**
     * All metadata values have the metadata type.
     */
    override val type: Type = MetadataType
    
    /**
     * Accepts a visitor for this metadata value.
     * This is part of the visitor pattern implementation for IR traversal.
     *
     * @param visitor The visitor to accept
     * @param T The return type of the visitor
     * @return Result of visiting this metadata
     */
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitMetadata(this)
    
    /**
     * Gets the unique identifier for this metadata value.
     * In LLVM IR, metadata typically doesn't have names unless explicitly specified.
     *
     * @return Unique identifier string
     */
    override fun getIdentifier(): String {
        return name ?: "metadata_${hashCode()}"
    }
    
    /**
     * Checks if this metadata can be attached to the given value.
     * Most metadata can be attached to instructions, functions, and global variables.
     *
     * @return true if this metadata can be attached to values, false otherwise
     */
    open fun canAttachToValues(): Boolean = true
    
    /**
     * Checks if this metadata is distinct.
     * Distinct metadata nodes are always treated as unique, even if they have
     * the same content as other metadata nodes.
     *
     * @return true if this metadata is distinct, false otherwise
     */
    open fun isDistinct(): Boolean = false
    
    /**
     * Gets a string representation of this metadata in LLVM IR format.
     * This is used when generating LLVM IR text.
     *
     * @return The LLVM IR string representation of this metadata
     */
    abstract fun toIRString(): String
    
    override fun getParent(): Any? {
        // Metadata typically doesn't have a parent in the same way
        // Could be attached to various entities, but for Phase 1, return null
        return null
    }
}

/**
 * Metadata string value in LLVM IR.
 *
 * MDString represents string metadata in LLVM. It's one of the simplest
 * forms of metadata and is commonly used for names, paths, and other
 * string-based information.
 *
 * @property value The string value of this metadata
 * @property name The name of this metadata (`null` for unnamed metadata)
 */
data class MDString(
    val value: String,
    override val name: String? = null
) : Metadata() {
    
    override fun toIRString(): String = "!\"$value\""
    
    /**
     * Checks if this metadata string is empty.
     *
     * @return true if the string value is empty, false otherwise
     */
    fun isEmpty(): Boolean = value.isEmpty()
    
    /**
     * Gets the length of this metadata string.
     *
     * @return The length of the string value
     */
    fun getLength(): Int = value.length
}

/**
 * Metadata node value in LLVM IR.
 *
 * MDNode represents complex metadata that can contain other metadata values
 * as operands. This is the most flexible form of metadata and is used for
 * complex structures like debug information, type information, etc.
 *
 * @property operands The list of metadata operands
 * @property name The name of this metadata (`null` for unnamed metadata)
 * @property distinct Whether this metadata node is distinct
 */
data class MDNode(
    val operands: List<Metadata>,
    override val name: String? = null,
    val distinct: Boolean = false
) : Metadata() {
    
    override fun isDistinct(): Boolean = distinct
    
    override fun toIRString(): String {
        val prefix = if (distinct) "!distinct " else "!"
        val operandsStr = operands.joinToString(", ") { it.toIRString() }
        return "$prefix{$operandsStr}"
    }
    
    /**
     * Gets the number of operands in this metadata node.
     *
     * @return The number of operands
     */
    fun getNumOperands(): Int = operands.size
    
    /**
     * Gets the operand at the specified index.
     *
     * @param index The index of the operand to get
     * @return The metadata operand at the specified index
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    fun getOperand(index: Int): Metadata {
        require(index in operands.indices) { 
            "Operand index $index out of bounds for metadata node with ${operands.size} operands" 
        }
        return operands[index]
    }
    
    /**
     * Checks if this metadata node is empty (has no operands).
     *
     * @return true if this metadata node has no operands, false otherwise
     */
    fun isEmpty(): Boolean = operands.isEmpty()
}

/**
 * Constant metadata value in LLVM IR.
 *
 * ConstantAsMetadata wraps a regular constant value as metadata.
 * This allows regular values to be used in metadata contexts while
 * maintaining their value semantics.
 *
 * @property value The constant value wrapped as metadata
 * @property name The name of this metadata (`null` for unnamed metadata)
 */
data class ConstantAsMetadata(
    val value: space.norb.llvm.core.Constant,
    override val name: String? = null
) : Metadata() {
    
    override fun toIRString(): String = value.toString()
    
    /**
     * Gets the wrapped constant value.
     *
     * @return The constant value
     */
    fun getConstant(): space.norb.llvm.core.Constant = value
    
    /**
     * Gets the type of the wrapped constant.
     *
     * @return The type of the wrapped constant
     */
    fun getConstantType(): Type = value.type
}
