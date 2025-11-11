package rusty.semantic.visitors

import rusty.parser.ASTTree
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
import rusty.parser.nodes.support.SelfParamNode
import rusty.parser.nodes.utils.Visitor
import rusty.parser.nodes.utils.accept
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticType

/**
 * ASTTypeDumper traverses the parser AST and annotates expression nodes
 * with resolved semantic types obtained from Context.expressionTypeMemory.
 *
 * It reuses formatting ideas from the parser Dumper (color helpers, indentation)
 * but is independent so it can live in the semantic package.
 */
class ASTTypeDumper(private val context: Context, private val cfgColor: Boolean = true) : Visitor<Unit> {
    val sb = StringBuilder()
    private var indent = 0

    private fun line(text: String) { sb.append("  ".repeat(indent)).appendLine(text) }
    private inline fun <T> indented(block: () -> T): T { indent++; return try { block() } finally { indent-- } }

    private fun color(text: String, code: Int): String = if (cfgColor) "\u001B[${code}m$text\u001B[0m" else text
    private fun label(text: String) = color(text, 36)
    private fun field(text: String) = color(text, 90)
    private fun value(text: String) = color(text, 32)
    private fun number(text: String) = color(text, 34)
    private fun literal(text: String) = color(text, 33)
    private fun keyword(text: String) = color(text, 35)
    private fun op(text: String) = color(text, 31)
    private fun info(text: String) = color(text, 90)

    // Convert SemanticType to a compact human-readable string (reused logic from semantic dumper)
    private fun semanticTypeToStr(t: SemanticType?): String {
        if (t == null) return "~"
        return when (t) {
            is SemanticType.I32Type -> "i32"
            is SemanticType.U32Type -> "u32"
            is SemanticType.ISizeType -> "isize"
            is SemanticType.USizeType -> "usize"
            is SemanticType.AnyIntType -> "any-int"
            is SemanticType.AnySignedIntType -> "any-signed-int"
            is SemanticType.AnyUnsignedIntType -> "any-unsigned-int"
            is SemanticType.CharType -> "char"
            is SemanticType.StrType -> "str"
            is SemanticType.CStrType -> "cstr"
            is SemanticType.BoolType -> "bool"
            is SemanticType.UnitType -> "unit"
            is SemanticType.WildcardType -> "_"
            is SemanticType.NeverType -> "!"
            is SemanticType.ArrayType -> {
                val elem = semanticTypeToStr(t.elementType.getOrNull())
                val len = if (t.length.isReady()) t.length.get().value.toString() else "_"
                "[${elem}; ${len}]"
            }
            is SemanticType.StructType -> "struct ${t.identifier}"
            is SemanticType.EnumType -> "enum ${t.identifier}"
            is SemanticType.ReferenceType -> {
                if (t.isMutable.isReady()) {
                    "&mut ${semanticTypeToStr(t.type.getOrNull())}"
                } else {
                    "~"
                }
            }
            is SemanticType.TraitType -> "trait ${t.identifier}"
            is SemanticType.FunctionHeader -> "func-header ${t.identifier}(${t.paramTypes.joinToString(", ") { semanticTypeToStr(it) }}) -> ${semanticTypeToStr(t.returnType)}"
            is SemanticType.ExitType -> "exit"
            else -> "~"
        }
    }

