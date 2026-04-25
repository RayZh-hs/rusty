package rusty.asm.support

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.Value
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module

class AsmContext (
    val module: Module
) {
    val analysisManager: AnalysisManager = AnalysisManager(module)
    val stackManager: StackManager = StackManager()
    val registerAllocation: Map<Function, Map<Value, SavableSlot>> by lazy {
        RegisterAllocator.allocate(module, analysisManager)
    }
}
