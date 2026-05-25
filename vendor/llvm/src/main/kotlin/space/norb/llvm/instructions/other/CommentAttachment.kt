package space.norb.llvm.instructions.other

import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.types.VoidType
import space.norb.llvm.visitors.IRVisitor

/**
 * Represents a pure IR comment that can be placed in the instruction stream.
 *
 * The comment is preserved in the printed IR (prefixed with ';') but has no
 * operands, side effects, or runtime semantics.
 */
class CommentAttachment(
    name: String?,
    val comment: String
) : OtherInst(name, VoidType, emptyList()) {
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitCommentAttachment(this)

    override fun getOpcodeName(): String = "comment"
}
