package space.norb.llvm.instructions.memory

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.PointerType

/**
 * Load value from memory instruction.
 *
 * This instruction loads a value from an un-typed pointer, complying with the latest LLVM IR standard.
 * All pointers are of a single type regardless of the element type they point to.
 *
 * IR output example:
 * ```
 * %val = load i32, ptr %ptr      ; Loads from un-typed pointer ptr
 * %fval = load float, ptr %fptr  ; Loads from un-typed pointer ptr
 * ```
 *
 * Key characteristics:
 * - All load instructions operate on "ptr" type regardless of element type
 * - Result type is explicitly specified in the load instruction
 * - Pointer type no longer conveys element type information
 * - Type safety is ensured through explicit type checking
 */
class LoadInst(
    name: String?,
    loadedType: Type,
    pointer: Value
) : MemoryInst(name, loadedType, listOf(pointer)) {
    
    /**
     * The type of value being loaded from memory.
     * This is explicitly specified since un-typed pointers don't convey this information.
     */
    val loadedType: Type = loadedType
    
    /**
     * The pointer operand from which to load the value.
     * In un-typed mode, this should be a PointerType.
     */
    val pointer: Value
        get() = getOperand(0)
    
    /**
     * The expected pointer type for this load operation.
     * In un-typed mode, this is PointerType.
     */
    val expectedPointerType: Type
        get() = PointerType
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitLoadInst(this)
    
    override fun getPointerOperands(): List<Value> = listOf(pointer)
    
    override fun mayReadFromMemory(): Boolean = true
    
    override fun mayWriteToMemory(): Boolean = false
}
