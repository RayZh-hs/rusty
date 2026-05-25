package space.norb.llvm.instructions.terminators

import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.visitors.IRVisitor

/**
 * Unreachable instruction.
 *
 * The unreachable instruction has no defined semantics. This instruction is
 * used to inform the optimizer that a particular portion of the code is not
 * reachable. This can be used to indicate that the code after a noreturn call
 * cannot be reached, and other facts.
 *
 * Syntax:
 *   unreachable
 *
 * The unreachable instruction has no successors.
 */
class UnreachableInst private constructor(
    name: String?,
    type: Type
) : TerminatorInst(name, type, emptyList()) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitUnreachableInst(this)
    
    override fun isFunctionTerminating(): Boolean = true
    
    override fun getSuccessors(): List<BasicBlock> = emptyList()
    
    companion object {
        /**
         * Creates an unreachable instruction.
         *
         * @param name The name of the instruction
         * @param type The type of the instruction (should be void)
         * @return An unreachable instruction
         */
        fun create(name: String?, type: Type): UnreachableInst {
            return UnreachableInst(name, type)
        }
    }
}
