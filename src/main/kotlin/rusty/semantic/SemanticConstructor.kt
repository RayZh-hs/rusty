package rusty.semantic

import rusty.parser.ASTTree
import rusty.semantic.support.Context
import rusty.semantic.visitors.ItemNameCollectingVisitor

typealias InputType = ASTTree
typealias SemanticContext = Context
typealias OutputType = SemanticContext

class SemanticConstructor {
    companion object {
        fun run(astTree: InputType): OutputType {
            val context = Context(astTree = astTree)
            ItemNameCollectingVisitor(context)
            return context
        }
    }
}