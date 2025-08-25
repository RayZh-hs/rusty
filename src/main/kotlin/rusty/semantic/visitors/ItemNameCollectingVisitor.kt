package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.core.utils.associateUniquelyBy
import rusty.parser.nodes.ASTNode
import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.ParamsNode
import rusty.parser.nodes.PathInExpressionNode
import rusty.parser.nodes.PathIndentSegmentNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.StatementNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.support.ConditionsNode
import rusty.parser.nodes.support.FunctionParamNode
import rusty.parser.nodes.utils.Visitor
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticFunctionParamNode
import rusty.semantic.support.SemanticSelfNode
import rusty.semantic.support.Symbol

class ItemNameCollectingVisitor(val ctx: Context) : Visitor<Unit> {
    private var scopeCursor = ctx.scopeTree

    private fun <R> withinNewScope(parentNode: ASTNode, childName: String? = null, action: () -> R): R {
        val childScope = scopeCursor.addChildScope(childPointer = parentNode.pointer, childName = childName)
        scopeCursor = childScope
        return action().afterWhich {
            scopeCursor = scopeCursor.parent ?: ctx.scopeTree // Reset to global scope if parent is null
        }
    }

    fun run() {
        assert(scopeCursor == ctx.scopeTree) { "Scope cursor should start at the prelude scope" }
        visit(ctx.astTree)
    }

    // Crate
    override fun visitCrate(node: CrateNode) {
        withinNewScope(node, "TopLevelItem") {
            node.items.forEach { visit(it) }
        }
    }

    // Patterns
    override fun visitPattern(node: PatternNode) = Unit
    override fun visitLiteralPattern(node: SupportingPatternNode.LiteralPatternNode) = Unit
    override fun visitIdentifierPattern(node: SupportingPatternNode.IdentifierPatternNode) = Unit
    override fun visitWildcardPattern(node: SupportingPatternNode.WildcardPatternNode) = Unit
    override fun visitDestructuredTuplePattern(node: SupportingPatternNode.DestructuredTuplePatternNode) = Unit
    override fun visitPathPattern(node: SupportingPatternNode.PathPatternNode) = Unit

    // Expressions with block: Build new scope
    override fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) {
        withinNewScope(node, "Block") {
            node.statements.forEach { visit(it) }
            node.trailingExpression?.let { visit(it) }
        }
    }
    override fun visitConstBlockExpression(node: ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode) {
        visit(node.expression)
    }
    override fun visitLoopBlockExpression(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode) {
        visit(node.expression)
    }
    override fun visitWhileBlockExpression(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode) {
        visit(node.condition)
        visit(node.expression)
    }
    override fun visitIfBlockExpression(node: ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode) {
        node.ifs.forEach {
            visit(it.condition)
            visit(it.then)
        }
        node.elseBranch?.let { visit(it) }
    }
    override fun visitMatchBlockExpression(node: ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode) {
        visit(node.scrutinee)
        node.matchArmsNode.arms.zip(node.matchArmsNode.values)
            .forEach { (arm, value) ->
                if (arm.guard != null) {
                    visit(arm.guard)
                }
                visit(value)
            }
    }

    // Literal expressions
    override fun visitI32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode) = Unit
    override fun visitISizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode) = Unit
    override fun visitU32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode) = Unit
    override fun visitUSizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode) = Unit
    override fun visitStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode) = Unit
    override fun visitCharLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode) = Unit
    override fun visitBoolLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode) = Unit

    // Non-block expressions
    override fun visitUnderscoreExpression(node: ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode) = Unit
    override fun visitTupleExpression(node: ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode) = Unit
    override fun visitArrayExpression(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode) = Unit
    override fun visitStructExpression(node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode) = Unit
    override fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode) = Unit
    override fun visitIndexExpression(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode) = Unit
    override fun visitFieldExpression(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode) = Unit
    override fun visitPathExpression(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode) = Unit
    override fun visitTupleIndexing(node: ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode) = Unit
    override fun visitInfixOperator(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode) = Unit
    override fun visitPrefixOperator(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode) = Unit

    // Control flow expressions
    override fun visitReturnExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode) = Unit
    override fun visitBreakExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode) = Unit
    override fun visitContinueExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode) = Unit

    // Statements: Let leads to a new symbol
    override fun visitNullStatement(node: StatementNode.NullStatementNode) = Unit
    override fun visitItemStatement(node: StatementNode.ItemStatementNode) = Unit
    override fun visitLetStatement(node: StatementNode.LetStatementNode) = Unit
    override fun visitExpressionStatement(node: StatementNode.ExpressionStatementNode) = Unit

    // Items: Declare new symbols
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        val selfParam = node.functionParamsNode.selfParam?.let { SemanticSelfNode.from(it) }
        val funcParams = node.functionParamsNode.functionParams.map {
            when (it) {
                is FunctionParamNode.FunctionParamTypedPatternNode -> SemanticFunctionParamNode(
                    pattern = it.pattern,
                )
                else -> throw CompileError("Removed from Spec: Unexpected function parameter type $it")
                    .with(ctx).at(node.functionParamsNode.pointer)
            }
        }
        ctx.scopeTree.functionST.declare(Symbol.Function(
            identifier = node.identifier,
            definedAt = node,
            selfParam = Slot(selfParam),
            funcParams = Slot(funcParams),
        ))
        withinNewScope(node, "FunctionParam") {
            // Placeholder: Here the parameters should be added to the scope
            node.withBlockExpressionNode?.let { visit(it) }
        }
    }
    override fun visitStructItem(node: ItemNode.StructItemNode) {
        ctx.scopeTree.structEnumST.declare(Symbol.Struct(
            identifier = node.identifier,
            definedAt = node
        ))
    }
    override fun visitEnumItem(node: ItemNode.EnumItemNode) {
        ctx.scopeTree.structEnumST.declare(Symbol.Enum(
            identifier = node.identifier,
            definedAt = node,
            elements = Slot(node.variants.map { it.identifier }),
        ))
    }
    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        ctx.scopeTree.variableConstantST.declare(Symbol.Const(
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
        ctx.scopeTree.structEnumST.declare(Symbol.Trait(
            identifier = node.identifier,
            definedAt = node,
            functions = Slot(functions),
            constants = Slot(constants),
        ))
    }

    // Implements will be injected by the ImplementInjectingVisitor (Phase 2)
    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) = Unit
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) = Unit

    // Types
    override fun visitTypePath(node: TypeNode.TypePath) = Unit
    override fun visitNeverType(node: TypeNode.NeverType) = Unit
    override fun visitTupleType(node: TypeNode.TupleType) = Unit
    override fun visitArrayType(node: TypeNode.ArrayType) = Unit
    override fun visitSliceType(node: TypeNode.SliceType) = Unit
    override fun visitReferenceType(node: TypeNode.ReferenceType) = Unit
    override fun visitInferredType(node: TypeNode.InferredType) = Unit

    // Params
    override fun visitGenericParams(node: ParamsNode.GenericParamsNode) = Unit
    override fun visitFunctionParams(node: ParamsNode.FunctionParamsNode) = Unit

    // Paths
    override fun visitPathIndentSegment(node: PathIndentSegmentNode) = Unit
    override fun visitPathInExpression(node: PathInExpressionNode) = Unit

    // Misc
    override fun visitConditions(node: ConditionsNode) {
        visit(node.expression)
    }
}