package space.norb.llvm.instructions.terminators

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.visitors.IRVisitor

/**
 * Function return instruction.
 *
 * A return instruction returns control flow from a function back to the caller.
 * It can optionally return a value.
 */
class ReturnInst(
    name: String?,
    type: Type,
    returnValue: Value? = null
) : TerminatorInst(name, type, returnValue?.let { listOf(it) } ?: emptyList()) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitReturnInst(this)
    
    override fun isFunctionTerminating(): Boolean = true
    
    override fun getSuccessors(): List<BasicBlock> = emptyList()
    
    /**
     * Checks if this return instruction returns a value.
     */
    fun hasReturnValue(): Boolean = operands.isNotEmpty()
    
    /**
     * Gets the return value.
     *
     * @return The return value, or null if this is a void return
     */
    fun getReturnValue(): Value? = if (hasReturnValue()) operands[0] else null
    
    companion object {
        /**
         * Creates a void return instruction.
         *
         * @param name The name of the instruction
         * @param type The type of the instruction (should be void)
         * @return A void return instruction
         */
        fun createVoid(name: String?, type: Type): ReturnInst {
            return ReturnInst(name, type, null)
        }
        
        /**
         * Creates a return instruction with a value.
         *
         * @param name The name of the instruction
         * @param type The type of the instruction (should be void)
         * @param returnValue The value to return
         * @return A return instruction with a value
         */
        fun createWithValue(name: String?, type: Type, returnValue: Value): ReturnInst {
            return ReturnInst(name, type, returnValue)
        }
    }
}