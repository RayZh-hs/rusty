package rusty.parser.nodes

import rusty.core.CompilerPointer
import rusty.parser.nodes.utils.Parsable
import rusty.parser.putils.Context

@Parsable
data class CrateNode(val items: List<ItemNode>, override val pointer: CompilerPointer): ASTNode(pointer) {
    companion object {
        val name get() = "Crate"

        fun parse(ctx: Context): CrateNode {
            ctx.callMe(name) {
                val pointer = ctx.peekPointer()
                val itemNodeList: MutableList<ItemNode> = mutableListOf()
                while (!ctx.stream.atEnd())
                    itemNodeList.add(ItemNode.parse(ctx))
                return CrateNode(itemNodeList, pointer)
            }
        }
    }
}
