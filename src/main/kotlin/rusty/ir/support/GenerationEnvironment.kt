package rusty.ir.support

import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.Scope
import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.core.Value
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function

data class LoopFrame(
    val breakTarget: BasicBlock,
    val continueTarget: BasicBlock,
    val resultSlot: Value? = null,
)

data class FunctionEnvironment(
    val bodyBuilder: IRBuilder,
    val allocaBuilder: IRBuilder,
    val allocaEntryBlock: BasicBlock,
    val bodyEntryBlock: BasicBlock,
    val plan: FunctionPlan,
    val function: Function,
    val scope: Scope,
    val renamer: Renamer,
    val locals: ArrayDeque<MutableMap<SemanticSymbol.Variable, Value>> = ArrayDeque(),
    val loopStack: ArrayDeque<LoopFrame> = ArrayDeque(),
    var terminated: Boolean = false,
    val returnSlot: Value? = null,
) {
    fun findLocalSymbol(identifier: String): SemanticSymbol.Variable? {
        return locals.asReversed().firstNotNullOfOrNull { table ->
            table.entries.toList().asReversed()
                .firstOrNull { it.key.identifier == identifier }
                ?.key
        }
    }

    fun findLocalSlot(symbol: SemanticSymbol.Variable): Value? {
        return locals.asReversed().firstNotNullOfOrNull { table ->
            table[symbol] ?: table.entries.toList().asReversed()
                .firstOrNull { it.key.identifier == symbol.identifier }
                ?.value
        }
    }
}
