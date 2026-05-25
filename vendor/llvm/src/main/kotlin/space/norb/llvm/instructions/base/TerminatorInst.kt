package space.norb.llvm.instructions.base

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.visitors.IRVisitor

/**
 * Abstract base class for all terminator instructions.
 *
 * Terminator instructions are instructions that can only appear at the end of a basic block.
 * They control the flow of execution by determining which basic block will be executed next.
 *
 * Examples of terminator instructions include:
 * - ReturnInst: returns from a function
 * - BranchInst: conditional or unconditional branch
 * - SwitchInst: multi-way branch
 * - InvokeInst: call with exception handling
 * - ResumeInst: resume exception propagation
 * - UnreachableInst: indicates unreachable code
 */
abstract class TerminatorInst(
    name: String?,
    type: Type,
    operands: List<Value>
) : Instruction(name, type, operands) {
    
    /**
     * Accept method for the visitor pattern.
     * All terminator instructions must implement this to support
     * the visitor pattern for IR traversal and processing.
     */
    abstract override fun <T> accept(visitor: IRVisitor<T>): T
    
    /**
     * Checks if this terminator instruction terminates the function
     * (i.e., returns from the function).
     */
    open fun isFunctionTerminating(): Boolean = false
    
    /**
     * Gets the list of successor basic blocks that this terminator
     * may transfer control to.
     */
    abstract fun getSuccessors(): List<space.norb.llvm.structure.BasicBlock>
}
