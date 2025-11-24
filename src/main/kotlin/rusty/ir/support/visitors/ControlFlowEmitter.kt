package rusty.ir.support.visitors

import rusty.ir.support.GeneratedValue
import rusty.ir.support.LoopFrame
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticType
import rusty.parser.nodes.ExpressionNode
import rusty.core.CompilerPointer
import space.norb.llvm.structure.BasicBlock

class ControlFlowEmitter(
    private val ctx: SemanticContext,
    private val emitExpr: (ExpressionNode) -> GeneratedValue?,
    private val currentEnv: () -> rusty.ir.support.FunctionEnvironment,
    private val addBlockComment: (CompilerPointer, String) -> Unit,
) {
    fun emitIf(node: ExpressionNode.WithBlockExpressionNode.IfBlockExpressionNode): GeneratedValue? {
        val env = currentEnv()
        val fn = env.function
        val resultType = ctx.expressionTypeMemory.recall(node) { SemanticType.UnitType }
        val needsValue = resultType != SemanticType.UnitType
        val auxSlot = if (needsValue) {
            env.builder.insertAlloca(resultType.toIRType(), Name.blockResult(env.renamer).identifier)
        } else {
            null
        }
        val merge = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)

        val firstGuard = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        env.builder.insertBr(firstGuard)
        var nextGuardBlock: BasicBlock = firstGuard
        node.ifs.forEachIndexed { index, clause ->
            env.builder.positionAtEnd(nextGuardBlock)
            env.terminated = false
            addBlockComment(clause.condition.expression.pointer, if (index == 0) "if-guard" else "else-if-guard")
            val condValue = emitExpr(clause.condition.expression) ?: return null
            val thenBlock = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
            val elseBlock = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
            env.builder.insertCondBr(condValue.value, thenBlock, elseBlock)

            env.builder.positionAtEnd(thenBlock)
            addBlockComment(clause.then.pointer, "then-block")
            val thenVal = emitExpr(clause.then)
            if (auxSlot != null && thenVal != null) env.builder.insertStore(thenVal.value, auxSlot)
            if (!env.terminated) env.builder.insertBr(merge)
            env.terminated = false

            nextGuardBlock = elseBlock
        }

        val finalElseBlock = nextGuardBlock
        env.builder.positionAtEnd(finalElseBlock)
        node.elseBranch?.let {
            addBlockComment(it.pointer, "else-block")
            val elseVal = emitExpr(it)
            if (auxSlot != null && elseVal != null) env.builder.insertStore(elseVal.value, auxSlot)
        } ?: addBlockComment(node.pointer, "else-block")
        if (!env.terminated) env.builder.insertBr(merge)
        env.terminated = false

        env.builder.positionAtEnd(merge)
        addBlockComment(node.pointer, "end-if")
        return auxSlot?.let {
            GeneratedValue(env.builder.insertLoad(resultType.toIRType(), it, Name.blockResult(env.renamer).identifier), resultType)
        }
    }

    fun emitWhile(node: ExpressionNode.WithBlockExpressionNode.WhileBlockExpressionNode): GeneratedValue? {
        val env = currentEnv()
        val fn = env.function
        val head = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        val body = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        val exit = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)

        env.builder.insertBr(head)
        env.builder.positionAtEnd(head)
        addBlockComment(node.condition.expression.pointer, "while-guard")
        val cond = emitExpr(node.condition.expression) ?: return null
        env.builder.insertCondBr(cond.value, body, exit)

        env.builder.positionAtEnd(body)
        addBlockComment(node.expression.pointer, "while-body")
        env.loopStack.addLast(LoopFrame(exit, head))
        emitExpr(node.expression)
        if (!env.terminated) env.builder.insertBr(head)
        env.loopStack.removeLast()
        env.terminated = false

        env.builder.positionAtEnd(exit)
        addBlockComment(node.pointer, "while-exit")
        return null
    }

    fun emitLoop(node: ExpressionNode.WithBlockExpressionNode.LoopBlockExpressionNode): GeneratedValue? {
        val env = currentEnv()
        val fn = env.function
        val body = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        val exit = fn.insertBasicBlock(Name.block(env.renamer).identifier, false)
        env.builder.insertBr(body)
        env.builder.positionAtEnd(body)
        addBlockComment(node.pointer, "loop-body")
        env.loopStack.addLast(LoopFrame(exit, body))
        emitExpr(node.expression)
        if (!env.terminated) env.builder.insertBr(body)
        env.loopStack.removeLast()
        env.terminated = false
        env.builder.positionAtEnd(exit)
        addBlockComment(node.pointer, "loop-exit")
        return null
    }

    fun emitBreak(node: ExpressionNode.WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode) {
        val env = currentEnv()
        val frame = env.loopStack.lastOrNull() ?: return
        node.expr?.let { emitExpr(it) }
        env.builder.insertBr(frame.breakTarget)
        env.terminated = true
    }

    fun emitContinue() {
        val env = currentEnv()
        val frame = env.loopStack.lastOrNull() ?: return
        env.builder.insertBr(frame.continueTarget)
        env.terminated = true
    }
}
