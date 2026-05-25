package space.norb.llvm.instructions.memory

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.PointerType

/**
 * Stack memory allocation instruction.
 *
 * This instruction allocates memory on the stack and returns an un-typed pointer,
 * complying with the latest LLVM IR standard. All pointers are of a single type
 * regardless of the element type they point to.
 *
 * IR output example:
 * ```
 * %ptr = alloca i32        ; Returns ptr (un-typed pointer)
 * %fptr = alloca float     ; Returns ptr (un-typed pointer)
 * ```
 *
 * Key characteristics:
 * - All alloca instructions return "ptr" type regardless of element type
 * - Element type information is conveyed through the alloca type parameter
 * - Type information is preserved for type checking and validation
 */
class AllocaInst(
    name: String?,
    allocatedType: Type,
    arraySize: Value? = null
) : MemoryInst(name,
    PointerType,
    if (arraySize != null) listOf(arraySize) else emptyList()) {
    
    /**
     * The type of memory being allocated.
     * This is preserved for type information even when using un-typed pointers.
     */
    val allocatedType: Type = allocatedType
    
    /**
     * The pointer type returned by this alloca instruction.
     * In un-typed mode, this will be PointerType.
     */
    val resultType: Type
        get() = PointerType
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitAllocaInst(this)
    
    override fun getPointerOperands(): List<Value> {
        // Alloca doesn't have a pointer operand - it allocates memory and returns a pointer
        return emptyList()
    }
    
    override fun mayReadFromMemory(): Boolean = false
    
    override fun mayWriteToMemory(): Boolean = true
}