package rusty.ir

import rusty.ir.support.IRContext
import rusty.ir.support.visitors.FunctionRegistrar
import rusty.ir.support.visitors.FunctionBodyGenerator
import rusty.ir.support.visitors.PreludeHandler
import rusty.ir.support.visitors.StructHandler
import rusty.semantic.support.SemanticContext

class IRConstructor {
    companion object {
        fun run(semanticContext: SemanticContext, dumpToScreen: Boolean = false): String {
            IRContext.reset()
            val module = IRContext.module.also {
                PreludeHandler(semanticContext).run()
                StructHandler(semanticContext).run()
                FunctionRegistrar(semanticContext).run()
                FunctionBodyGenerator(semanticContext).run()
            }
            val irString = module.toIRString()
            if (dumpToScreen) {
                dumpScreen(irString)
            }
            return irString
        }
    }
}
