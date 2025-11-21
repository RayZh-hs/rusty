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
    val builder: IRBuilder,
    val plan: FunctionPlan,
    val function: Function,
    val scope: Scope,
    val locals: ArrayDeque<MutableMap<SemanticSymbol.Variable, Value>> = ArrayDeque(),
    val loopStack: ArrayDeque<LoopFrame> = ArrayDeque(),
    var terminated: Boolean = false,
    val returnSlot: Value? = null,
)
