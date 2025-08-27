package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.utils.getStructIdFromType
import rusty.semantic.visitors.utils.injectAssociatedItems
import rusty.semantic.visitors.utils.sequentialLookup

class ImplementInjectorVisitor(override val ctx: Context) : ScopeAwareVisitorBase(ctx) {

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        val identifier = getStructIdFromType(ctx, node.typeNode)
        val semanticStruct = sequentialLookup(identifier, currentScope(), {it.structEnumST})
            ?: throw CompileError("Cannot find struct or enum with id $identifier to implement").with(node)
        when (semanticStruct.symbol) {
            is SemanticSymbol.Struct -> {
                semanticStruct.symbol.injectAssociatedItems(ctx, node.associatedItems)
            }
            is SemanticSymbol.Enum -> {
                semanticStruct.symbol.injectAssociatedItems(ctx, node.associatedItems)
            }
            else -> throw CompileError("Expected struct or enum symbol, found ${semanticStruct.symbol}").with(node)
        }
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        val identifier = getStructIdFromType(ctx, node.typeNode)
        val semanticStruct = sequentialLookup(identifier, currentScope(), {it.structEnumST})
            ?: throw CompileError("Cannot find struct or enum with id $identifier to implement").with(node)
        when (semanticStruct.symbol) {
            is SemanticSymbol.Struct -> {
                semanticStruct.symbol.injectAssociatedItems(ctx, node.associatedItems)
            }
            is SemanticSymbol.Enum -> {
                semanticStruct.symbol.injectAssociatedItems(ctx, node.associatedItems)
            }
            else -> throw CompileError("Expected struct or enum symbol, found ${semanticStruct.symbol}").with(node)
        }
    }
}