package rusty.parser.nodes

import rusty.core.CompilerPointer

/**
 * Base AST node interface with common companion behavior.
 * All concrete AST nodes should extend this (directly or indirectly via sealed subclasses).
 * 'name' identifies the grammar production (best-effort).
 */
abstract class ASTNode(override val pointer: CompilerPointer) : WithPointer {
    companion object {
        val name get() = "ASTNode"
    }
}

/**
 * Common interface for AST nodes that carry a pointer to the source code.
 * */
interface WithPointer {
    val pointer: CompilerPointer
}

// The @Peekable and @Parsable annotations are used to mark AST nodes that can be peeked at or parsed.
// They reside in the utils folder