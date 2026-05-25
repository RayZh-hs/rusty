package space.norb.llvm.instructions.base

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.visitors.IRVisitor

/**
 * Abstract base class for other instructions that don't fit into
 * the other categories (terminator, binary, memory, cast).
 *
 * This category includes miscellaneous instructions that perform
 * various operations in LLVM IR.
 *
 * Examples of other instructions include:
 * - CallInst: function call instruction
 * - ICmpInst: integer comparison instruction
 * - FCmpInst: floating-point comparison instruction
 * - PhiNode: PHI node for SSA form
 * - SelectInst: conditional selection instruction
 * - VAArgInst: variable argument access
 * - ExtractElementInst: extract element from vector
 * - InsertElementInst: insert element into vector
 * - ShuffleVectorInst: shuffle vector elements
 * - ExtractValueInst: extract value from aggregate
 * - InsertValueInst: insert value into aggregate
 * - LandingPadInst: exception landing pad
 * - FreezeInst: freeze instruction for poison values
 */
abstract class OtherInst(
    name: String?,
    type: Type,
    operands: List<Value>
) : Instruction(name, type, operands) {
    
    /**
     * Accept method for the visitor pattern.
     * All other instructions must implement this to support
     * the visitor pattern for IR traversal and processing.
     */
    abstract override fun <T> accept(visitor: IRVisitor<T>): T
    
    /**
     * Gets the opcode name for this instruction.
     * This is used for IR printing and debugging.
     */
    abstract fun getOpcodeName(): String
    
    /**
     * Checks if this instruction may have side effects.
     * This is important for optimization and code motion.
     */
    open fun mayHaveSideEffects(): Boolean = false
    
    /**
     * Checks if this instruction may read from memory.
     */
    open fun mayReadFromMemory(): Boolean = false
    
    /**
     * Checks if this instruction may write to memory.
     */
    open fun mayWriteToMemory(): Boolean = false
    
    /**
     * Checks if this instruction is pure (no side effects and doesn't access memory).
     * Pure instructions can be freely moved and optimized.
     */
    open fun isPure(): Boolean = !mayHaveSideEffects() && !mayReadFromMemory() && !mayWriteToMemory()
    
    /**
     * Checks if this instruction has the same semantics as another instruction.
     * This is useful for common subexpression elimination.
     */
    open fun isSameOperationAs(other: OtherInst): Boolean {
        if (this::class != other::class) return false
        if (type != other.type) return false
        if (operands.size != other.operands.size) return false
        return operands.zip(other.operands).all { (a, b) -> a == b }
    }
}
