package rusty.parser.nodes.utils

import rusty.parser.nodes.*
import rusty.parser.nodes.support.ConditionsNode

interface Visitor<R> {
	// Crate
	fun visitCrate(node: CrateNode): R

	// Patterns
	fun visitPattern(node: PatternNode): R
	fun visitLiteralPattern(node: SupportingPatternNode.LiteralPatternNode): R
	fun visitIdentifierPattern(node: SupportingPatternNode.IdentifierPatternNode): R
	fun visitWildcardPattern(node: SupportingPatternNode.WildcardPatternNode): R
	fun visitDestructuredTuplePattern(node: SupportingPatternNode.DestructuredTuplePatternNode): R
	fun visitPathPattern(node: SupportingPatternNode.PathPatternNode): R

	// Expressions with block
	fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode): R
	fun visitConstBlockExpression(node: ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode): R
	fun visitLoopBlockExpression(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode): R
	fun visitWhileBlockExpression(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode): R
	fun visitIfBlockExpression(node: ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode): R
	fun visitMatchBlockExpression(node: ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode): R

	// Literal expressions
    fun visitI32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode): R
    fun visitISizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode): R
    fun visitU32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode): R
    fun visitUSizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode): R
    fun visitStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode): R
    fun visitCStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode): R
	fun visitCharLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode): R
	fun visitBoolLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode): R

	// Non-block expressions
	fun visitUnderscoreExpression(node: ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode): R
	fun visitTupleExpression(node: ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode): R
	fun visitArrayExpression(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode): R
	fun visitStructExpression(node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode): R
	fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode): R
	fun visitIndexExpression(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode): R
	fun visitFieldExpression(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode): R
	fun visitPathExpression(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode): R
	fun visitTupleIndexing(node: ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode): R
	fun visitInfixOperator(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode): R
	fun visitPrefixOperator(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode): R

	// Control flow expressions
	fun visitReturnExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode): R
	fun visitBreakExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode): R
	fun visitContinueExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode): R

	// Statements
	fun visitNullStatement(node: StatementNode.NullStatementNode): R
	fun visitItemStatement(node: StatementNode.ItemStatementNode): R
	fun visitLetStatement(node: StatementNode.LetStatementNode): R
	fun visitExpressionStatement(node: StatementNode.ExpressionStatementNode): R

	// Items
	fun visitFunctionItem(node: ItemNode.FunctionItemNode): R
	fun visitStructItem(node: ItemNode.StructItemNode): R
	fun visitEnumItem(node: ItemNode.EnumItemNode): R
	fun visitConstItem(node: ItemNode.ConstItemNode): R
	fun visitTraitItem(node: ItemNode.TraitItemNode): R
	fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode): R
	fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode): R

	// Types
	fun visitTypePath(node: TypeNode.TypePath): R
	fun visitNeverType(node: TypeNode.NeverType): R
	fun visitTupleType(node: TypeNode.TupleType): R
	fun visitArrayType(node: TypeNode.ArrayType): R
	fun visitSliceType(node: TypeNode.SliceType): R
	fun visitReferenceType(node: TypeNode.ReferenceType): R
	fun visitInferredType(node: TypeNode.InferredType): R

	// Params
	fun visitGenericParams(node: ParamsNode.GenericParamsNode): R
	fun visitFunctionParams(node: ParamsNode.FunctionParamsNode): R

	// Paths
	fun visitPathIndentSegment(node: PathIndentSegmentNode): R
	fun visitPathInExpression(node: PathInExpressionNode): R

    // Misc
    fun visitConditions(node: ConditionsNode): R

	// Dynamic dispatch (entrypoint)
	fun visit(node: ASTNode): R = node.accept(this)
}

