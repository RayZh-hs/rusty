package rusty.asm

import rusty.asm.support.AsmContext
import rusty.asm.support.RegisterAllocator

object AsmConstructor {
    fun run(asmContext: AsmContext, dumpToScreen: Boolean = false): String {
        materializeStackFrames(asmContext)
        val output = AsmTranslator(asmContext).translate()
        if (dumpToScreen) print(output)
        return output
    }

    fun materializeStackFrames(
        asmContext: AsmContext,
        registerBytes: Int = RegisterAllocator.Config().registerBytes,
    ) {
        StackFrameMaterializer.materialize(asmContext, registerBytes)
    }
}
