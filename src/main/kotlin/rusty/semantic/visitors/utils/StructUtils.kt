package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.support.AssociatedItemsNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import kotlin.collections.set

fun getIdentifierFromType(ctx: Context, typeNode: TypeNode): String = when(typeNode) {
    is TypeNode.TypePath -> {
        if (typeNode.pathSegmentNode.token != Token.I_IDENTIFIER)
            throw CompileError("Impl expected a struct identifier, found: ${typeNode.pathSegmentNode}")
                .with(ctx).at(typeNode.pathSegmentNode.pointer)
        typeNode.pathSegmentNode.name?: throw CompileError("Cannot implement an unnamed struct")
            .with(ctx).at(typeNode.pathSegmentNode.pointer)
    }
    else -> throw CompileError("Cannot implement a non-path type: $typeNode")
        .with(ctx).at(typeNode.pointer)
}
