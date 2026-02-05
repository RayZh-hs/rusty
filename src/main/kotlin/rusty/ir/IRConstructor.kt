package rusty.ir

import rusty.ir.support.IRContext
import rusty.ir.support.visitors.FunctionRegistrar
import rusty.ir.support.visitors.FunctionBodyGenerator
import rusty.ir.support.visitors.PreludeHandler
import rusty.ir.support.visitors.StructHandler
import rusty.ir.support.visitors.StructSizeFunctionGenerator
import rusty.semantic.support.SemanticContext
import space.norb.llvm.structure.Module

class IRConstructor {
    companion object {
        fun run(semanticContext: SemanticContext, dumpToScreen: Boolean = false): Module {
            IRContext.reset()
            val module = IRContext.module.also {
                PreludeHandler(semanticContext).run()
                StructHandler(semanticContext).run()
                StructSizeFunctionGenerator().run()
                FunctionRegistrar(semanticContext).run()
                FunctionBodyGenerator(semanticContext).run()
            }
            // Put outside the conditional so that it is always walked to capture express-time errors (if any)
            if (dumpToScreen) {
                dumpScreen(module)
            }
            return module
        }
    }
}
