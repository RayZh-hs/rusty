package rusty.ir.support

import rusty.semantic.support.SemanticType
import space.norb.llvm.core.Value

data class GeneratedValue(
    val value: Value,
    val type: SemanticType,
)
