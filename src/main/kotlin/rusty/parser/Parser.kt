package rusty.parser

import rusty.core.Stream
import rusty.lexer.TokenBearer
import rusty.parser.nodes.CrateNode
import rusty.parser.putils.Context
import java.util.*

typealias ASTTree = CrateNode
typealias TokenStream = Stream<TokenBearer>

class Parser {
    companion object {
        fun run(input: MutableList<TokenBearer>): ASTTree {
            val tokenStream = Stream(input)
            val ctx = Context(
                tokenStream
            )
            return CrateNode.parse(ctx)
        }
    }
}
