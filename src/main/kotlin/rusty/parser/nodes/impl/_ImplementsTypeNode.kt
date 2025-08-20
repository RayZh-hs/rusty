package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.PathIndentSegmentNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.utils.afterWhich
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken

val TypeNode.Companion.name get() = "TypeNode"

fun TypeNode.Companion.parse(ctx: Context): TypeNode {
    // Since typing systems are coupled with semantic check,
    // and the internal workings of it is rather tedious,
    // we will call the context stack here under a unified name.
    ctx.callMe(name) {
        return parseTypeNode(ctx)
    }
}

// Use a custom function as wrapper so that the callMe() annotator is called only once
// It is exposed for external use (see _SupportsTypeNode)
fun parseTypeNode(ctx: Context): TypeNode {
    return when (ctx.peekToken()) {
        Token.O_DOUBLE_COLON, Token.I_IDENTIFIER, Token.K_SELF, Token.K_TYPE_SELF -> parseTypePath(ctx)
        Token.O_NOT -> parseNeverType(ctx)
        Token.O_LPAREN -> parseTupleOrGroup(ctx)
        Token.O_LSQUARE -> parseArrayOrSlice(ctx)
        Token.O_AND -> parseReferenceType(ctx)
        Token.O_UNDERSCORE -> {
            TypeNode.InferredType(ctx.peekPointer()).afterWhich {
                ctx.stream.consume(1)
            }
        }

        else -> throw CompileError("Unidentified typing prefix: ${ctx.peekToken()}")
    }
}

private fun parseTypePath(ctx: Context): TypeNode.TypePath {
    val pointer = ctx.peekPointer()
    return TypeNode.TypePath(PathIndentSegmentNode.parse(ctx), pointer)
}

private fun parseNeverType(ctx: Context): TypeNode.NeverType {
    val pointer = ctx.peekPointer()
    putilsExpectToken(ctx, Token.O_NOT)
    return TypeNode.NeverType(parseTypeNode(ctx), pointer)
}

private fun parseTupleOrGroup(ctx: Context): TypeNode {
    val pointer = ctx.peekPointer()
    putilsExpectToken(ctx, Token.O_LPAREN)
    if (ctx.peekToken() == Token.O_RPAREN) {
        // () forms a unit type, aka. 0-tuple
        ctx.stream.consume(1)
        return TypeNode.TupleType(listOf(), pointer)
    }
    val firstType = parseTypeNode(ctx)
    when (val nextToken = ctx.stream.read().token) {
        Token.O_RPAREN -> return firstType  // (type)
        Token.O_COMMA -> {
            // (type, ...)
            val listOfTypes = mutableListOf<TypeNode>()
            var hasTrailingComma = true
            listOfTypes.add(firstType)
            while (ctx.peekToken() != Token.O_RPAREN) {
                if (!hasTrailingComma)
                    throw CompileError("Types in a Tuple Type must be separated by commas").with(ctx).at(ctx.peekPointer())
                listOfTypes.add(parseTypeNode(ctx))
                hasTrailingComma = putilsConsumeIfExistsToken(ctx, Token.O_COMMA)
            }
            ctx.stream.consume(1)
            return TypeNode.TupleType(listOfTypes, pointer)
        }
        else -> throw CompileError("Unexpected token when parsing Tuple or Group: $nextToken").with(ctx).at(ctx.peekPointer())
    }
}

private fun parseArrayOrSlice(ctx: Context): TypeNode {
    val pointer = ctx.peekPointer()
    putilsExpectToken(ctx, Token.O_LSQUARE)
    val type = parseTypeNode(ctx)
    return when (val nextToken = ctx.stream.read().token) {
        Token.O_RSQUARE -> {
            // SliceType [type]
            TypeNode.SliceType(type, pointer)
        }
        Token.O_SEMICOLON -> {
            // ArrayType [type; expression]
            val size = ExpressionNode.parse(ctx)
            putilsExpectToken(ctx, Token.O_RSQUARE)
            TypeNode.ArrayType(type, size, pointer)
        }
        else -> throw CompileError("Unexpected token when parsing Array or Slice: $nextToken").with(ctx).at(ctx.peekPointer())
    }
}

private fun parseReferenceType(ctx: Context): TypeNode.ReferenceType {
    val pointer = ctx.peekPointer()
    putilsExpectToken(ctx, Token.O_AND)
    val isMut = putilsConsumeIfExistsToken(ctx, Token.K_MUT)
    val type = parseTypeNode(ctx)
    return TypeNode.ReferenceType(type, isMut, pointer)
}