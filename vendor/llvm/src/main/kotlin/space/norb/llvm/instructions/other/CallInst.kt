package space.norb.llvm.instructions.other

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.VoidType

/**
 * Function call instruction.
 *
 * This instruction represents a function call in LLVM IR. It can be used for both
 * direct calls (calling a specific function) and indirect calls (calling through
 * a function pointer).
 *
 * The call instruction follows the untyped pointer model where all pointers are
 * of type "ptr" regardless of the function signature.
 *
 * IR output examples:
 * ```
 * ; Direct call
 * %result = call i32 @my_function(i32 %arg1, i32 %arg2)
 *
 * ; Indirect call through function pointer
 * %result = call i32 %func_ptr(i32 %arg1, i32 %arg2)
 *
 * ; Call to function returning void
 * call void @printf(ptr @msg)
 * ```
 *
 * Key characteristics:
 * - Supports both direct and indirect function calls
 * - All function pointers are of type "ptr" in untyped mode
 * - Validates argument count and types against function signature
 * - May have side effects (calls to external functions)
 * - May read from and write to memory
 */
class CallInst private constructor(
    name: String?,
    type: Type,
    callee: Value,
    args: List<Value>,
    private val isDirectCall: Boolean
) : OtherInst(name, type, listOf(callee) + args) {
    
    /**
     * The function or function pointer being called.
     * For direct calls, this is a Function value.
     * For indirect calls, this is a pointer value.
     */
    val callee: Value
        get() = getOperand(0)
    
    /**
     * The arguments passed to the function.
     */
    val arguments: List<Value>
        get() = getOperandsList().drop(1)
    
    /**
     * The function type of the callee.
     * This is extracted from the callee's type for validation.
     */
    val functionType: FunctionType
    
    init {
        if (type == VoidType && !name.isNullOrEmpty()) {
            throw IllegalArgumentException("Call instructions returning void cannot have result names")
        }
        // For indirect calls, we need to handle the case where we don't have function type information
        // The validation should be deferred to the createIndirectCall method
        this.functionType = when {
            callee.type.isFunctionType() -> callee.type as FunctionType
            callee.type.isPointerType() -> {
                // For indirect calls, we create a dummy function type since we don't have the actual type
                // The actual validation happens in createIndirectCall
                FunctionType(space.norb.llvm.types.VoidType, emptyList(), false)
            }
            else -> {
                throw IllegalArgumentException("Callee must have function type or be a function pointer")
            }
        }
        
        // Only validate for direct calls where we have the actual function type
        if (isDirectCall && callee.type.isFunctionType()) {
            // Validate argument count
            if (!functionType.isVarArg && args.size != functionType.paramTypes.size) {
                throw IllegalArgumentException(
                    "Argument count mismatch: expected ${functionType.paramTypes.size}, got ${args.size}"
                )
            }
            
            if (functionType.isVarArg && args.size < functionType.paramTypes.size) {
                throw IllegalArgumentException(
                    "Variadic function requires at least ${functionType.paramTypes.size} arguments, got ${args.size}"
                )
            }
            
            // Validate argument types for fixed parameters
            for (i in functionType.paramTypes.indices) {
                if (i < args.size) {
                    val expectedType = functionType.paramTypes[i]
                    val actualType = args[i].type
                    
                    // In untyped pointer mode, all pointers are compatible
                    if (!isTypeCompatible(expectedType, actualType)) {
                        throw IllegalArgumentException(
                            "Argument type mismatch at position $i: expected $expectedType, got $actualType"
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Checks if this is a direct function call.
     *
     * @return true if this is a direct call to a named function
     */
    fun isDirectCall(): Boolean = isDirectCall
    
    /**
     * Checks if this is an indirect function call through a function pointer.
     *
     * @return true if this is an indirect call through a pointer
     */
    fun isIndirectCall(): Boolean = !isDirectCall
    
    /**
     * Gets the return type of the called function.
     *
     * @return The return type of the function
     */
    fun getReturnType(): Type = functionType.returnType

    /**
     * Checks if this call produces an SSA value.
     */
    fun producesValue(): Boolean = type != VoidType
    
    /**
     * Gets the parameter types of the called function.
     *
     * @return List of parameter types
     */
    fun getParameterTypes(): List<Type> = functionType.paramTypes
    
    /**
     * Checks if the called function is variadic.
     *
     * @return true if the function is variadic
     */
    fun isVariadicCall(): Boolean = functionType.isVarArg
    
    /**
     * Gets the number of arguments passed to this call.
     *
     * @return The argument count
     */
    fun getArgumentCount(): Int = arguments.size
    
    /**
     * Gets the argument at the specified index.
     *
     * @param index The argument index
     * @return The argument value
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    fun getArgument(index: Int): Value {
        return arguments[index]
    }
    
    override fun getOpcodeName(): String = "call"
    
    override fun mayHaveSideEffects(): Boolean = true
    
    override fun mayReadFromMemory(): Boolean = true
    
    override fun mayWriteToMemory(): Boolean = true
    
    override fun isPure(): Boolean = false
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitCallInst(this)
    
    companion object {
        /**
         * Creates a direct function call instruction.
         *
         * @param name The name of the instruction result
         * @param callee The function being called
         * @param args The arguments to pass to the function
         * @return A new CallInst for a direct function call
         * @throws IllegalArgumentException if argument validation fails
         */
        fun createDirectCall(name: String?, callee: Value, args: List<Value>): CallInst {
            val functionType = callee.type as? FunctionType
                ?: throw IllegalArgumentException("Direct calls require function values")
            return CallInst(name, functionType.returnType, callee, args, true)
        }
        
        /**
         * Creates an indirect function call instruction through a function pointer.
         *
         * @param name The name of the instruction result
         * @param returnType The return type of the function
         * @param funcPtr The function pointer to call
         * @param args The arguments to pass to the function
         * @return A new CallInst for an indirect function call
         * @throws IllegalArgumentException if argument validation fails
         */
        fun createIndirectCall(name: String?, returnType: Type, funcPtr: Value, args: List<Value>): CallInst {
            if (!funcPtr.type.isPointerType()) {
                throw IllegalArgumentException("Function pointer must have pointer type")
            }
            return CallInst(name, returnType, funcPtr, args, false)
        }
        
        /**
         * Checks if two types are compatible in untyped pointer mode.
         *
         * @param expected The expected type
         * @param actual The actual type
         * @return true if types are compatible
         */
        private fun isTypeCompatible(expected: Type, actual: Type): Boolean {
            // In untyped pointer mode, all pointers are compatible
            return when {
                expected.isPointerType() && actual.isPointerType() -> true
                expected == actual -> true
                else -> false
            }
        }
    }
}
