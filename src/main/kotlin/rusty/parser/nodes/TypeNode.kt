package rusty.parser.nodes

import rusty.parser.putils.Context

// I will assume that Type refers to TypeNoBounds.
sealed class TypeNode {
    companion object;

    data class TypePath(val path: List<Any>, val isGlobal: Boolean) : TypeNode() {}
    data class NeverType(val type: TypeNode): TypeNode()
    data class TupleType(val types: List<TypeNode>): TypeNode()
    data class ArrayType(val type: TypeNode, val length: ExpressionNode): TypeNode()
    data class SliceType(val type: TypeNode): TypeNode()
    data class ReferenceType(val type: TypeNode, val isMut: Boolean): TypeNode()
    data object InferredType: TypeNode()
    // not implemented: QualifiedPathInType
}