package rusty.opt.passes

// Recognize patterns like foo[a] and replace array access with advancing pointer arithmetic to substitute multiplication with addition.

import rusty.opt.passes.utils.NaturalLoop
import rusty.opt.passes.utils.findSimpleNaturalLoops
import rusty.opt.passes.utils.dominates
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.PointerType
import space.norb.llvm.values.constants.IntConstant

object LoopAddressReductionPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        val predecessors = am.get(PredecessorAnalysis::class)
        val dominators = am.get(DominatorTreeAnalysis::class)
        var changed = false

        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            val domInfo = dominators.getFunctionInfo(function) ?: continue
            val loops = findSimpleNaturalLoops(function, predecessors, domInfo.immediateDominators)
            for (loop in loops) {
                changed = runOnLoop(loop, domInfo.immediateDominators) || changed
            }
        }

        if (changed) am.invalidateAll() else am.invalidateNone()
        return module
    }

    private fun runOnLoop(
        loop: NaturalLoop,
        immediateDominators: Map<ULong, ULong?>,
    ): Boolean {
        val owners = instructionOwners(loop.blocks + loop.preheader)
        val plans = loop.header.instructions
            .filterIsInstance<PhiNode>()
            .mapNotNull { phi -> inductionPlan(phi, loop, owners) }
            .flatMap { induction -> gepPlans(induction, loop, immediateDominators, owners) }

        if (plans.isEmpty()) return false

        var changed = false
        for (plan in plans) {
            if (!plan.gep.hasUses()) continue
            rewrite(plan, loop)
            changed = true
        }
        return changed
    }

    private fun inductionPlan(
        phi: PhiNode,
        loop: NaturalLoop,
        owners: Map<Instruction, BasicBlock>,
    ): InductionPlan? {
        if (phi.type !is IntegerType) return null
        if (phi.incomingValues.size != 2) return null

        val start = phi.getIncomingValueForBlock(loop.preheader) ?: return null
        val latchValue = phi.getIncomingValueForBlock(loop.latch) ?: return null
        val update = latchValue as? AddInst ?: return null
        val one = update.rhs as? IntConstant
        if (update.lhs != phi || one?.value != 1L) return null
        if (owners[update] != loop.latch) return null

        return InductionPlan(phi, start, update)
    }

    private fun gepPlans(
        induction: InductionPlan,
        loop: NaturalLoop,
        immediateDominators: Map<ULong, ULong?>,
        owners: Map<Instruction, BasicBlock>,
    ): List<AddressPlan> {
        val result = mutableListOf<AddressPlan>()
        for (block in loop.blocks) {
            for (inst in block.instructions) {
                val gep = inst as? GetElementPtrInst ?: continue
                if (!isSimpleArrayIndex(gep, induction.phi)) continue
                if (!isLoopInvariant(gep.pointer, loop, owners)) continue
                if (!dominates(loop.header, block, immediateDominators)) continue

                val elementType = (gep.elementType as ArrayType).elementType
                result.add(AddressPlan(gep, induction, elementType))
            }
        }
        return result
    }

    private fun isSimpleArrayIndex(gep: GetElementPtrInst, index: Value): Boolean {
        val arrayType = gep.elementType as? ArrayType ?: return false
        if (arrayType.elementType.isArrayType() || arrayType.elementType.isStructType()) return false
        if (gep.indices.size != 2) return false
        val first = gep.indices[0] as? IntConstant ?: return false
        return first.value == 0L && gep.indices[1] == index
    }

    private fun isLoopInvariant(
        value: Value,
        loop: NaturalLoop,
        owners: Map<Instruction, BasicBlock>,
    ): Boolean {
        val owner = (value as? Instruction)?.let(owners::get) ?: return true
        return owner !in loop.blocks
    }

    private fun rewrite(plan: AddressPlan, loop: NaturalLoop) {
        val startGep = GetElementPtrInst.create(
            "${plan.gep.name ?: "addr"}.start",
            plan.gep.elementType,
            plan.gep.pointer,
            listOf(IntConstant(0L, IntegerType.I64), plan.induction.start),
        )
        loop.preheader.instructions.add(loop.preheader.insertionIndexBeforeTerminator(), startGep)

        val pointerPhi = PhiNode.createPlaceholder("${plan.gep.name ?: "addr"}.phi", PointerType)
        val headerInsertIndex = loop.header.instructions.indexOfLast { it is PhiNode } + 1
        loop.header.instructions.add(headerInsertIndex, pointerPhi)

        val nextGep = GetElementPtrInst.create(
            "${plan.gep.name ?: "addr"}.next",
            plan.elementType,
            pointerPhi,
            listOf(IntConstant(1L, IntegerType.I64)),
        )
        val latchInsertIndex = loop.latch.instructions.indexOf(plan.induction.update)
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: loop.latch.instructions.size
        loop.latch.instructions.add(latchInsertIndex, nextGep)

        pointerPhi.addIncomingMutable(startGep, loop.preheader)
        pointerPhi.addIncomingMutable(nextGep, loop.latch)

        plan.gep.replaceAllUsesWith(pointerPhi)
        plan.gep.detachOperands()
        for (block in loop.blocks) {
            if (block.instructions.remove(plan.gep)) break
        }
    }

    private fun instructionOwners(blocks: Collection<BasicBlock>): Map<Instruction, BasicBlock> =
        blocks.flatMap { block -> block.instructions.map { instruction -> instruction to block } }.toMap()

    private fun BasicBlock.insertionIndexBeforeTerminator(): Int {
        val terminator = terminator ?: return instructions.size
        val index = instructions.indexOf(terminator)
        return if (index >= 0) index else instructions.indexOfFirst { it is TerminatorInst }
            .takeIf { it >= 0 }
            ?: instructions.size
    }

    private fun GetElementPtrInst.detachOperands() {
        for (index in 0 until getNumOperands()) {
            space.norb.llvm.core.ValueUseRegistry.unregisterUse(getOperand(index), this)
        }
    }

    private data class InductionPlan(
        val phi: PhiNode,
        val start: Value,
        val update: AddInst,
    )

    private data class AddressPlan(
        val gep: GetElementPtrInst,
        val induction: InductionPlan,
        val elementType: space.norb.llvm.core.Type,
    )
}
