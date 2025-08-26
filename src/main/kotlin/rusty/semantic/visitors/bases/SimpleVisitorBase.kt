package rusty.semantic.visitors.bases

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.core.utils.associateUniquelyBy
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
import rusty.parser.nodes.utils.Visitor
import rusty.semantic.support.Context

open class SimpleVisitorBase(open val ctx: Context) : Visitor<Unit> {

    open fun run() {
        visit(ctx.astTree)
    }

    // Crate
    override fun visitCrate(node: CrateNode) {
        node.items.forEach { visit(it) }
    }

    // Patterns
    override fun visitPattern(node: PatternNode) {
        node.patternNodes.forEach { visit(it) }
    }
    override fun visitLiteralPattern(node: SupportingPatternNode.LiteralPatternNode) = Unit
    override fun visitIdentifierPattern(node: SupportingPatternNode.IdentifierPatternNode) {
        node.extendedByPatternNode?.let { visit(it) }
    }
    override fun visitWildcardPattern(node: SupportingPatternNode.WildcardPatternNode) = Unit
    override fun visitDestructuredTuplePattern(node: SupportingPatternNode.DestructuredTuplePatternNode) {
        node.tuple.forEach { visit(it) }
    }
    override fun visitPathPattern(node: SupportingPatternNode.PathPatternNode) {
        visit(node.path)
    }

    // Expressions with block: Build new scope
    override fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) {
        node.statements.forEach { visit(it) }
        node.trailingExpression?.let { visit(it) }
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
                // arm.pattern is inside Conditions/MatchArmNode, but here arms store (pattern, guard)
                visit(arm.pattern)
                arm.guard?.let { visit(it) }
                visit(value)
            }
    }

    // Literal expressions
    override fun visitI32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode) = Unit
    override fun visitISizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode) = Unit
    override fun visitU32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode) = Unit
    override fun visitUSizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode) = Unit
    override fun visitStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode) = Unit
    override fun visitCStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode) = Unit
    override fun visitCharLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode) = Unit
    override fun visitBoolLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode) = Unit

    // Non-block expressions
    override fun visitUnderscoreExpression(node: ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode) = Unit
    override fun visitTupleExpression(node: ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode) {
        node.elements.forEach { visit(it) }
    }
    override fun visitArrayExpression(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode) {
        node.elements.forEach { visit(it) }
        visit(node.repeat)
    }
    override fun visitStructExpression(node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode) {
        visit(node.pathInExpressionNode)
        node.fields.forEach { field ->
            field.expressionNode?.let { visit(it) }
        }
    }
    override fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode) {
        visit(node.callee)
        node.arguments.forEach { visit(it) }
    }
    override fun visitIndexExpression(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode) {
        visit(node.base)
        visit(node.index)
    }
    override fun visitFieldExpression(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode) {
        visit(node.base)
    }
    override fun visitPathExpression(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode) {
        visit(node.pathInExpressionNode)
    }
    override fun visitTupleIndexing(node: ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode) {
        visit(node.base)
    }
    override fun visitInfixOperator(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode) {
        visit(node.left)
        visit(node.right)
    }
    override fun visitPrefixOperator(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode) {
        visit(node.expr)
    }

    // Control flow expressions
    override fun visitReturnExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode) {
        node.expr?.let { visit(it) }
    }
    override fun visitBreakExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode) {
        node.expr?.let { visit(it) }
    }
    override fun visitContinueExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode) = Unit

    // Statements: Let leads to a new symbol
    override fun visitNullStatement(node: StatementNode.NullStatementNode) = Unit
    override fun visitItemStatement(node: StatementNode.ItemStatementNode) {
        visit(node.item)
    }
    override fun visitLetStatement(node: StatementNode.LetStatementNode) {
        visit(node.patternNode)
        node.typeNode?.let { visit(it) }
        node.expressionNode?.let { visit(it) }
    }
    override fun visitExpressionStatement(node: StatementNode.ExpressionStatementNode) {
        visit(node.expression)
    }

    // Items: Declare new symbols
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        node.genericParamsNode?.let { visit(it) }
        visit(node.functionParamsNode)
        node.returnTypeNode?.let { visit(it) }
        node.withBlockExpressionNode?.let { visit(it) }
    }
    override fun visitStructItem(node: ItemNode.StructItemNode) {
        node.fields.forEach {
            visit(it.typeNode)
        }
    }
    override fun visitEnumItem(node: ItemNode.EnumItemNode) = Unit
    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        visit(node.typeNode)
        node.expressionNode?.let { visit(it) }
    }
    override fun visitTraitItem(node: ItemNode.TraitItemNode) {
        // Associated items: consts and functions
        node.associatedItems.constItems.forEach { visit(it) }
        node.associatedItems.functionItems.forEach { visit(it) }
    }
    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        visit(node.typeNode)
        node.associatedItems.constItems.forEach { visit(it) }
        node.associatedItems.functionItems.forEach { visit(it) }
    }
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        // Trait identifier is a string; traverse type and associated items
        visit(node.typeNode)
        node.associatedItems.constItems.forEach { visit(it) }
        node.associatedItems.functionItems.forEach { visit(it) }
    }

    // Types
    override fun visitTypePath(node: TypeNode.TypePath) = Unit
    override fun visitNeverType(node: TypeNode.NeverType) {
        visit(node.type)
    }
    override fun visitTupleType(node: TypeNode.TupleType) {
        node.types.forEach { visit(it) }
    }
    override fun visitArrayType(node: TypeNode.ArrayType) {
        visit(node.type)
        visit(node.length)
    }
    override fun visitSliceType(node: TypeNode.SliceType) {
        visit(node.type)
    }
    override fun visitReferenceType(node: TypeNode.ReferenceType) {
        visit(node.type)
    }
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