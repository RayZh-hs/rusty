package rusty.parser.nodes.impl

import rusty.settings.Settings
import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.support.ConditionsNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.support.IfBranchNode
import rusty.parser.nodes.StatementNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.support.MatchArmsNode
import rusty.parser.putils.ParsingContext
import rusty.parser.putils.putilsExpectToken

fun ExpressionNode.WithBlockExpressionNode.Companion.peek(ctx: ParsingContext): Boolean {
    return setOf(Token.O_LCURL, Token.K_CONST, Token.K_LOOP, Token.K_WHILE, Token.K_IF, Token.K_MATCH)
        .contains(ctx.peekToken())
}

fun ExpressionNode.WithBlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode {
    return when (val token = ctx.peekToken()) {
        Token.O_LCURL -> ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        Token.K_CONST -> ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode.parse(ctx)
        Token.K_LOOP -> ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode.parse(ctx)
        Token.K_WHILE -> ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode.parse(ctx)
        Token.K_IF -> ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode.parse(ctx)
        Token.K_MATCH -> ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode.parse(ctx)
        else -> throw CompileError("Unexpected token for block expression: $token").with(ctx).at(ctx.peekPointer())
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
fun ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode.WithBlockExpressionNode.BlockExpressionNode {
    ctx.callMe(name, enable_stack = true) {
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
                        statements.add(StatementNode.ExpressionStatementNode(expr, ctx.topPointer()))
                    }

                    Token.O_RCURL -> {
                        // the ending expression should be marked as the trailing expression
                        trailingExpression = expr
                    }

                    else -> {
                        // In the event of a block expression, we can omit the ending semicolumn
                        if (isBlockExpr) {
                            statements.add(StatementNode.ExpressionStatementNode(expr, ctx.topPointer()))
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
        return ExpressionNode.WithBlockExpressionNode.BlockExpressionNode(statements, trailingExpression, ctx.topPointer())
    }
}

// const { ... }
fun ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode {
    ctx.callMe(name, enable_stack = true) {
        putilsExpectToken(ctx, Token.K_CONST)
        val block = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode(block, ctx.topPointer())
    }
}

// loop { ... }
fun ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode {
    ctx.callMe(name, enable_stack = true) {
        putilsExpectToken(ctx, Token.K_LOOP)
        val block = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode(block, ctx.topPointer())
    }
}

fun enforceConditionalParens(ctx: ParsingContext) {
    if (Settings.ENFORCE_PAREN_ON_CONDITIONAL) {
        if (ctx.peekToken() != Token.O_LPAREN) {
            throw CompileError("Expected '(' but found ${ctx.peekToken()}").with(ctx).at(ctx.peekPointer())
        }
    }
}

// while(condition) { ... }
fun ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode {
    ctx.callMe(name, enable_stack = true) {
        putilsExpectToken(ctx, Token.K_WHILE)
        enforceConditionalParens(ctx)
        val condition = ConditionsNode.parse(ctx)
        val block = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode(condition, block, ctx.topPointer())
    }
}

// if (condition) { ... } (else { ... } | SELF)?
fun ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode {
    ctx.callMe(name, enable_stack = true) {
        val ifBranches = mutableListOf<IfBranchNode>()
        var elseBranch: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode? = null

        putilsExpectToken(ctx, Token.K_IF)
        enforceConditionalParens(ctx)
        val firstCondition = ConditionsNode.parse(ctx)
        val firstThen = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
        ifBranches.add(IfBranchNode(firstCondition, firstThen))

        while (ctx.peekToken() == Token.K_ELSE) {
            ctx.stream.consume(1)

            // Check for 'else if'
            if (ctx.peekToken() == Token.K_IF) {
                ctx.stream.consume(1) // Consume 'if'
                enforceConditionalParens(ctx)
                val condition = ConditionsNode.parse(ctx)
                val thenBlock = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
                ifBranches.add(IfBranchNode(condition, thenBlock))
            } else {
                // This must be the final 'else' block
                elseBranch = ExpressionNode.WithBlockExpressionNode.BlockExpressionNode.parse(ctx)
                break
            }
        }

        return ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode(ifBranches, elseBranch, ctx.topPointer())
    }
}

// match scrutinee { ... }
fun ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode.Companion.parse(ctx: ParsingContext): ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode {
    ctx.callMe(name, enable_stack = true) {
        putilsExpectToken(ctx, Token.K_MATCH)
        val scrutinee = ctx.withEnableStruct(enable = false) {
            ExpressionNode.parse(ctx)
        }
        val matchArmsNode = MatchArmsNode.parse(ctx)
        return ExpressionNode.WithBlockExpressionNode.MatchBlockExpressionNode(scrutinee, matchArmsNode, ctx.topPointer())
    }
}
