package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.core.utils.VolatileWithDefault
import rusty.core.utils.associateUniquelyBy
import rusty.parser.nodes.ASTNode
import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.visitors.bases.SimpleVisitorBase
import rusty.semantic.visitors.utils.newFunctionSignature

class ItemNameCollectorVisitor(override val ctx: Context) : SimpleVisitorBase(ctx) {
    private var scopeCursor = ctx.scopeTree
    private var kindAnnotator = VolatileWithDefault(Scope.ScopeKind.Normal)

    private fun <R> withinNewScope(parentNode: ASTNode, childName: String? = null, kind: Scope.ScopeKind, action: () -> R): R {
        val childScope = scopeCursor.addChildScope(childPointer = parentNode.pointer, childName = childName, childKind = kind)
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
        kindAnnotator.write(Scope.ScopeKind.Crate)
        withinNewScope(node, "Crate", kindAnnotator.readOrDefault()) {
            // Delegate traversal to base
            super.visitCrate(node)
        }
    }

    // Expressions with block: Build new scope (keep only scope logic; traversal via base)
    override fun visitBlockExpression(node: rusty.parser.nodes.ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) {
        withinNewScope(node, "Block", kindAnnotator.readOrDefault()) {
            super.visitBlockExpression(node)
        }
    }

    // Items: Declare new symbols
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        // Declare into the current scope, not the root
        val signature = newFunctionSignature(ctx, node)
        scopeCursor.functionST.declare(signature)
        // Enter a child scope to hold parameters/body
        withinNewScope(node, "FunctionParam", Scope.ScopeKind.FunctionParams) {
            if (node.withBlockExpressionNode != null)
                kindAnnotator.write(Scope.ScopeKind.FunctionBody)
            super.visitFunctionItem(node)
        }
    }
    override fun visitStructItem(node: ItemNode.StructItemNode) {
        val fields = node.fields.associate { it.identifier to Slot<SemanticType>()}
        val definesType = SemanticType.StructType(node.identifier, fields)
        scopeCursor.typeST.declare(SemanticSymbol.Struct(
            identifier = node.identifier,
            definedAt = node,
            definesType = definesType,
        ))
    }
    override fun visitEnumItem(node: ItemNode.EnumItemNode) {
        val elements = node.variants.map { it.identifier }
        val definesType = SemanticType.EnumType(node.identifier, Slot(elements))
        scopeCursor.typeST.declare(SemanticSymbol.Enum(
            identifier = node.identifier,
            definedAt = node,
            definesType = definesType,
        ))
    }
    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        scopeCursor.variableST.declare(SemanticSymbol.Const(
            identifier = node.identifier,
            definedAt = node,
        ))
    }
    override fun visitTraitItem(node: ItemNode.TraitItemNode) {
        withinNewScope(node, "Trait", Scope.ScopeKind.Trait) {
            val functions = node.associatedItems.functionItems.map {
                visitFunctionItem(it)
                scopeCursor.functionST.resolve(it.identifier) as SemanticSymbol.Function
            }.associateUniquelyBy({it.identifier},
                exception = { CompileError("Duplicate function $it in trait found").with(ctx).at(node.pointer) })
            val constants = node.associatedItems.constItems.map {
                visitConstItem(it)
                scopeCursor.variableST.resolve(it.identifier) as SemanticSymbol.Const
            }.associateUniquelyBy({it.identifier},
                exception = { CompileError("Duplicate constant $it in trait found").with(ctx).at(node.pointer) })
            val traitType = SemanticType.TraitType(node.identifier)
            scopeCursor.typeST.declare(SemanticSymbol.Trait(
                identifier = node.identifier,
                definedAt = node,
                functions = functions,
                constants = constants,
                definesType = traitType,
            ))
        }
    }

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        withinNewScope(node, "Impl", Scope.ScopeKind.Implement) {
            // Delegate traversal to base
            super.visitInherentImplItem(node)
        }
    }
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        withinNewScope(node, "Impl", Scope.ScopeKind.Implement) {
            // Delegate traversal to base
            super.visitTraitImplItem(node)
        }
    }

    override fun visitWhileBlockExpression(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode) {
        visit(node.condition)
        kindAnnotator.write(Scope.ScopeKind.Repeat)
        visit(node.expression)
    }

    override fun visitLoopBlockExpression(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode) {
        kindAnnotator.write(Scope.ScopeKind.Repeat)
        visit(node.expression)
    }
}