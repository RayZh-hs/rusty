package rusty.parser.nodes

import rusty.parser.putils.Context


data class CrateNode(val items: List<ItemNode>) {
    companion object {
        val name get() = "Crate"

        fun parse(ctx: Context): CrateNode {
            ctx.callMe(name) {
                val itemNodeList: MutableList<ItemNode> = mutableListOf()
                while (!ctx.stream.atEnd())
                    itemNodeList.add(ItemNode.parse(ctx))
                return CrateNode(itemNodeList)
            }
        }
    }
}
