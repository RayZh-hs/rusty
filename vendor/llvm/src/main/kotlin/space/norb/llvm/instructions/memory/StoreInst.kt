package space.norb.llvm.instructions.memory

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.PointerType

/**
 * Store value to memory instruction.
 *
 * This instruction stores a value to an un-typed pointer, complying with the latest LLVM IR standard.
 * All pointers are of a single type regardless of the element type they point to.
 *
 * IR output example:
 * ```
 * store i32 42, ptr %ptr       ; Stores to un-typed pointer ptr
 * store float 3.14, ptr %fptr  ; Stores to un-typed pointer ptr
 * ```
 *
 * Key characteristics:
 * - All store instructions operate on "ptr" type regardless of element type
 * - Value type is explicitly specified in the store instruction
 * - Pointer type no longer conveys element type information
 * - Type safety is ensured through explicit type checking
 */
class StoreInst(
    name: String?,
    storedType: Type,
    value: Value,
    pointer: Value
) : MemoryInst(name, storedType, listOf(value, pointer)) {
    
    /**
     * The type of value being stored to memory.
     * This is explicitly specified since un-typed pointers don't convey this information.
     */
    val storedType: Type = storedType
    
    /**
     * The value to be stored.
     * The type of this value should match storedType.
     */
    val value: Value
        get() = getOperand(0)
    
    /**
     * The pointer operand to which to store the value.
     * In un-typed mode, this should be a PointerType.
     */
    val pointer: Value
        get() = getOperand(1)
    
    /**
     * The expected pointer type for this store operation.
     * In un-typed mode, this is PointerType.
     */
    val expectedPointerType: Type
        get() = PointerType
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitStoreInst(this)
    
    override fun getPointerOperands(): List<Value> = listOf(pointer)
    
    override fun mayReadFromMemory(): Boolean = false
    
    override fun mayWriteToMemory(): Boolean = true
}
