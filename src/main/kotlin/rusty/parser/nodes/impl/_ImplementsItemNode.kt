package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.ParamsNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.support.AssociatedItemsNode
import rusty.parser.nodes.support.EnumVariantNode
import rusty.parser.nodes.support.StructExprFieldNode
import rusty.parser.nodes.support.StructFieldNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectListWithin
import rusty.parser.putils.putilsExpectToken

// Function Item
val ItemNode.FunctionItemNode.Companion.name get() = "FunctionItem"

fun ItemNode.FunctionItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.stream.peekOrNull()?.token == Token.K_FN
}

fun ItemNode.FunctionItemNode.Companion.parse(ctx: Context): ItemNode.FunctionItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_FN)
        val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
        val genericParamsNode = if (ctx.peekToken() == Token.O_LANG) ParamsNode.GenericParamsNode.parse(ctx) else null
        val functionParamsNode = ParamsNode.FunctionParamsNode.parse(ctx)
        val returnTypeNode = when (ctx.peekToken()) {
            Token.O_ARROW -> {
                ctx.stream.consume(1)
                TypeNode.parse(ctx)
            }
            else -> null
        }
        val withBlockExpressionNode = when (ctx.stream.peekOrNull()?.token) {
            Token.O_LCURL -> ExpressionNode.WithBlockExpressionNode.parse(ctx)
            Token.O_SEMICOLON -> {
                ctx.stream.consume(1)
                null
            }
            else -> throw CompileError("Malformed function body at line ${ctx.stream.peekOrNull()?.lineNumber}").with(
                ctx
            )
        }
        return ItemNode.FunctionItemNode(
            identifier = identifier,
            genericParamsNode = genericParamsNode,
            functionParamsNode = functionParamsNode,
            returnTypeNode = returnTypeNode,
            withBlockExpressionNode = withBlockExpressionNode
        )
    }
}

// Struct Item
val ItemNode.StructItemNode.Companion.name get() = "StructItem"

fun ItemNode.StructItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.peekToken() == Token.K_STRUCT
}

fun ItemNode.StructItemNode.Companion.parse(ctx: Context): ItemNode.StructItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_STRUCT)
        val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
        if (putilsConsumeIfExistsToken(ctx, Token.O_SEMICOLON)) {
            return ItemNode.StructItemNode(
                identifier = identifier,
                fields = emptyList(),
                isDeclaration = true
            )
        }
        val fields = putilsExpectListWithin(
            ctx,
            parsingFunction = StructFieldNode.Companion::parse,
            wrappingTokens = Pair(Token.O_LCURL, Token.O_RCURL)
        )
        return ItemNode.StructItemNode(identifier, fields, isDeclaration = false)
    }
}

// Enum Item
val ItemNode.EnumItemNode.Companion.name get() = "EnumItem"

fun ItemNode.EnumItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.peekToken() == Token.K_ENUM
}

fun ItemNode.EnumItemNode.Companion.parse(ctx: Context): ItemNode.EnumItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_ENUM)
        val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
        val variants = putilsExpectListWithin(
            ctx,
            parsingFunction = EnumVariantNode.Companion::parse,
            wrappingTokens = Pair(Token.O_LCURL, Token.O_RCURL)
        )
        return ItemNode.EnumItemNode(identifier, variants)
    }
}

// Const Item
val ItemNode.ConstItemNode.Companion.name get() = "ConstantItem"

fun ItemNode.ConstItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.peekToken() == Token.K_CONST
}

fun ItemNode.ConstItemNode.Companion.parse(ctx: Context): ItemNode.ConstItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_CONST)
        val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
        putilsExpectToken(ctx, Token.O_COLUMN)
        val typeNode = TypeNode.parse(ctx)
        val expressionNode = if (putilsConsumeIfExistsToken(ctx, Token.O_EQ)) {
            ExpressionNode.parse(ctx)
        } else null
        putilsExpectToken(ctx, Token.O_SEMICOLON)
        return ItemNode.ConstItemNode(identifier, typeNode, expressionNode)
    }
}

// Trait Item
val ItemNode.TraitItemNode.Companion.name get() = "TraitItem"

fun ItemNode.TraitItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.peekToken() == Token.K_TRAIT
}

fun ItemNode.TraitItemNode.Companion.parse(ctx: Context): ItemNode.TraitItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_TRAIT)
        val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
        val associatedItems = AssociatedItemsNode.parse(ctx)
        return ItemNode.TraitItemNode(identifier, associatedItems)
    }
}

// Impl Item
val ItemNode.ImplItemNode.Companion.name get() = "ImplItem"

fun ItemNode.ImplItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.peekToken() == Token.K_IMPL
}

fun ItemNode.ImplItemNode.Companion.parse(ctx: Context): ItemNode.ImplItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_IMPL)
        val nt = ctx.stream.peekAtOrNull(ctx.stream.cur)?.token
        val nnt = ctx.stream.peekAtOrNull(ctx.stream.cur + 1)?.token
        when {
            nt == Token.I_IDENTIFIER && nnt == Token.K_FOR -> {
                // TraitImpl →
                //    impl IDENTIFIER for Type {...}
                val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
                putilsExpectToken(ctx, Token.K_FOR)
                val forTypeNode = TypeNode.parse(ctx)
                val associatedItems = AssociatedItemsNode.parse(ctx)
                return ItemNode.ImplItemNode.TraitImplItemNode(
                    identifier = identifier,
                    typeNode = forTypeNode,
                    associatedItems = associatedItems
                )
            }
            else -> {
                // InherentImpl →
                //    impl Type {...}
                val forTypeNode = TypeNode.parse(ctx)
                val associatedItems = AssociatedItemsNode.parse(ctx)
                return ItemNode.ImplItemNode.InherentImplItemNode(
                    typeNode = forTypeNode,
                    associatedItems = associatedItems
                )
            }
        }
    }
}