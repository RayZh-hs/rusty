package rusty.opt

import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.PredecessorAnalysis
import space.norb.llvm.analysis.presets.UseDefAnalysis
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.transformation.presets.CFGSimplifyPass
import space.norb.llvm.transformation.presets.Mem2RegPass
import java.io.OutputStream
import java.io.PrintStream

object IROptimizer {
    
    private val passes: List<IRPass> = listOf(
        Mem2RegPass,
        CFGSimplifyPass,
    )
    
    private fun runPass(irModule: Module, manager: AnalysisManager, pass: IRPass, dumpToScreen: Boolean = false): Module {
        val optimized = pass.run(irModule, manager)
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
