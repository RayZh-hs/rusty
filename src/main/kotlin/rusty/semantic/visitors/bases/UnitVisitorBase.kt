package rusty.semantic.visitors.bases

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

open class UnitVisitorBase(open val ctx: Context) : Visitor<Unit> {

    open fun run() {
        visit(ctx.astTree)
    }

    override fun visitCrate(node: CrateNode) = Unit
    override fun visitPattern(node: PatternNode) = Unit
    override fun visitLiteralPattern(node: SupportingPatternNode.LiteralPatternNode) = Unit
    override fun visitIdentifierPattern(node: SupportingPatternNode.IdentifierPatternNode) = Unit
    override fun visitWildcardPattern(node: SupportingPatternNode.WildcardPatternNode) = Unit
    override fun visitDestructuredTuplePattern(node: SupportingPatternNode.DestructuredTuplePatternNode) = Unit
    override fun visitPathPattern(node: SupportingPatternNode.PathPatternNode) = Unit
    override fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) = Unit
    override fun visitConstBlockExpression(node: ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode) = Unit
    override fun visitLoopBlockExpression(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode) = Unit
    override fun visitWhileBlockExpression(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode) = Unit
    override fun visitIfBlockExpression(node: ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode) = Unit
    override fun visitMatchBlockExpression(node: ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode) = Unit
    override fun visitI32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode) = Unit
    override fun visitISizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode) = Unit
    override fun visitU32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode) = Unit
    override fun visitUSizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode) = Unit
    override fun visitAnyIntLiteralNode(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode) = Unit
    override fun visitAnySignedIntLiteralNode(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode) = Unit
    override fun visitStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode) = Unit
    override fun visitCStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode) = Unit
    override fun visitCharLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode) = Unit
    override fun visitBoolLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode) = Unit
    override fun visitUnderscoreExpression(node: ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode) = Unit
    override fun visitTupleExpression(node: ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode) = Unit
    override fun visitArrayExpression(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode) = Unit
    override fun visitStructExpression(node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode) = Unit
    override fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode) = Unit
    override fun visitIndexExpression(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode) = Unit
    override fun visitFieldExpression(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode) = Unit
    override fun visitPathExpression(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode) = Unit
    override fun visitTupleIndexing(node: ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode) = Unit
    override fun visitTypeCastExpression(node: ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode) = Unit
    override fun visitInfixOperator(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode) = Unit
    override fun visitPrefixOperator(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode) = Unit
    override fun visitReturnExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode) = Unit
    override fun visitBreakExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode) = Unit
    override fun visitContinueExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode) = Unit
    override fun visitNullStatement(node: StatementNode.NullStatementNode) = Unit
    override fun visitItemStatement(node: StatementNode.ItemStatementNode) = Unit
    override fun visitLetStatement(node: StatementNode.LetStatementNode) = Unit
    override fun visitExpressionStatement(node: StatementNode.ExpressionStatementNode) = Unit
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) = Unit
    override fun visitStructItem(node: ItemNode.StructItemNode) = Unit
    override fun visitEnumItem(node: ItemNode.EnumItemNode) = Unit
    override fun visitConstItem(node: ItemNode.ConstItemNode) = Unit
    override fun visitTraitItem(node: ItemNode.TraitItemNode) = Unit
    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) = Unit
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) = Unit
    override fun visitTypePath(node: TypeNode.TypePath) = Unit
    override fun visitNeverType(node: TypeNode.NeverType) = Unit
    override fun visitTupleType(node: TypeNode.TupleType) = Unit
    override fun visitArrayType(node: TypeNode.ArrayType) = Unit
    override fun visitSliceType(node: TypeNode.SliceType) = Unit
    override fun visitReferenceType(node: TypeNode.ReferenceType) = Unit
    override fun visitInferredType(node: TypeNode.InferredType) = Unit
    override fun visitGenericParams(node: ParamsNode.GenericParamsNode) = Unit
    override fun visitFunctionParams(node: ParamsNode.FunctionParamsNode) = Unit
    override fun visitPathIndentSegment(node: PathIndentSegmentNode) = Unit
    override fun visitPathInExpression(node: PathInExpressionNode) = Unit
    override fun visitConditions(node: ConditionsNode) = Unit
}