@Suppress("CyclomaticComplexMethod")
fun <R> ASTNode.accept(visitor: Visitor<R>): R = when (this) {
	is CrateNode -> visitor.visitCrate(this)

	is PatternNode -> visitor.visitPattern(this)
	is SupportingPatternNode.LiteralPatternNode -> visitor.visitLiteralPattern(this)
	is SupportingPatternNode.IdentifierPatternNode -> visitor.visitIdentifierPattern(this)
	is SupportingPatternNode.WildcardPatternNode -> visitor.visitWildcardPattern(this)
	is SupportingPatternNode.DestructuredTuplePatternNode -> visitor.visitDestructuredTuplePattern(this)
	is SupportingPatternNode.PathPatternNode -> visitor.visitPathPattern(this)

	is ExpressionNode.WithBlockExpressionNode.BlockExpressionNode -> visitor.visitBlockExpression(this)
	is ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode -> visitor.visitConstBlockExpression(this)
	is ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode -> visitor.visitLoopBlockExpression(this)
	is ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode -> visitor.visitWhileBlockExpression(this)
	is ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode -> visitor.visitIfBlockExpression(this)
	is ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode -> visitor.visitMatchBlockExpression(this)

    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode -> visitor.visitI32Literal(this)
    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode -> visitor.visitISizeLiteral(this)
    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode -> visitor.visitU32Literal(this)
    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode -> visitor.visitUSizeLiteral(this)
    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode -> visitor.visitStringLiteral(this)
    is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode -> visitor.visitCStringLiteral(this)
	is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode -> visitor.visitCharLiteral(this)
	is ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode -> visitor.visitBoolLiteral(this)
	is ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode -> visitor.visitUnderscoreExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode -> visitor.visitTupleExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode -> visitor.visitArrayExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode -> visitor.visitStructExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode -> visitor.visitCallExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode -> visitor.visitIndexExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode -> visitor.visitFieldExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode -> visitor.visitPathExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode -> visitor.visitTupleIndexing(this)
	is ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode -> visitor.visitInfixOperator(this)
	is ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode -> visitor.visitPrefixOperator(this)
	is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode -> visitor.visitReturnExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode -> visitor.visitBreakExpression(this)
	is ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode -> visitor.visitContinueExpression(this)

	is StatementNode.NullStatementNode -> visitor.visitNullStatement(this)
	is StatementNode.ItemStatementNode -> visitor.visitItemStatement(this)
	is StatementNode.LetStatementNode -> visitor.visitLetStatement(this)
	is StatementNode.ExpressionStatementNode -> visitor.visitExpressionStatement(this)

	is ItemNode.FunctionItemNode -> visitor.visitFunctionItem(this)
	is ItemNode.StructItemNode -> visitor.visitStructItem(this)
	is ItemNode.EnumItemNode -> visitor.visitEnumItem(this)
	is ItemNode.ConstItemNode -> visitor.visitConstItem(this)
	is ItemNode.TraitItemNode -> visitor.visitTraitItem(this)
	is ItemNode.ImplItemNode.InherentImplItemNode -> visitor.visitInherentImplItem(this)
	is ItemNode.ImplItemNode.TraitImplItemNode -> visitor.visitTraitImplItem(this)

	is TypeNode.TypePath -> visitor.visitTypePath(this)
	is TypeNode.NeverType -> visitor.visitNeverType(this)
	is TypeNode.TupleType -> visitor.visitTupleType(this)
	is TypeNode.ArrayType -> visitor.visitArrayType(this)
	is TypeNode.SliceType -> visitor.visitSliceType(this)
	is TypeNode.ReferenceType -> visitor.visitReferenceType(this)
	is TypeNode.InferredType -> visitor.visitInferredType(this)

	is ParamsNode.GenericParamsNode -> visitor.visitGenericParams(this)
	is ParamsNode.FunctionParamsNode -> visitor.visitFunctionParams(this)

	is PathIndentSegmentNode -> visitor.visitPathIndentSegment(this)
	is PathInExpressionNode -> visitor.visitPathInExpression(this)

    is ConditionsNode -> visitor.visitConditions(this)

    else -> throw IllegalArgumentException("Unknown ASTNode type: ${this::class.simpleName}")
}
