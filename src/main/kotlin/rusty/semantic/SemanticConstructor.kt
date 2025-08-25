package rusty.semantic

import rusty.parser.ASTTree
import rusty.semantic.support.Context
import rusty.semantic.visitors.ImplementInjectingVisitor
import rusty.semantic.visitors.ItemNameCollectingVisitor
import java.io.File

typealias InputType = ASTTree
typealias SemanticContext = Context
typealias OutputType = SemanticContext

class SemanticConstructor {
    companion object {
        fun run(astTree: InputType): OutputType {
            val context = Context(astTree = astTree)
            ItemNameCollectingVisitor(context).run()
            ImplementInjectingVisitor(context).run()
            return context
        }

        // Overload: run with dumping per phase
        fun runWithDumps(
            astTree: InputType,
            dumpToScreen: Boolean = true,
            dumpDir: String? = null
        ): OutputType {
            val context = Context(astTree = astTree)

            // Phase 1: item name collection
            ItemNameCollectingVisitor(context).run()
            if (dumpToScreen) dumpScreenPhase("phase-1:item-name-collection", context)
            dumpDir?.let { dir ->
                val path = File(dir, "semantic_phase1.txt").path
                dumpPhase("phase-1:item-name-collection", context, path)
            }

            // Phase 2: implement injection
            ImplementInjectingVisitor(context).run()
            if (dumpToScreen) dumpScreenPhase("phase-2:implement-injection", context)
            dumpDir?.let { dir ->
                val path = File(dir, "semantic_phase2.txt").path
                dumpPhase("phase-2:implement-injection", context, path)
            }

            return context
        }
    }
}