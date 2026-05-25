package space.norb.llvm.instructions.base

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.PointerType

/**
 * Abstract base class for all memory instructions.
 *
 * Memory instructions operate on memory locations through pointers.
 * These instructions can read from, write to, or allocate memory.
 *
 * Examples of memory instructions include:
 * - AllocaInst: allocates memory on the stack
 * - LoadInst: reads a value from memory
 * - StoreInst: writes a value to memory
 * - GetElementPtrInst: computes pointer addresses
 * - AtomicRMWInst: atomic read-modify-write operations
 * - CmpXchgInst: atomic compare-and-swap operations
 *
 * In the untyped pointer model, all pointers are of type "ptr" regardless
 * of the type of data they point to.
 */
abstract class MemoryInst(
    name: String?,
    type: Type,
    operands: List<Value>
) : Instruction(name, type, operands) {
    
    /**
     * Accept method for the visitor pattern.
     * All memory instructions must implement this to support
     * the visitor pattern for IR traversal and processing.
     */
    abstract override fun <T> accept(visitor: IRVisitor<T>): T
    
    /**
     * Gets the pointer operand(s) used by this memory instruction.
     * Most memory instructions have exactly one pointer operand,
     * but some (like atomic operations) may have more.
     */
    abstract fun getPointerOperands(): List<Value>
    
    /**
     * Checks if this memory instruction may read from memory.
     */
    abstract fun mayReadFromMemory(): Boolean
    
    /**
     * Checks if this memory instruction may write to memory.
     */
    abstract fun mayWriteToMemory(): Boolean
    
    /**
     * Checks if this memory instruction has volatile semantics.
     * Volatile operations cannot be optimized away or reordered.
     */
    open fun isVolatile(): Boolean = false
    
    /**
     * Validates that pointer operands are of the correct type.
     * In the untyped pointer model, all pointer operands should be of type "ptr".
     */
    protected fun validatePointerOperands() {
        getPointerOperands().forEach { operand ->
            if (operand.type != PointerType) {
                throw IllegalArgumentException("Memory instruction pointer operand must be of type 'ptr', got: ${operand.type}")
            }
        }
    }
}
