package rusty.ir

import rusty.ir.support.IRContext
import rusty.ir.support.visitors.StructHandler
import rusty.semantic.support.SemanticContext

class IRConstructor {
    companion object {
        fun run(semanticContext: SemanticContext, dumpToScreen: Boolean = false): String {
            val module = IRContext.module.also {
                // Phase 1: Fill in the structs and constants
                StructHandler(semanticContext).run()
            }
            val irString = module.toIRString()
            if (dumpToScreen) {
                dumpScreen(irString)
            }
            return irString
        }
    }
}