    /**
     * Attempt to obtain a type for an expression from the Context's memory bank.
     * We use MemoryBank.recall with a lazy evaluator that throws if the type is not present.
     * That way we avoid computing types here and only read already-cached values.
     */
    private fun getExprType(expr: ExpressionNode): String? {
        return try {
            val t = context.expressionTypeMemory.recall(expr) { throw IllegalStateException("no-type") }
            semanticTypeToStr(t)
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun lineWithType(prefix: String, expr: ExpressionNode) {
        val t = getExprType(expr)
        if (t != null) sb.append("  ".repeat(indent)).append(prefix).append(" : ").append(color(t, 0)).appendLine()
        else line(prefix)
    }

    // ----- Visitor implementations (focused on expressions; reuses parser dump structure) -----

    override fun visitCrate(node: CrateNode) {
        line(label("Crate"))
        indented {
            if (node.items.isEmpty()) line(info("(empty)")) else node.items.forEach { it.accept(this) }
        }
    }

    override fun visitPattern(node: PatternNode) = line(label("Pattern")).also { indented { node.patternNodes.forEach { it.accept(this) } } }
    override fun visitLiteralPattern(node: SupportingPatternNode.LiteralPatternNode) = line(label("PatLiteral")).also { indented { node.literalNode.accept(this) } }
    override fun visitIdentifierPattern(node: SupportingPatternNode.IdentifierPatternNode) = line(label("PatIdent")).also { node.extendedByPatternNode?.accept(this) }
    override fun visitWildcardPattern(node: SupportingPatternNode.WildcardPatternNode) = line(label("PatWildcard"))
    override fun visitDestructuredTuplePattern(node: SupportingPatternNode.DestructuredTuplePatternNode) = line(label("PatTuple")).also { indented { node.tuple.forEach { it.accept(this) } } }
    override fun visitPathPattern(node: SupportingPatternNode.PathPatternNode) = line(label("PatPath") + " " + value(node.path.path.joinToString("::") { seg -> seg.name ?: seg.token.toString().lowercase() }))

    override fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) {
        lineWithType(label("Block"), node)
        indented {
            val hasStmts = node.statements.isNotEmpty()
            val hasTail = node.trailingExpression != null
            if (!hasStmts && !hasTail) line(info("(empty)")) else {
                node.statements.forEach { it.accept(this) }
                if (hasTail) {
                    line(field("trailing") + ":")
                    indented { node.trailingExpression!!.accept(this) }
                }
            }
        }
    }
    override fun visitConstBlockExpression(node: ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode) {
        lineWithType(label("ConstBlock"), node)
        indented { node.expression.accept(this) }
    }
    override fun visitLoopBlockExpression(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode) {
        lineWithType(label("LoopBlock"), node)
        indented { node.expression.accept(this) }
    }
    override fun visitWhileBlockExpression(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode) {
        lineWithType(label("WhileBlock"), node)
        indented {
            line(field("condition") + ":")
            indented { node.condition.expression.accept(this) }
            line(field("body") + ":")
            indented { node.expression.accept(this) }
        }
    }
    override fun visitIfBlockExpression(node: ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode) {
        lineWithType(label("IfBlock"), node)
        indented {
            node.ifs.forEachIndexed { idx, br ->
                line(field("if[$idx]") + ":")
                indented {
                    line(field("cond") + ":")
                    indented { br.condition.expression.accept(this) }
                    line(field("then") + ":")
                    indented { br.then.accept(this) }
                }
            }
            node.elseBranch?.let { eb ->
                line(field("else") + ":")
                indented { eb.accept(this) }
            }
        }
    }
    override fun visitMatchBlockExpression(node: ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode) {
        lineWithType(label("MatchBlock"), node)
        indented {
            line(field("scrutinee") + ":")
            indented { node.scrutinee.accept(this) }
            line(field("arms") + ":")
            indented {
                val arms = node.matchArmsNode.arms
                val values = node.matchArmsNode.values
                for (i in arms.indices) {
                    line(field("[$i]") + ":")
                    indented {
                        line(field("pattern") + ":")
                        indented { arms[i].pattern.accept(this) }
                        arms[i].guard?.let { g ->
                            line(field("guard") + ":")
                            indented { g.accept(this) }
                        }
                        line(field("value") + ":")
                        indented { values[i].accept(this) }
                    }
                }
            }
        }
    }

    override fun visitI32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode) =
        lineWithType(label("I32") + " " + literal(node.value.toString()), node)
    override fun visitISizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.ISizeLiteralNode) =
        lineWithType(label("ISize") + " " + literal(node.value.toString()), node)
    override fun visitU32Literal(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.U32LiteralNode) =
        lineWithType(label("U32") + " " + literal(node.value.toString()), node)
    override fun visitUSizeLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.USizeLiteralNode) =
        lineWithType(label("USize") + " " + literal(node.value.toString()), node)

    override fun visitAnyIntLiteralNode(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnyIntLiteralNode) {
        lineWithType(label("AnyInt") + " " + literal(node.value.toString()), node)
    }
    override fun visitAnySignedIntLiteralNode(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.AnySignedIntLiteralNode) {
        lineWithType(label("AnySignedInt") + " " + literal(node.value.toString()), node)
    }

    override fun visitStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.StringLiteralNode) =
        lineWithType(label("String") + " " + literal("\"${node.value}\""), node)
    override fun visitCStringLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CStringLiteralNode) =
        lineWithType(label("CString") + " " + literal("\"${node.value}\""), node)
    override fun visitCharLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.CharLiteralNode) =
        lineWithType(label("Char") + " " + literal("'${node.value}'"), node)
    override fun visitBoolLiteral(node: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode.BoolLiteralNode) =
        lineWithType(label("Bool") + " " + literal(node.value.toString()), node)
    override fun visitUnderscoreExpression(node: ExpressionNode.WithoutBlockExpressionNode.UnderscoreExpressionNode) =
        lineWithType(literal("_"), node)

    override fun visitTupleExpression(node: ExpressionNode.WithoutBlockExpressionNode.TupleExpressionNode) {
        lineWithType(label("Tuple"), node)
        indented { node.elements.forEachIndexed { i, e -> line(field("[$i]") + ":"); indented { e.accept(this) } } }
    }
    override fun visitArrayExpression(node: ExpressionNode.WithoutBlockExpressionNode.ArrayExpressionNode) {
        lineWithType(label("Array"), node)
        indented {
            node.elements.forEachIndexed { i, e -> line(field("[$i]") + ":"); indented { e.accept(this) } }
            line(field("repeat") + ":")
            indented { node.repeat.accept(this) }
        }
    }
    override fun visitStructExpression(node: ExpressionNode.WithoutBlockExpressionNode.StructExpressionNode) {
        val pathStr = node.pathInExpressionNode.path.joinToString("::") { seg -> seg.name ?: seg.token.toString().lowercase() }
        lineWithType(label("StructExpr") + " " + value(pathStr), node)
        indented {
            if (node.fields.isEmpty()) line(info("(no fields)")) else node.fields.forEachIndexed { i, f ->
                line(field("[$i]") + ":")
                indented {
                    line(field("name") + ": " + value(f.identifier))
                    if (f.expressionNode != null) {
                        line(field("value") + ":")
                        indented { f.expressionNode.accept(this) }
                    } else line(info("(no value)"))
                }
            }
        }
    }
    override fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode) {
        lineWithType(label("Call"), node)
        indented {
            line(field("callee") + ":")
            indented { node.callee.accept(this) }
            if (node.arguments.isNotEmpty()) {
                line(field("args") + ":")
                indented { node.arguments.forEachIndexed { i, a -> line(field("[$i]") + ":"); indented { a.accept(this) } } }
            }
        }
    }
    override fun visitIndexExpression(node: ExpressionNode.WithoutBlockExpressionNode.IndexExpressionNode) {
        lineWithType(label("Index"), node)
        indented {
            line(field("base") + ":")
            indented { node.base.accept(this) }
            line(field("index") + ":")
            indented { node.index.accept(this) }
        }
    }
    override fun visitFieldExpression(node: ExpressionNode.WithoutBlockExpressionNode.FieldExpressionNode) {
        lineWithType(label("Field"), node)
        indented {
            line(field("base") + ":")
            indented { node.base.accept(this) }
            line(field("name") + ": " + value(node.field))
        }
    }
    override fun visitPathExpression(node: ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode) {
        val pathStr = node.pathInExpressionNode.path.joinToString("::") { seg -> seg.name ?: seg.token.toString().lowercase() }
        lineWithType(label("Path") + " " + value(pathStr), node)
    }
    override fun visitTupleIndexing(node: ExpressionNode.WithoutBlockExpressionNode.TupleIndexingNode) {
        lineWithType(label("TupleIndex"), node)
        indented {
            line(field("base") + ":")
            indented { node.base.accept(this) }
            line(field("index") + ": " + number(node.index.toString()))
        }
    }
    override fun visitTypeCastExpression(node: ExpressionNode.WithoutBlockExpressionNode.TypeCastExpressionNode) {
        lineWithType(label("TypeCast"), node)
        indented {
            line(field("expr") + ":")
            indented { node.expr.accept(this) }
            line(field("type") + ":")
            indented { node.targetType.accept(this) }
        }
    }

    override fun visitInfixOperator(node: ExpressionNode.WithoutBlockExpressionNode.InfixOperatorNode) {
        lineWithType(label("Infix") + " op=" + op(node.op.toString()), node)
        indented {
            line(field("left") + ":")
            indented { node.left.accept(this) }
            line(field("right") + ":")
            indented { node.right.accept(this) }
        }
    }
    override fun visitPrefixOperator(node: ExpressionNode.WithoutBlockExpressionNode.PrefixOperatorNode) {
        lineWithType(label("Prefix") + " op=" + op(node.op.toString()), node)
        indented {
            line(field("expr") + ":")
            indented { node.expr.accept(this) }
        }
    }

    override fun visitReferenceExpression(node: ExpressionNode.WithoutBlockExpressionNode.ReferenceExpressionNode) {
        lineWithType(label("Ref") + " mut=" + field(node.isMut.toString()), node)
        indented {
            line(field("expr") + ":")
            indented { node.expr.accept(this) }
        }
    }
    override fun visitDereferenceExpression(node: ExpressionNode.WithoutBlockExpressionNode.DereferenceExpressionNode) {
        lineWithType(label("Deref"), node)
        indented {
            line(field("expr") + ":")
            indented { node.expr.accept(this) }
        }
    }

    override fun visitReturnExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode) {
        lineWithType(keyword("return"), node)
        node.expr?.let {
            indented {
                line(field("value") + ":")
                indented { it.accept(this) }
            }
        }
    }
    override fun visitBreakExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode) {
        lineWithType(keyword("break"), node)
        node.expr?.let {
            indented {
                line(field("value") + ":")
                indented { it.accept(this) }
            }
        }
    }
    override fun visitContinueExpression(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode) =
        lineWithType(keyword("continue"), node)

    override fun visitNullStatement(node: StatementNode.NullStatementNode) = line(label("NullStatement"))
    override fun visitItemStatement(node: StatementNode.ItemStatementNode) {
        line(label("ItemStatement"))
        indented { node.item.accept(this) }
    }
    override fun visitLetStatement(node: StatementNode.LetStatementNode) {
        line(label("LetStatement"))
        indented {
            line(field("pattern") + ":")
            indented { node.patternNode.accept(this) }
            node.typeNode?.let {
                line(field("type") + ":")
                indented { it.accept(this) }
            }
            line(field("init") + ":")
            indented {
                if (node.expressionNode != null) node.expressionNode.accept(this) else line(info("(uninitialized)"))
            }
        }
    }
    override fun visitExpressionStatement(node: StatementNode.ExpressionStatementNode) {
        line(label("ExpressionStatement"))
        indented { node.expression.accept(this) }
    }

    // Basic items printing (compact)
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        line(label("FunctionItem"))
        indented {
            line(field("name") + ": " + value(node.identifier))
            node.withBlockExpressionNode?.let {
                line(field("body") + ":")
                indented { it.accept(this) }
            }
        }
    }
    override fun visitStructItem(node: ItemNode.StructItemNode) {
        line(label("StructItem"))
        indented {
            line(field("name") + ": " + value(node.identifier))
            line(field("fields") + ":")
            indented {
                if (node.fields.isEmpty()) line(info("(none)"))
                else node.fields.forEachIndexed { i, f ->
                    line(field("[$i]") + ":")
                    indented {
                        line(field("name") + ": " + value(f.identifier))
                        line(field("type") + ":")
                        indented { f.typeNode.accept(this) }
                    }
                }
            }
        }
    }
    override fun visitEnumItem(node: ItemNode.EnumItemNode) {
        line(label("EnumItem"))
        indented {
            line(field("name") + ": " + value(node.identifier))
            line(field("variants") + ":")
            indented {
                if (node.variants.isEmpty()) line(info("(none)"))
                else node.variants.forEachIndexed { i, v ->
                    line(field("[$i]") + ":")
                    indented { line(value(v.identifier)) }
                }
            }
        }
    }

    // Items not previously implemented in this dumper: keep compact outputs
    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        line(label("ConstItem"))
        indented {
            line(field("name") + ": " + value(node.identifier))
            line(field("type") + ":")
            indented { node.typeNode.accept(this) }
            line(field("value") + ":")
            indented { node.expressionNode?.accept(this) ?: line(info("(none)")) }
        }
    }
    override fun visitTraitItem(node: ItemNode.TraitItemNode) {
        line(label("TraitItem"))
        indented {
            line(field("name") + ": " + value(node.identifier))
            line(field("assoc") + ":")
            indented { line(info("(trait items not expanded)")) }
        }
    }
    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        line(label("ImplItem(Inherent)"))
        indented {
            line(field("type") + ":")
            indented { node.typeNode.accept(this) }
            line(field("assoc") + ":")
            indented { line(info("(impl items not expanded)")) }
        }
    }
    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        line(label("ImplItem(Trait)"))
        indented {
            line(field("trait") + ": " + value(node.identifier))
            line(field("type") + ":")
            indented { node.typeNode.accept(this) }
            line(field("assoc") + ":")
            indented { line(info("(impl items not expanded)")) }
        }
    }

    // ---- Type visitors (compact, reuse structure from parser dumper) ----
    override fun visitTypePath(node: TypeNode.TypePath) {
        val seg = node.pathSegmentNode
        line(value(seg.name ?: seg.token.toString().lowercase()))
    }
    override fun visitNeverType(node: TypeNode.NeverType) {
        line(label("Never"))
        indented { node.type.accept(this) }
    }
    override fun visitTupleType(node: TypeNode.TupleType) {
        line(label("TupleType"))
        indented {
            if (node.types.isEmpty()) line(info("(unit)")) else node.types.forEachIndexed { i, t ->
                line(field("[$i]") + ":")
                indented { t.accept(this) }
            }
        }
    }
    override fun visitArrayType(node: TypeNode.ArrayType) {
        line(label("ArrayType"))
        indented {
            line(field("elem") + ":")
            indented { node.type.accept(this) }
            line(field("len") + ":")
            indented { node.length.accept(this) }
        }
    }
    override fun visitSliceType(node: TypeNode.SliceType) {
        line(label("SliceType"))
        indented { node.type.accept(this) }
    }
    override fun visitReferenceType(node: TypeNode.ReferenceType) {
        val mutPart = if (node.isMut) " mut" else ""
        line(label("RefType$mutPart"))
        indented { node.type.accept(this) }
    }
    override fun visitInferredType(node: TypeNode.InferredType) {
        line(label("InferredType"))
    }

    // ---- Params visitors ----
    override fun visitGenericParams(node: ParamsNode.GenericParamsNode) {
        if (node.genericParams.isEmpty()) line(info("(none)")) else node.genericParams.forEachIndexed { i, gp ->
            line(field("[$i]") + ":")
            indented { gp.type.accept(this) }
        }
    }
    override fun visitFunctionParams(node: ParamsNode.FunctionParamsNode) {
        node.selfParam?.let { sp ->
            line(field("self") + ":")
            indented { visitSelfParam(sp) }
        }
        if (node.functionParams.isEmpty()) return
        line(field("args") + ":")
        indented {
            node.functionParams.forEachIndexed { i, fn ->
                line(field("[$i]") + ":")
                indented { when (fn) {
                    is FunctionParamNode.FunctionParamTypedPatternNode -> visitFunctionParamTypedPattern(fn)
                    is FunctionParamNode.FunctionParamTypeNode -> visitFunctionParamType(fn)
                    is FunctionParamNode.FunctionParamWildcardNode -> visitFunctionParamWildcard(fn)
                } }
            }
        }
    }
    override fun visitSelfParam(node: SelfParamNode) {
        val mods = buildList { if (node.isReference) add("&"); if (node.isMutable) add(keyword("mut")) }.joinToString("")
        line(value("self") + if (mods.isNotEmpty()) " <$mods>" else "")
        node.type?.let {
            line(field("type") + ":")
            indented { it.accept(this) }
        }
    }
    override fun visitFunctionParamTypedPattern(node: FunctionParamNode.FunctionParamTypedPatternNode) {
        line(label("ParamPattern"))
        indented {
            line(field("pattern") + ":")
            indented { node.pattern.accept(this) }
            node.type?.let {
                line(field("type") + ":")
                indented { it.accept(this) }
            }
        }
    }
    override fun visitFunctionParamType(node: FunctionParamNode.FunctionParamTypeNode) {
        line(label("ParamType"))
        indented { node.type.accept(this) }
    }
    override fun visitFunctionParamWildcard(node: FunctionParamNode.FunctionParamWildcardNode) {
        line(label("ParamWildcard..."))
    }

    // ---- Path visitors ----
    override fun visitPathIndentSegment(node: PathIndentSegmentNode) { line(value(node.name ?: node.token.toString().lowercase())) }
    override fun visitPathInExpression(node: PathInExpressionNode) {
        val pathStr = node.path.joinToString("::") { it.name ?: it.token.toString().lowercase() }
        line(label("Path") + " " + value(pathStr))
    }

    // Misc
    override fun visitConditions(node: ConditionsNode) {
        node.expression.accept(this)
    }

    /**
     * Format an ASTTree root and return the resulting dump string.
     * Users of this class should call this to get the annotated dump.
     */
    fun format(root: ASTTree): String {
        root.accept(this)
        return sb.toString()
    }
}