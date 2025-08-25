package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.core.utils.associateUniquelyBy
import rusty.parser.nodes.ASTNode
import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.Context
import rusty.semantic.support.Symbol
import rusty.semantic.visitors.bases.SimpleVisitorBase
import rusty.semantic.visitors.utils.newFunctionSignature

class ItemNameCollectingVisitor(override val ctx: Context) : SimpleVisitorBase(ctx) {
    private var scopeCursor = ctx.scopeTree

    private fun <R> withinNewScope(parentNode: ASTNode, childName: String? = null, action: () -> R): R {
        val childScope = scopeCursor.addChildScope(childPointer = parentNode.pointer, childName = childName)
        scopeCursor = childScope
        return action().afterWhich {
            scopeCursor = scopeCursor.parent ?: ctx.scopeTree // Reset to global scope if parent is null
        }
    }

    override fun run() {
        assert(scopeCursor == ctx.scopeTree) { "Scope cursor should start at the prelude scope" }
        super.run()
    }

    // Crate
    override fun visitCrate(node: CrateNode) {
        withinNewScope(node, "Crate") {
            // Delegate traversal to base
            super.visitCrate(node)
        }
    }

    // Expressions with block: Build new scope (keep only scope logic; traversal via base)
    override fun visitBlockExpression(node: rusty.parser.nodes.ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) {
        withinNewScope(node, "Block") {
            super.visitBlockExpression(node)
        }
    }

    // Items: Declare new symbols
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        // Declare into the current scope, not the root
        val signature = newFunctionSignature(ctx, node)
        scopeCursor.functionST.declare(signature)
        // Enter a child scope to hold parameters/body
        withinNewScope(node, "FunctionParam") {
            super.visitFunctionItem(node)
        }
    }
    override fun visitStructItem(node: ItemNode.StructItemNode) {
    scopeCursor.structEnumST.declare(Symbol.Struct(
            identifier = node.identifier,
            definedAt = node
        ))
    }
    override fun visitEnumItem(node: ItemNode.EnumItemNode) {
    scopeCursor.structEnumST.declare(Symbol.Enum(
            identifier = node.identifier,
            definedAt = node,
            elements = Slot(node.variants.map { it.identifier }),
        ))
    }
    override fun visitConstItem(node: ItemNode.ConstItemNode) {
    scopeCursor.variableConstantST.declare(Symbol.Const(
            identifier = node.identifier,
            definedAt = node,
        ))
    }
    override fun visitTraitItem(node: ItemNode.TraitItemNode) {
        val functions = node.associatedItems.functionItems.map {
            Symbol.Function(it.identifier, node)
        }.associateUniquelyBy({it.identifier},
            exception = { CompileError("Duplicate function $it in trait found").with(ctx).at(node.pointer) })
        val constants = node.associatedItems.constItems.map {
            Symbol.Const(it.identifier, node)
        }.associateUniquelyBy({it.identifier},
            exception = { CompileError("Duplicate constant $it in trait found").with(ctx).at(node.pointer) })
    scopeCursor.structEnumST.declare(Symbol.Trait(
            identifier = node.identifier,
            definedAt = node,
            functions = Slot(functions),
            constants = Slot(constants),
        ))
    }

    // Implements will be injected by the ImplementInjectingVisitor (Phase 2)
    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) = Unit
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) = Unit
}