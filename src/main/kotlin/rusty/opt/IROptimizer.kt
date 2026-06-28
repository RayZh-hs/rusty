package rusty.opt

import rusty.opt.passes.AggressiveDeadCodeEliminationPass
import rusty.opt.passes.SizeInliningPass
import rusty.opt.passes.GlobalValueNumberingPass
import rusty.opt.passes.IdenticalGepReductionPass
import rusty.opt.passes.InstCombineCleanupPass
import rusty.opt.passes.LoopAddressReductionPass
import rusty.opt.passes.LoopCounterPromotionPass
import rusty.opt.passes.LoopInvariantCodeMotionPass
import rusty.opt.passes.PointerSlotForwardingPass
import rusty.opt.passes.ScalarReplacementOfAggregatesPass
import rusty.opt.passes.SmallMemcopyLoweringPass
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.analysis.presets.UseDefAnalysis
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.transformation.presets.CFGSimplifyPass
import space.norb.llvm.transformation.presets.FunctionInliningPass
import space.norb.llvm.transformation.presets.Mem2RegPass
import java.io.OutputStream
import java.io.PrintStream

object IROptimizer {
    
    // Inline small, non-recursive callees up to this many raw IR instructions. Kept moderate: the
    // post-inline memory traffic (parameter/local allocas, return forwarding) is cleaned up by the
    // SROA / Mem2Reg / GVN / InstCombine passes that run afterwards, so the inlined bodies collapse
    // into the caller's register-resident values rather than spilling.
    private const val INLINE_INSTRUCTION_THRESHOLD = 40

    private val functionInliningPass = FunctionInliningPass { callee ->
        callee.basicBlocks.sumOf { it.instructions.size } < INLINE_INSTRUCTION_THRESHOLD
    }

    private val passes: List<IRPass> = listOf(
        SizeInliningPass,
        functionInliningPass,
        SmallMemcopyLoweringPass,
        InstCombineCleanupPass,
        PointerSlotForwardingPass,
        ScalarReplacementOfAggregatesPass,
        Mem2RegPass,
        AggressiveDeadCodeEliminationPass,
        InstCombineCleanupPass,
        IdenticalGepReductionPass,
        GlobalValueNumberingPass,
        InstCombineCleanupPass,
        LoopInvariantCodeMotionPass,
        LoopAddressReductionPass,
        InstCombineCleanupPass,
        LoopCounterPromotionPass,
        InstCombineCleanupPass,
        CFGSimplifyPass,
        AggressiveDeadCodeEliminationPass,
        InstCombineCleanupPass,
    )
    
    private fun runPass(irModule: Module, manager: AnalysisManager, pass: IRPass, dumpToScreen: Boolean = false): Module {
        val optimized = pass.run(irModule, manager)
        pass.updateAnalysisManager(manager)
        if (dumpToScreen) {
            println("After ${pass::class.simpleName}:")
            dumpScreen(optimized)
        }
        return optimized
    }

    fun run(irModule: Module, dumpToScreen: Boolean = false): Module {
        val manager = AnalysisManager(module = irModule)
        for (pass in passes) {
            runPass(irModule, manager, pass, dumpToScreen)
        }
        return irModule
    }
}
