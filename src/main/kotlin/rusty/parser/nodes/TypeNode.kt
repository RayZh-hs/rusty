package rusty.parser.nodes

import rusty.core.CompilerPointer
import rusty.parser.putils.Context

// I will assume that Type refers to TypeNoBounds.
sealed class TypeNode(pointer: CompilerPointer): ASTNode(pointer) {
    companion object;

    data class TypePath(val pathSegmentNode: PathIndentSegmentNode, override val pointer: CompilerPointer) : TypeNode(pointer)
    data class NeverType(val type: TypeNode, override val pointer: CompilerPointer): TypeNode(pointer)
    data class TupleType(val types: List<TypeNode>, override val pointer: CompilerPointer): TypeNode(pointer)
    data class ArrayType(val type: TypeNode, val length: ExpressionNode, override val pointer: CompilerPointer): TypeNode(pointer)
    data class SliceType(val type: TypeNode, override val pointer: CompilerPointer): TypeNode(pointer)
    data class ReferenceType(val type: TypeNode, val isMut: Boolean, override val pointer: CompilerPointer): TypeNode(pointer)
    data class InferredType(override val pointer: CompilerPointer): TypeNode(pointer)
}