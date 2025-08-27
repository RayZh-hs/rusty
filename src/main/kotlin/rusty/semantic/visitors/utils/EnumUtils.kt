package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.parser.nodes.support.AssociatedItemsNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import kotlin.collections.set

fun SemanticSymbol.Enum.injectAssociatedItems(ctx: Context, node: AssociatedItemsNode) {
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
        this.constants[constItem.identifier] = SemanticSymbol.Const(
            identifier = constItem.identifier,
            definedAt = constItem,
        )
    }
}