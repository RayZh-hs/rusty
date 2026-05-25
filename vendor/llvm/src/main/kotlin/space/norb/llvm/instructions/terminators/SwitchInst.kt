package space.norb.llvm.instructions.terminators

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.visitors.IRVisitor

/**
 * Switch instruction for multi-way branching.
 *
 * A switch instruction transfers control to one of several destinations based on
 * the value of a condition. It consists of:
 * - A condition value to compare
 * - A default destination when no cases match
 * - A list of case-value/destination pairs
 */
class SwitchInst private constructor(
    name: String?,
    type: Type,
    operands: List<Value>,
    private val caseCount: Int
) : TerminatorInst(name, type, operands) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitSwitchInst(this)
    
    override fun getSuccessors(): List<BasicBlock> {
        val successors = mutableListOf<BasicBlock>()
        successors.add(getDefaultDestination() as BasicBlock)
        
        // Add all case destinations
        for (i in 0 until caseCount) {
            successors.add(getCaseDestination(i) as BasicBlock)
        }
        
        return successors
    }
    
    /**
     * Gets the condition value that is being compared.
     */
    fun getCondition(): Value = operands[0]
    
    /**
     * Gets the default destination basic block when no cases match.
     */
    fun getDefaultDestination(): Value = operands[1]
    
    /**
     * Gets the number of cases in this switch instruction.
     */
    fun getNumCases(): Int = caseCount
    
    /**
     * Gets the case value at the specified index.
     *
     * @param index The index of the case (0-based)
     * @return The case value
     * @throws IndexOutOfBoundsException if index is out of range
     */
    fun getCaseValue(index: Int): Value {
        require(index in 0 until caseCount) { "Case index $index out of range (0..${caseCount - 1})" }
        return operands[2 + index * 2]
    }
    
    /**
     * Gets the case destination at the specified index.
     *
     * @param index The index of the case (0-based)
     * @return The case destination basic block
     * @throws IndexOutOfBoundsException if index is out of range
     */
    fun getCaseDestination(index: Int): Value {
        require(index in 0 until caseCount) { "Case index $index out of range (0..${caseCount - 1})" }
        return operands[2 + index * 2 + 1]
    }
    
    /**
     * Gets all case-value/destination pairs.
     *
     * @return A list of pairs where each pair contains (case value, destination)
     */
    fun getCases(): List<Pair<Value, Value>> {
        val cases = mutableListOf<Pair<Value, Value>>()
        for (i in 0 until caseCount) {
            cases.add(Pair(getCaseValue(i), getCaseDestination(i)))
        }
        return cases
    }
    
    companion object {
        /**
         * Creates a switch instruction.
         *
         * @param name The name of the instruction
         * @param type The type of the instruction (should be void)
         * @param condition The condition value to compare
         * @param defaultDestination The default destination when no cases match
         * @param cases A list of case-value/destination pairs
         * @return A switch instruction
         */
        fun create(
            name: String?,
            type: Type,
            condition: Value,
            defaultDestination: Value,
            cases: List<Pair<Value, Value>> = emptyList()
        ): SwitchInst {
            require(defaultDestination is BasicBlock) { "Default destination must be a BasicBlock" }
            require(condition.type.isIntegerType()) { "Condition must be of integer type" }
            
            // Validate all cases
            for ((caseValue, caseDest) in cases) {
                require(caseValue.type.isIntegerType()) { "Case values must be of integer type" }
                require(caseDest is BasicBlock) { "Case destinations must be BasicBlocks" }
                require(caseValue.type == condition.type) {
                    "Case value type (${caseValue.type}) must match condition type (${condition.type})"
                }
            }
            
            // Check for duplicate case values
            val caseValues = cases.map { it.first }
            require(caseValues.size == caseValues.distinct().size) { "Duplicate case values are not allowed" }
            
            // Build operands list: condition, default destination, then case-value/destination pairs
            val operands = mutableListOf<Value>()
            operands.add(condition)
            operands.add(defaultDestination)
            
            for ((caseValue, caseDest) in cases) {
                operands.add(caseValue)
                operands.add(caseDest)
            }
            
            return SwitchInst(name, type, operands, cases.size)
        }
    }
}