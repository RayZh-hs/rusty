package rusty.parser.nodes

import rusty.parser.putils.Context
import rusty.parser.putils.putilsContextHasHitEOF
import rusty.parser.putils.putilsZeroOrMore


data class CrateNode(val items: List<ItemNode>) {
    companion object {
        val name get() = "Crate"

        fun parse(ctx: Context): CrateNode {
            ctx.callMe(name) {
                val itemNodeList = putilsZeroOrMore(
                    ctx, ItemNode::parse, ::putilsContextHasHitEOF
                )
                return CrateNode(itemNodeList)
            }
        }
    }
}
