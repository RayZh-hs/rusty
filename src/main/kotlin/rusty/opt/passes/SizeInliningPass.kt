package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant
import space.norb.llvm.utils.getSizeInBytes

/**
 * IR pass that inlines sizeof helper functions into constant integer values.
 *
 * The compiler emits auxiliary functions named `aux.func.sizeof.<StructName>` that compute
 * the byte-size of a struct at runtime via pointer arithmetic. This pass:
 *
 * 1. Identifies those helper functions by name prefix.
 * 2. Computes the struct size statically using the library's layout utilities.
 * 3. Replaces every call to a sizeof helper with an `i32` constant.
 * 4. Removes the now-dead sizeof functions from the module.
 */
object SizeInliningPass : IRPass() {
    private const val SIZEOF_PREFIX = "aux.func.sizeof."

    override fun run(module: Module, am: AnalysisManager): Module {
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
            ?: module.getAllNamedStructTypes().find { it.name.endsWith(".$structName") }

        // If we can resolve the struct type, compute a constant replacement.
        val replacementConstant: IntConstant? = if (structType != null && !structType.isOpaque()) {
            IntConstant(structType.getSizeInBytes(module), IntegerType(32))
        } else {
            null
        }

        if (replacementConstant != null) {
            val callInsts = sizeofFn.getUses().filterIsInstance<CallInst>()
            for (callInst in callInsts) {
                callInst.replaceAllUsesWith(replacementConstant)
                module.functions
                    .flatMap { it.basicBlocks }
                    .firstOrNull { callInst in it.instructions }
                    ?.instructions?.remove(callInst)
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
}
