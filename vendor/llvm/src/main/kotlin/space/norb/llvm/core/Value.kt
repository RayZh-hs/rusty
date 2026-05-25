package space.norb.llvm.core

import java.util.IdentityHashMap

internal object ValueUseRegistry {
    private val uses: MutableMap<Value, MutableMap<User, Int>> = IdentityHashMap()

    fun registerUse(value: Value, user: User) {
        val users = uses.getOrPut(value) { IdentityHashMap() }
        users[user] = (users[user] ?: 0) + 1
    }

    fun unregisterUse(value: Value, user: User) {
        val users = uses[value] ?: return
        val count = users[user] ?: return

        if (count <= 1) {
            users.remove(user)
            if (users.isEmpty()) {
                uses.remove(value)
            }
        } else {
            users[user] = count - 1
        }
    }

    fun getUses(value: Value): List<User> = uses[value]?.keys?.toList() ?: emptyList()

    fun hasUses(value: Value): Boolean = uses[value]?.isNotEmpty() == true
}

/**
 * Core interface for all LLVM values.
 *
 * This is the fundamental abstraction in the LLVM IR system. All LLVM entities
 * that can be used as operands (constants, instructions, functions, etc.) implement
 * this interface. It provides the basic contract that all values must follow.
 *
 * Values in LLVM have:
 * - A type that determines what kind of value it is
 * - An optional name for identification (`null` for unnamed values)
 * - Methods for querying properties and relationships
 *
 * This interface is designed to be extended by:
 * - [Constant] - Immutable constant values
 * - [User] - Values that use other values as operands (like instructions)
 * - [Function] - Function definitions
 * - [Argument] - Function arguments
 * - [GlobalVariable] - Global variables
 */
interface Value {
    /**
     * The name of this value. `null` means the value is unnamed.
     * In LLVM IR, values can be named (e.g., %x, %result) or unnamed.
     */
    val name: String?
    
    /**
     * The LLVM type of this value.
     * Every value in LLVM has a specific type that determines what operations
     * can be performed on it.
     */
    val type: Type
    
    /**
     * Checks if this value is a constant.
     * Constants are immutable values that can be used as operands.
     *
     * @return true if this value is a constant, false otherwise
     */
    fun isConstant(): Boolean = this is Constant
    
    /**
     * Checks if this value is a user (uses other values as operands).
     * Instructions are the primary users in LLVM IR.
     *
     * @return true if this value uses other values, false otherwise
     */
    fun isUser(): Boolean = this is User
    
    /**
     * Gets the unique identifier for this value.
     * In LLVM IR, this would be the name with appropriate prefix.
     *
     * @return Unique identifier string
     */
    fun getIdentifier(): String = name ?: "unnamed"
    
    /**
     * Accepts a visitor for this value.
     * This is part of the visitor pattern implementation for IR traversal.
     *
     * @param visitor The visitor to accept
     * @param T The return type of the visitor
     * @return Result of visiting this value
     */
    fun <T> accept(visitor: space.norb.llvm.visitors.IRVisitor<T>): T
    
    /**
     * Gets the parent containing this value.
     * The parent could be a Module, Function, or BasicBlock depending on the value type:
     * - Functions belong to Modules
     * - BasicBlocks belong to Functions
     * - Instructions belong to BasicBlocks
     * - Arguments belong to Functions
     * - GlobalVariables belong to Modules
     * - Constants typically don't have parents unless they are global initializers
     */
    fun getParent(): Any? {
        // Base implementation - concrete classes should override
        // The parent could be a Module, Function, or BasicBlock depending on the value type
        return null
    }
    
    /**
     * Checks if this value has any uses.
     */
    fun hasUses(): Boolean = ValueUseRegistry.hasUses(this)

    /**
     * Gets all users that reference this value as an operand.
     */
    fun getUses(): List<User> = ValueUseRegistry.getUses(this)

    /**
     * Replaces every current use of this value with [newValue].
     */
    fun replaceAllUsesWith(newValue: Value) {
        for (user in getUses().toList()) {
            user.replaceUsesOfWith(this, newValue)
        }
    }
}
