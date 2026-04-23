package rusty.opt.passes

import rusty.core.MemoryLayout
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant

/**
 * IR pass that inlines sizeof helper functions into constant integer values.
 *
 * The compiler emits auxiliary functions named `aux.func.sizeof.<StructName>` that compute
 * the byte-size of a struct at runtime via pointer arithmetic. This pass:
 *
 * 1. Identifies those helper functions by name prefix.
 * 2. Computes the struct size statically using [MemoryLayout].
 * 3. Replaces every call to a sizeof helper with an `i32` constant.
 * 4. Removes the now-dead sizeof functions from the module.
 */
object SizeInliningPass : IRPass() {
    private const val SIZEOF_PREFIX = "aux.func.sizeof."

    override fun run(module: Module, am: AnalysisManager): Module {
        // Snapshot the sizeof functions so we can mutate module.functions safely.
        val sizeofFunctions = module.functions.filter { it.name?.startsWith(SIZEOF_PREFIX) == true }

        for (sizeofFn in sizeofFunctions) {
            processSizeofFunction(module, sizeofFn)
        }

        return module
    }

    private fun processSizeofFunction(module: Module, sizeofFn: Function) {
        val structName = sizeofFn.name!!.removePrefix(SIZEOF_PREFIX)
        // Struct types are registered with a prefix like "prelude.struct." or "user.struct.",
        // whereas the sizeof function only encodes the bare identifier.
        val structType = module.getNamedStructType(structName)
            ?: module.getAllNamedStructTypes().find { it.name?.endsWith(".$structName") == true }

        // Collect every CallInst that calls this sizeof function.
        val callsToReplace = mutableListOf<Pair<CallInst, BasicBlock>>()
        for (function in module.functions) {
            for (block in function.basicBlocks) {
                for (instruction in block.instructions) {
                    if (instruction is CallInst && instruction.callee == sizeofFn) {
                        callsToReplace.add(instruction to block)
                    }
                }
            }
        }

        // If we can resolve the struct type, compute a constant replacement.
        val replacementConstant: IntConstant? = if (structType != null && !structType.isOpaque()) {
            val layout = MemoryLayout.fromType(structType, module = module)
            val sizeInBytes = layout.size.toLong()
            IntConstant(sizeInBytes, IntegerType(32))
        } else {
            null
        }

        if (replacementConstant != null) {
            for ((callInst, block) in callsToReplace) {
                replaceAllUses(module, callInst, replacementConstant)
                block.instructions.remove(callInst)
            }
        }

        // Remove the sizeof function only when there are no remaining callers.
        val hasRemainingUses = module.functions.any { fn ->
            fn.basicBlocks.any { block ->
                block.instructions.any { inst ->
                    inst is CallInst && inst.callee == sizeofFn
                }
            }
        }

        if (!hasRemainingUses) {
            module.functions.remove(sizeofFn)
        }
    }

    private fun replaceAllUses(module: Module, oldValue: Value, newValue: Value) {
        for (function in module.functions) {
            for (block in function.basicBlocks) {
                for (instruction in block.instructions) {
                    if (instruction == oldValue) continue
                    replaceUses(instruction, oldValue, newValue)
                }
            }
        }
    }

    private fun replaceUses(user: User, oldValue: Value, newValue: Value) {
        for (index in 0 until user.getNumOperands()) {
            if (user.getOperand(index) == oldValue) {
                user.setOperand(index, newValue)
            }
        }
    }
}
