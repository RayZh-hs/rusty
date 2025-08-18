package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.support.ConditionsNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.support.IfBranchNode
import rusty.parser.nodes.StatementNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.parseWithoutStruct
import rusty.parser.nodes.support.MatchArmsNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsExpectToken

fun ExpressionNode.WithBlockExpressionNode.Companion.peek(ctx: Context): Boolean {
    return setOf(Token.O_LCURL, Token.K_CONST, Token.K_LOOP, Token.K_WHILE, Token.K_IF, Token.K_MATCH)
        .contains(ctx.peekToken())
}

fun ExpressionNode.WithBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode {
    return when (val token = ctx.peekToken()) {
        Token.O_LCURL -> ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        Token.K_CONST -> ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode.parse(ctx)
        Token.K_LOOP -> ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode.parse(ctx)
        Token.K_WHILE -> ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode.parse(ctx)
        Token.K_IF -> ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode.parse(ctx)
        Token.K_MATCH -> ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode.parse(ctx)
        else -> throw CompileError("Unexpected token for block expression: $token").with(ctx)
    }
}

// Name definitions
val ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.Companion.name get() = "BlockExpression"
val ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode.Companion.name get() = "ConstBlockExpression"
val ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode.Companion.name get() = "LoopBlockExpression"
val ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode.Companion.name get() = "WhileBlockExpression"
val ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode.Companion.name get() = "IfBlockExpression"
val ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode.Companion.name get() = "MatchBlockExpression"

// { ... }
fun ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode.WithBlockExpressionNode.BlockExpressionNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.O_LCURL)

        val statements = mutableListOf<StatementNode>()
        var trailingExpression: ExpressionNode? = null
        while (ctx.peekToken() != Token.O_RCURL && ctx.peekToken() != null) {
            var isBlockExpr = false
            val expr = ctx.tryParse("BlockExpression@Expression") {
                isBlockExpr = ExpressionNode.WithBlockExpressionNode.peek(ctx)
                ExpressionNode.parse(ctx)
            }
            if (expr == null) {
                statements.add(StatementNode.parse(ctx))
            } else {
                when (ctx.peekToken()) {
                    Token.O_SEMICOLON -> {
                        // [expr]; forms a statement
                        ctx.stream.consume(1)
                        statements.add(StatementNode.ExpressionStatementNode(expr))
                    }

                    Token.O_RCURL -> {
                        // the ending expression should be marked as the trailing expression
                        trailingExpression = expr
                    }

                    else -> {
                        // In the event of a block expression, we can omit the ending semicolumn
                        if (isBlockExpr) {
                            statements.add(StatementNode.ExpressionStatementNode(expr))
                            continue
                        }
                        throw CompileError("Unexpected token for end of expression: ${ctx.peekToken()} when parsing $expr").with(
                            ctx
                        )
                    }
                }
            }
        }

        putilsExpectToken(ctx, Token.O_RCURL)
        return ExpressionNode.WithBlockExpressionNode.BlockExpressionNode(statements, trailingExpression)
    }
}

// const { ... }
fun ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_CONST)
        val block = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode(block)
    }
}

// loop { ... }
fun ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_LOOP)
        val block = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode(block)
    }
}

// while(condition) { ... }
fun ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_WHILE)
        val condition = ConditionsNode.parse(ctx)
        val block = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode(condition, block)
    }
}

// if (condition) { ... } (else { ... } | SELF)?
fun ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode {
    ctx.callMe(name) {
        val ifBranches = mutableListOf<IfBranchNode>()
        var elseBranch: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode? = null

        putilsExpectToken(ctx, Token.K_IF)
        val firstCondition = ConditionsNode.parse(ctx)
        val firstThen = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        ifBranches.add(IfBranchNode(firstCondition, firstThen))

        while (ctx.peekToken() == Token.K_ELSE) {
            ctx.stream.consume(1)

            // Check for 'else if'
            if (ctx.peekToken() == Token.K_IF) {
                ctx.stream.consume(1) // Consume 'if'
                val condition = ConditionsNode.parse(ctx)
                val thenBlock = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
                ifBranches.add(IfBranchNode(condition, thenBlock))
            } else {
                // This must be the final 'else' block
                elseBranch = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
                break
            }
        }

        return ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode(ifBranches, elseBranch)
    }
}

// match scrutinee { ... }
fun ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_MATCH)
        val scrutinee = ExpressionNode.parseWithoutStruct(ctx)
        val matchArmsNode = MatchArmsNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode(scrutinee, matchArmsNode)
    }
}