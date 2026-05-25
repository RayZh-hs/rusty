package space.norb.llvm.structure

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.visitors.IRVisitor

/**
 * Function Argument
 *
 * Represents an argument to a function in LLVM IR. Each argument has a name, type, and is associated with a parent function.
 * The index property indicates the position of the argument in the function's parameter list.
 *
 * @param name The name of the argument, which serves as its identifier within the function.
 * @param type The type of the argument, which must be a valid LLVM IR type.
 * @param function The parent function to which this argument belongs.
 * @param index The position of the argument in the function's parameter list, starting from 0.
 */
class Argument(
    override val name: String?,
    override val type: Type,
    val function: Function,
    val index: Int
) : Value {
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitArgument(this)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Argument) return false
        return name == other.name && type == other.type && function == other.function && index == other.index
    }
    
    override fun hashCode(): Int {
        return 31 * (name?.hashCode() ?: 0) + type.hashCode() + function.hashCode() + index
    }
    
    override fun toString(): String {
        return "Argument(name=$name, type=$type, function=${function.name}, index=$index)"
    }
    
    override fun getParent(): Any? {
        // Arguments belong to functions
        return function
    }
}