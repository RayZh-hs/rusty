package rusty.semantic

import rusty.parser.ASTTree
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.Context
import rusty.semantic.visitors.ImplementInjectorVisitor
import rusty.semantic.visitors.ItemNameCollectorVisitor

typealias InputType = ASTTree
typealias SemanticContext = Context
typealias OutputType = SemanticContext

class SemanticConstructor {
    companion object {
        fun run(astTree: InputType, dumpToScreen: Boolean = false): OutputType {
            val context = Context(astTree = astTree)
            ItemNameCollectorVisitor(context).run().afterWhich { if (dumpToScreen) dumpScreenPhase("phase-1:item-name-collection", context) }
            ImplementInjectorVisitor(context).run().afterWhich { if (dumpToScreen) dumpScreenPhase("phase-2:implement-injection", context) }
            return context
        }
    }
}