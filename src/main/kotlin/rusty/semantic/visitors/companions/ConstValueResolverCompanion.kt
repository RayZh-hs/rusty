package rusty.semantic.visitors.companions

import rusty.core.CompileError
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
import rusty.semantic.support.SemanticValueNode

// This companion scans the AST and resolves constants to their values.
// It is called on every const expression by the core ConstValueResolver to resolve constants.
// Later on, it is used to evaluate const expressions at compile time. (lookup and evaluate, which is its default behavior).
class ConstValueResolverCompanion(val ctx: Context) : Visitor<SemanticValueNode> {
    fun invalidResolve(): SemanticValueNode {
        throw CompileError("Constant resolution landed in invalid state").with(ctx)
    }
    
    override fun visitCrate(node: CrateNode): SemanticValueNode = invalidResolve()
    override fun visitPattern(node: PatternNode): SemanticValueNode = invalidResolve()
    override fun visitLiteralPattern(node: SupportingPatternNode.LiteralPatternNode): SemanticValueNode = invalidResolve()
    override fun visitIdentifierPattern(node: SupportingPatternNode.IdentifierPatternNode): SemanticValueNode = invalidResolve()
    override fun visitWildcardPattern(node: SupportingPatternNode.WildcardPatternNode): SemanticValueNode = invalidResolve()
    override fun visitDestructuredTuplePattern(node: SupportingPatternNode.DestructuredTuplePatternNode): SemanticValueNode = invalidResolve()
    override fun visitPathPattern(node: SupportingPatternNode.PathPatternNode): SemanticValueNode = invalidResolve()
    override fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitConstBlockExpression(node: ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitLoopBlockExpression(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitWhileBlockExpression(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitIfBlockExpression(node: ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitMatchBlockExpression(node: ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitI32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode): SemanticValueNode = invalidResolve()
    override fun visitISizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitU32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode): SemanticValueNode = invalidResolve()
    override fun visitUSizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitAnyIntegerLiteralNode(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntegerLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitCStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitCharLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitBoolLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode): SemanticValueNode = invalidResolve()
    override fun visitUnderscoreExpression(node: ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitTupleExpression(node: ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitArrayExpression(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitStructExpression(node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitIndexExpression(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitFieldExpression(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitPathExpression(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitTupleIndexing(node: ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode): SemanticValueNode = invalidResolve()
    override fun visitInfixOperator(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode): SemanticValueNode = invalidResolve()
    override fun visitPrefixOperator(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode): SemanticValueNode = invalidResolve()
    override fun visitReturnExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitBreakExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitContinueExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitNullStatement(node: StatementNode.NullStatementNode): SemanticValueNode = invalidResolve()
    override fun visitItemStatement(node: StatementNode.ItemStatementNode): SemanticValueNode = invalidResolve()
    override fun visitLetStatement(node: StatementNode.LetStatementNode): SemanticValueNode = invalidResolve()
    override fun visitExpressionStatement(node: StatementNode.ExpressionStatementNode): SemanticValueNode = invalidResolve()
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode): SemanticValueNode = invalidResolve()
    override fun visitStructItem(node: ItemNode.StructItemNode): SemanticValueNode = invalidResolve()
    override fun visitEnumItem(node: ItemNode.EnumItemNode): SemanticValueNode = invalidResolve()
    override fun visitConstItem(node: ItemNode.ConstItemNode): SemanticValueNode = invalidResolve()
    override fun visitTraitItem(node: ItemNode.TraitItemNode): SemanticValueNode = invalidResolve()
    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode): SemanticValueNode = invalidResolve()
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode): SemanticValueNode = invalidResolve()
    override fun visitTypePath(node: TypeNode.TypePath): SemanticValueNode = invalidResolve()
    override fun visitNeverType(node: TypeNode.NeverType): SemanticValueNode = invalidResolve()
    override fun visitTupleType(node: TypeNode.TupleType): SemanticValueNode = invalidResolve()
    override fun visitArrayType(node: TypeNode.ArrayType): SemanticValueNode = invalidResolve()
    override fun visitSliceType(node: TypeNode.SliceType): SemanticValueNode = invalidResolve()
    override fun visitReferenceType(node: TypeNode.ReferenceType): SemanticValueNode = invalidResolve()
    override fun visitInferredType(node: TypeNode.InferredType): SemanticValueNode = invalidResolve()
    override fun visitGenericParams(node: ParamsNode.GenericParamsNode): SemanticValueNode = invalidResolve()
    override fun visitFunctionParams(node: ParamsNode.FunctionParamsNode): SemanticValueNode = invalidResolve()
    override fun visitPathIndentSegment(node: PathIndentSegmentNode): SemanticValueNode = invalidResolve()
    override fun visitPathInExpression(node: PathInExpressionNode): SemanticValueNode = invalidResolve()
    override fun visitConditions(node: ConditionsNode): SemanticValueNode = invalidResolve()
}