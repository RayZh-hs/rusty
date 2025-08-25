package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.support.AssociatedItemsNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.Symbol
import kotlin.collections.set

fun getStructIdFromType(ctx: Context, typeNode: TypeNode): String = when(typeNode) {
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

fun getSemanticStructFromId(ctx: Context, identifier: String, startingPoint: Scope): Symbol.Struct {
    var pointer: Scope? = startingPoint
    while (pointer != null) {
        // Lookup in the current scope
        when (val lookup = pointer.structEnumST.resolve(identifier)) {
            is Symbol.Struct -> return lookup
            is Symbol.Enum -> throw CompileError("Expected struct identifier, found enum: $identifier")
                .with(ctx).at(pointer.annotation.pointer)
            null -> pointer = pointer.parent // Move to parent scope
            else -> throw IllegalStateException("Unexpected identifier: $lookup in structEnumST")
        }
    }
    throw CompileError("Struct '$identifier' not found in any accessible scope. Search starting from:")
        .with(startingPoint.annotation)
}

fun Symbol.Struct.injectAssociatedItems(ctx: Context, node: AssociatedItemsNode) {
    val implFunctions = node.functionItems
    val implConstants = node.constItems
    // Check for duplicates
    implFunctions.forEach { funcItem ->
        if (this.functions.containsKey(funcItem.identifier)) {
            throw CompileError("Duplicate function ${funcItem.identifier} in impl for struct $this")
                .with(ctx).at(funcItem.pointer)
        }
    }
    implConstants.forEach { constItem ->
        if (this.constants.containsKey(constItem.identifier)) {
            throw CompileError("Duplicate constant ${constItem.identifier} in impl for struct $this")
                .with(ctx).at(constItem.pointer)
        }
    }
    // Inject into the struct symbol
    implFunctions.forEach { funcItem ->
        this.functions[funcItem.identifier] = newFunctionSignature(ctx, funcItem)
    }
    implConstants.forEach { constItem ->
        this.constants[constItem.identifier] = Symbol.Const(
            identifier = constItem.identifier,
            definedAt = constItem,
        )
    }
}