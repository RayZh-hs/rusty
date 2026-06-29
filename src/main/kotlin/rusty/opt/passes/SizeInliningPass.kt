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
 * Inline `sizeof` helpers into constants. The frontend emits `aux.func.sizeof.<Struct>` helpers that
 * compute a struct's byte-size at runtime; this pass computes the size statically from the type layout,
 * replaces each call with the `i32` constant, and deletes the now-dead helper.
 *
 *   n = call aux.func.sizeof.Point()   ->   n = i32 12
 *
 * Note: a helper for an opaque or unresolvable struct is left untouched (no constant, not removed).
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
