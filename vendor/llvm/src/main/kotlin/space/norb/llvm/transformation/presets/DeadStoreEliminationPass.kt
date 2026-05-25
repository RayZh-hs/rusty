package space.norb.llvm.transformation.presets

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass

object DeadStoreEliminationPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        for (function in module.functions) {
            if (function.isDeclaration) continue
            for (block in function.basicBlocks) {
                eliminateDeadStoresInBlock(block)
            }
        }
        return module
    }

    private fun eliminateDeadStoresInBlock(block: space.norb.llvm.structure.BasicBlock) {
        val lastStore = mutableMapOf<space.norb.llvm.core.Value, StoreInst>()
        val deadStores = mutableSetOf<StoreInst>()

        // Iterate backwards through instructions
        for (instruction in block.instructions.asReversed()) {
            when (instruction) {
                is StoreInst -> {
                    val ptr = instruction.pointer
                    val existing = lastStore[ptr]
                    if (existing != null) {
                        deadStores.add(instruction)
                    }
                    lastStore[ptr] = instruction
                }

                is LoadInst -> {
                    // The loaded value is used, so any preceding store to this exact pointer is not dead.
                    lastStore.remove(instruction.pointer)
                }

                is AllocaInst -> {
                    // Alloca allocates stack space but does not clobber existing memory stores in a way that affects DSE.
                }

                is MemoryInst -> {
                    if (instruction.mayReadFromMemory() || instruction.mayWriteToMemory()) {
                        // Conservative: unknown memory operation may alias any tracked store.
                        lastStore.clear()
                    }
                }

                is OtherInst -> {
                    if (instruction.mayHaveSideEffects() ||
                        instruction.mayReadFromMemory() ||
                        instruction.mayWriteToMemory()
                    ) {
                        // Conservative: side-effecting instruction may read/write memory.
                        lastStore.clear()
                    }
                }
            }
        }

        if (deadStores.isNotEmpty()) {
            block.instructions.removeAll(deadStores)
        }
    }
}
