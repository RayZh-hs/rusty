package space.norb.llvm.structure

import space.norb.llvm.core.Value
import space.norb.llvm.types.LabelType
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.visitors.IRVisitor

typealias BasicBlockId = ULong

/**
 * Basic Block
 *
 * Represents a sequence of instructions with a single entry point and a single exit point.
 *
 * Basic blocks are fundamental units of control flow in LLVM IR, and they are used to structure the flow of execution within functions.
 * Each basic block contains a list of instructions and may end with a terminator instruction that defines the control flow to other basic blocks.
 *
 * @param name The name of the basic block, which serves as its identifier within the function.
 * @param function The parent function to which this basic block belongs.
 */
class BasicBlock(
    override val name: String?,
    val function: Function
) : Value {
    companion object {
        private var basicBlockId: BasicBlockId = 0UL
        private var basicBlockHashMap = mutableMapOf<BasicBlockId, BasicBlock>()
        private fun register(basicBlock: BasicBlock): BasicBlockId {
            val id = basicBlockId++
            basicBlockHashMap[id] = basicBlock
            return id
        }

        @Suppress("Unused")
        fun fromId(id: BasicBlockId) = basicBlockHashMap[id]
    }

    override val type: LabelType = LabelType
    val instructions: MutableList<Instruction> = mutableListOf()
    var terminator: TerminatorInst? = null
    val id: BasicBlockId = register(this)

    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitBasicBlock(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BasicBlock) return false
        // Equality is based on unique ID to ensure identity semantics
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "BasicBlock(name=$name, type=$type, function=${function.name})"
    }

    override fun getParent(): Any? {
        // Basic blocks belong to functions
        return function
    }

    fun getSuccessors(): List<BasicBlock> {
        return terminator?.getSuccessors() ?: emptyList()
    }
}
