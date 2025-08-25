package rusty.semantic.visitors

import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.visitors.bases.SimpleVisitorBase
import rusty.semantic.visitors.utils.getSemanticStructFromId
import rusty.semantic.visitors.utils.getStructIdFromType
import rusty.semantic.visitors.utils.injectAssociatedItems

class ImplementInjectingVisitor(override val ctx: Context) : SimpleVisitorBase(ctx) {

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        val structIdentifier = getStructIdFromType(ctx, node.typeNode)
        val semanticStruct = getSemanticStructFromId(ctx, structIdentifier, ctx.scopeTree)
        semanticStruct.injectAssociatedItems(ctx, node.associatedItems)
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        val structIdentifier = getStructIdFromType(ctx, node.typeNode)
        val semanticStruct = getSemanticStructFromId(ctx, structIdentifier, ctx.scopeTree)
        semanticStruct.injectAssociatedItems(ctx, node.associatedItems)
    }
}