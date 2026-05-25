package space.norb.llvm.instructions.other

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId

/**
 * PHI node for SSA form.
 *
 * PHI nodes are used in LLVM IR to implement the Static Single Assignment (SSA) form.
 * They select a value based on which basic block control flow came from. Each PHI node
 * has a list of incoming values, each paired with the basic block from which that
 * value comes.
 *
 * PHI nodes can only appear at the beginning of a basic block, before any other
 * instructions. They are used to merge values from different control flow paths.
 *
 * IR output examples:
 * ```
 * ; Simple PHI node
 * %result = phi i32 [ %val1, %entry ], [ %val2, %loop ], [ %val3, %exit ]
 *
 * ; PHI node with pointer values (all pointers are "ptr" type)
 * %ptr = phi ptr [ %ptr1, %bb1 ], [ %ptr2, %bb2 ]
 *
 * ; PHI node in a loop header
 * %counter = phi i32 [ 0, %entry ], [ %next, %loop_body ]
 * ```
 *
 * Key characteristics:
 * - Must be the first instructions in a basic block
 * - Each incoming value is paired with a predecessor basic block
 * - The number of incoming values must match the number of predecessors
 * - All incoming values must have compatible types
 * - Pure operation with no side effects
 */
class PhiNode private constructor(
    name: String?,
    type: Type,
    incomingValues: List<Pair<Value, BasicBlock>>,
    private val allowEmptyIncoming: Boolean = false
) : OtherInst(name, type, incomingValues.flatMap { listOf(it.first, it.second) }) {
    val incomingValues: List<Pair<Value, BasicBlock>>
        get() = getOperandsList()
            .chunked(2)
            .map { operands -> operands[0] to operands[1] as BasicBlock }

    /**
     * The list of incoming values (without their source blocks).
     */
    val values: List<Value> get() = incomingValues.map { it.first }
    
    /**
     * The list of source basic blocks.
     */
    val blocks: List<BasicBlock> get() = incomingValues.map { it.second }
    
    init {
        // Validate that we have at least one incoming value
        if (!allowEmptyIncoming && incomingValues.isEmpty()) {
            throw IllegalArgumentException("PhiNode must have at least one incoming value")
        }
        
        // Validate that all incoming values have compatible types
        val firstValueType = incomingValues.firstOrNull()?.first?.type
        for ((value, block) in incomingValues) {
            // Validate value type compatibility
            if (firstValueType != null && !areTypesCompatible(firstValueType, value.type)) {
                throw IllegalArgumentException(
                    "All incoming values must have compatible types: expected $firstValueType, got ${value.type}"
                )
            }
            
            // Validate that the block is actually a basic block
            if (block.type.toString() != "label") {
                throw IllegalArgumentException(
                    "Incoming block must be a basic block (label type), got ${block.type}"
                )
            }
        }
        
        // Check for duplicate blocks
        val blockSet = mutableSetOf<BasicBlockId>()
        for ((_, block) in incomingValues) {
            if (!blockSet.add(block.id)) {
                throw IllegalArgumentException("Duplicate incoming block: ${block.name}")
            }
        }
    }
    
    /**
     * Validates that two types are compatible for PHI node merging.
     *
     * @param type1 The first type
     * @param type2 The second type
     * @return true if types are compatible
     */
    private fun areTypesCompatible(type1: Type, type2: Type): Boolean {
        return when {
            // Same types are always compatible
            type1 == type2 -> true
            
            // All pointers are compatible in untyped mode
            type1.isPointerType() && type2.isPointerType() -> true
            
            // Integer types of the same width are compatible
            type1.isIntegerType() && type2.isIntegerType() -> {
                val bits1 = type1.getPrimitiveSizeInBits()
                val bits2 = type2.getPrimitiveSizeInBits()
                bits1 != null && bits1 == bits2
            }
            
            else -> false
        }
    }
    
    /**
     * Gets the number of incoming values.
     *
     * @return The number of incoming values
     */
    fun getNumIncomingValues(): Int = incomingValues.size
    
    /**
     * Gets the incoming value at the specified index.
     *
     * @param index The index of the incoming value
     * @return The (value, block) pair at the specified index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    fun getIncomingValue(index: Int): Pair<Value, BasicBlock> {
        return incomingValues[index]
    }
    
    /**
     * Gets the incoming value (without block) at the specified index.
     *
     * @param index The index of the incoming value
     * @return The value at the specified index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    fun getIncomingValueWithoutBlock(index: Int): Value {
        return incomingValues[index].first
    }
    
    /**
     * Gets the incoming block at the specified index.
     *
     * @param index The index of the incoming block
     * @return The block at the specified index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    fun getIncomingBlock(index: Int): BasicBlock {
        return incomingValues[index].second
    }
    
    /**
     * Finds the incoming value for the specified basic block.
     *
     * @param block The basic block to search for
     * @return The incoming value from the specified block, or null if not found
     */
    fun getIncomingValueForBlock(block: BasicBlock): Value? {
        return incomingValues.find { it.second == block }?.first
    }
    
    /**
     * Checks if this PHI node has an incoming value from the specified block.
     *
     * @param block The basic block to check
     * @return true if there's an incoming value from the block
     */
    fun hasIncomingValueForBlock(block: BasicBlock): Boolean {
        return incomingValues.any { it.second == block }
    }
    
    /**
     * Gets the index of the incoming value from the specified block.
     *
     * @param block The basic block to search for
     * @return The index of the incoming value, or `null` if not found
     */
    fun getIncomingValueIndexForBlock(block: BasicBlock): Int? =
        incomingValues.indexOfFirst { it.second == block }.takeIf { it >= 0 }
    
    /**
     * Adds a new incoming value to this PHI node.
     * Note: This is a convenience method for building PHI nodes during construction.
     * In a real LLVM implementation, PHI nodes are immutable after creation.
     *
     * @param value The new incoming value
     * @param block The basic block from which the value comes
     * @return A new PhiNode with the additional incoming value
     * @throws IllegalArgumentException if validation fails
     */
    fun addIncoming(value: Value, block: BasicBlock): PhiNode {
        val newIncomingValues = incomingValues + Pair(value, block)
        return PhiNode(name, type, newIncomingValues)
    }

    fun addIncomingMutable(value: Value, block: BasicBlock) {
        if (!areTypesCompatible(type, value.type)) {
            throw IllegalArgumentException(
                "All incoming values must have compatible types: expected $type, got ${value.type}"
            )
        }
        if (hasIncomingValueForBlock(block)) {
            throw IllegalArgumentException("Duplicate incoming block: ${block.name}")
        }
        addOperand(value)
        addOperand(block)
    }

    /**
     * Removes the incoming value associated with the specified basic block.
     *
     * @param block The basic block whose incoming value to remove
     */
    fun removeIncomingForBlock(block: BasicBlock) {
        val index = getIncomingValueIndexForBlock(block) ?: return
        // Remove the higher index first so the lower index is not affected
        removeOperand(index * 2 + 1) // block operand
        removeOperand(index * 2)     // value operand
    }
    
    /**
     * Creates a new PHI node with the incoming value from the specified block replaced.
     *
     * @param block The basic block whose incoming value to replace
     * @param newValue The new incoming value
     * @return A new PhiNode with the replaced incoming value
     * @throws IllegalArgumentException if the block is not found or validation fails
     */
    fun replaceIncomingValueForBlock(block: BasicBlock, newValue: Value): PhiNode {
        val index = getIncomingValueIndexForBlock(block)
            ?: throw IllegalArgumentException("No incoming value found for block: ${block.name}")

        val newIncomingValues = incomingValues.toMutableList()
        newIncomingValues[index] = Pair(newValue, block)
        return PhiNode(name, type, newIncomingValues)
    }
    
    /**
     * Checks if this PHI node is equivalent to another PHI node.
     * Two PHI nodes are equivalent if they have the same type and the same
     * incoming values from the same blocks (regardless of order).
     *
     * @param other The other PHI node to compare with
     * @return true if the PHI nodes are equivalent
     */
    fun isEquivalentTo(other: PhiNode): Boolean {
        if (type != other.type) return false
        if (incomingValues.size != other.incomingValues.size) return false
        
        // Create maps of block -> value for comparison
        val thisMap = incomingValues.associate { it.second to it.first }
        val otherMap = other.incomingValues.associate { it.second to it.first }
        
        return thisMap == otherMap
    }
    
    override fun getOpcodeName(): String = "phi"
    
    override fun mayHaveSideEffects(): Boolean = false
    
    override fun mayReadFromMemory(): Boolean = false
    
    override fun mayWriteToMemory(): Boolean = false
    
    override fun isPure(): Boolean = true
    
    override fun isSameOperationAs(other: OtherInst): Boolean {
        if (!super.isSameOperationAs(other)) return false
        if (other !is PhiNode) return false
        return isEquivalentTo(other)
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitPhiNode(this)
    
    companion object {
        /**
         * Creates a PHI node with the specified incoming values.
         *
         * @param name The name of the PHI node result
         * @param type The type of the PHI node (must match incoming value types)
         * @param incomingValues List of (value, basic block) pairs
         * @return A new PhiNode
         * @throws IllegalArgumentException if validation fails
         */
        fun create(name: String?, type: Type, incomingValues: List<Pair<Value, BasicBlock>>): PhiNode {
            return PhiNode(name, type, incomingValues)
        }

        fun createPlaceholder(name: String?, type: Type): PhiNode {
            return PhiNode(name, type, emptyList(), allowEmptyIncoming = true)
        }
        
        /**
         * Creates a PHI node with a single incoming value.
         * This is useful during construction when more values will be added later.
         *
         * @param name The name of the PHI node result
         * @param type The type of the PHI node
         * @param value The initial incoming value
         * @param block The basic block from which the value comes
         * @return A new PhiNode with one incoming value
         */
        fun createSingle(name: String?, type: Type, value: Value, block: BasicBlock): PhiNode {
            return PhiNode(name, type, listOf(Pair(value, block)))
        }
        
        /**
         * Creates a PHI node from separate value and block lists.
         *
         * @param name The name of the PHI node result
         * @param type The type of the PHI node
         * @param values List of incoming values
         * @param blocks List of corresponding basic blocks
         * @return A new PhiNode
         * @throws IllegalArgumentException if lists have different sizes or validation fails
         */
        fun createFromLists(name: String?, type: Type, values: List<Value>, blocks: List<BasicBlock>): PhiNode {
            if (values.size != blocks.size) {
                throw IllegalArgumentException(
                    "Values and blocks lists must have the same size: ${values.size} vs ${blocks.size}"
                )
            }
            
            val incomingValues = values.zip(blocks)
            return PhiNode(name, type, incomingValues)
        }
    }
}
