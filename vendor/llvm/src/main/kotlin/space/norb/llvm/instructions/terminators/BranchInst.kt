package space.norb.llvm.instructions.terminators

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.visitors.IRVisitor

/**
 * Branch instruction (conditional and unconditional).
 *
 * A branch instruction can be either:
 * - Unconditional: transfers control to a single destination
 * - Conditional: transfers control to one of two destinations based on a condition
 */
class BranchInst private constructor(
    name: String?,
    type: Type,
    operands: List<Value>
) : TerminatorInst(name, type, operands) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitBranchInst(this)
    
    override fun getSuccessors(): List<BasicBlock> {
        return when {
            isUnconditional() -> listOf(getDestination() as BasicBlock)
            isConditional() -> listOf(getTrueDestination() as BasicBlock, getFalseDestination() as BasicBlock)
            else -> emptyList()
        }
    }
    
    /**
     * Checks if this is an unconditional branch.
     */
    fun isUnconditional(): Boolean = operands.size == 1
    
    /**
     * Checks if this is a conditional branch.
     */
    fun isConditional(): Boolean = operands.size == 3
    
    /**
     * Gets the condition value for conditional branches.
     * @return The condition value, or null for unconditional branches
     */
    fun getCondition(): Value? = if (isConditional()) operands[0] else null
    
    /**
     * Gets the destination basic block for unconditional branches.
     * @return The destination basic block
     * @throws IllegalStateException if this is a conditional branch
     */
    fun getDestination(): Value {
        check(isUnconditional()) { "getDestination() can only be called on unconditional branches" }
        return operands[0]
    }
    
    /**
     * Gets the true destination basic block for conditional branches.
     * @return The true destination basic block
     * @throws IllegalStateException if this is an unconditional branch
     */
    fun getTrueDestination(): Value {
        check(isConditional()) { "getTrueDestination() can only be called on conditional branches" }
        return operands[1]
    }
    
    /**
     * Gets the false destination basic block for conditional branches.
     * @return The false destination basic block
     * @throws IllegalStateException if this is an unconditional branch
     */
    fun getFalseDestination(): Value {
        check(isConditional()) { "getFalseDestination() can only be called on conditional branches" }
        return operands[2]
    }
    
    companion object {
        /**
         * Creates an unconditional branch instruction.
         *
         * @param name The name of the instruction
         * @param type The type of the instruction (should be void)
         * @param destination The destination basic block
         * @return An unconditional branch instruction
         */
        fun createUnconditional(name: String?, type: Type, destination: Value): BranchInst {
            require(destination is BasicBlock) { "Destination must be a BasicBlock" }
            return BranchInst(name, type, listOf(destination))
        }
        
        /**
         * Creates a conditional branch instruction.
         *
         * @param name The name of the instruction
         * @param type The type of the instruction (should be void)
         * @param condition The condition value (must be i1 type)
         * @param trueDestination The destination when condition is true
         * @param falseDestination The destination when condition is false
         * @return A conditional branch instruction
         */
        fun createConditional(
            name: String?,
            type: Type,
            condition: Value,
            trueDestination: Value,
            falseDestination: Value
        ): BranchInst {
            require(condition.type.toString() == "i1") { "Condition must be of type i1" }
            require(trueDestination is BasicBlock) { "True destination must be a BasicBlock" }
            require(falseDestination is BasicBlock) { "False destination must be a BasicBlock" }
            require(trueDestination != falseDestination) { "True and false destinations must be different" }
            
            return BranchInst(name, type, listOf(condition, trueDestination, falseDestination))
        }
    }
}