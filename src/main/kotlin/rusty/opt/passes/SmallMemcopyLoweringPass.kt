package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant

/**
 * Lower small fixed-size aggregate copies into a single load/store of an integer that wide. The
 * frontend emits `aux.func.memfill(dst, src, size, 1)` as its memcpy shape.
 *
 *   call aux.func.memfill(dst, src, 8, 1)   ->   v = load i64 src ; store i64 v, dst
 *
 * Note: only fires for a repeat count of 1 and size 1..MAX_SCALAR_COPY_BYTES; larger copies keep the
 * call. Replacing the copy with one wide scalar load/store lets later passes forward the value.
 */
object SmallMemcopyLoweringPass : IRPass() {
    private const val MEMFILL_NAME = "aux.func.memfill"
    private const val MAX_SCALAR_COPY_BYTES = 32

    override fun run(module: Module, am: AnalysisManager): Module {
        var changed = false

        for (function in module.functions) {
            if (function.isDeclaration) continue
            var loweredIndex = 0
            for (block in function.basicBlocks) {
                val result = lowerCopiesInBlock(block, function, loweredIndex)
                loweredIndex = result.nextIndex
                changed = result.changed || changed
            }
        }

        if (changed) {
            am.invalidateAll()
        } else {
            am.invalidateNone()
        }
        return module
    }

    private fun lowerCopiesInBlock(block: BasicBlock, function: Function, initialIndex: Int): LoweringResult {
        var changed = false
        var loweredIndex = initialIndex
        var index = 0
        while (index < block.instructions.size) {
            val call = block.instructions[index] as? CallInst
            val copy = call?.toSmallMemcpy()
            if (copy == null) {
                index += 1
                continue
            }

            val scalarType = IntegerType(copy.sizeBytes * 8)
            val copyName = uniqueCopyName(copy.label, loweredIndex++)
            val load = LoadInst(
                "$copyName.load",
                scalarType,
                copy.sourcePtr
            )
            val store = StoreInst(
                null,
                scalarType,
                load,
                copy.destPtr
            )
            load.inlineComment = "lowered small memcpy"
            store.inlineComment = "lowered small memcpy"

            block.instructions.removeAt(index)
            block.instructions.add(index, store)
            block.instructions.add(index, load)
            changed = true
            index += 2
        }
        return LoweringResult(changed, loweredIndex)
    }

    private fun uniqueCopyName(label: String, index: Int): String =
        "${label.replace(Regex("[^A-Za-z0-9_.-]"), ".")}.$index"

    private fun CallInst.toSmallMemcpy(): SmallMemcpy? {
        val calleeName = (callee as? Function)?.name ?: return null
        if (calleeName != MEMFILL_NAME || arguments.size != 4) return null

        val repeatCount = arguments[3].asIntConstant() ?: return null
        if (repeatCount != 1) return null

        val sizeBytes = arguments[2].asIntConstant() ?: return null
        if (sizeBytes !in 1..MAX_SCALAR_COPY_BYTES) return null

        return SmallMemcpy(
            destPtr = arguments[0],
            sourcePtr = arguments[1],
            sizeBytes = sizeBytes,
            label = name ?: "memcpy"
        )
    }

    private fun Value.asIntConstant(): Int? =
        (this as? IntConstant)?.value?.toInt()

    private data class SmallMemcpy(
        val destPtr: Value,
        val sourcePtr: Value,
        val sizeBytes: Int,
        val label: String,
    )

    private data class LoweringResult(
        val changed: Boolean,
        val nextIndex: Int,
    )
}
