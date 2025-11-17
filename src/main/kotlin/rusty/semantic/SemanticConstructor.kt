package rusty.semantic

import rusty.parser.ASTTree
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.SemanticContext
import rusty.semantic.visitors.FunctionParamsDeclareVisitor
import rusty.semantic.visitors.FunctionTracerVisitor
import rusty.semantic.visitors.ImplementInjectorVisitor
import rusty.semantic.visitors.ItemNameCollectorVisitor
import rusty.semantic.visitors.ItemTypeResolverVisitor

typealias InputType = ASTTree
typealias SemanticContext = SemanticContext
typealias OutputType = SemanticContext

class SemanticConstructor {
    companion object {
        fun run(astTree: InputType, dumpToScreen: Boolean = false): OutputType {
            val context = SemanticContext(astTree = astTree)
            ItemNameCollectorVisitor(context).run().afterWhich { if (dumpToScreen) dumpScreenPhase("phase-1:item-name-collection", context) }
            ImplementInjectorVisitor(context).run().afterWhich { if (dumpToScreen) dumpScreenPhase("phase-2:implement-injection", context) }
            ItemTypeResolverVisitor(context).run().afterWhich { if (dumpToScreen) dumpScreenPhase("phase-3:item-type-resolution", context) }
            FunctionParamsDeclareVisitor(context).run().afterWhich { if (dumpToScreen) dumpScreenPhase("phase-4:function-params-declaration", context) }
            FunctionTracerVisitor(context).run().afterWhich { if (dumpToScreen) {
                dumpScreenPhase("phase-5:function-tracing", context)
                dumpScreenPhaseAstWithTypes("phase-6:ast-with-types", context)
            } }
            return context
        }
    }
}