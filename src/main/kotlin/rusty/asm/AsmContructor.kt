package rusty.asm

import rusty.asm.support.AsmContext
import rusty.asm.support.RegisterAllocator

object AsmConstructor {
    fun run(asmContext: AsmContext, dumpToScreen: Boolean = false): String {
        materializeStackFrames(asmContext)
        TODO()
    }

    fun materializeStackFrames(
        asmContext: AsmContext,
        registerBytes: Int = RegisterAllocator.Config().registerBytes,
    ) {
        for ((function, allocation) in asmContext.registerAllocation) {
            asmContext.stackManager.materializeSpills(function, allocation, registerBytes)
        }
    }
}
