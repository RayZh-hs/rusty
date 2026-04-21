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
    fun run(irModule: Module, dumpToScreen: Boolean = false): Module {
        val optimized = LlvmOptimizationPipeline.run(irModule)
        if (dumpToScreen) {
            dumpScreen(optimized)
        }
        return optimized
    }
}

private object LlvmOptimizationPipeline {
    private val requiredAnalyses: List<Analysis<*>> = listOf(
        PredecessorAnalysis,
        UseDefAnalysis,
        DominatorTreeAnalysis,
    )

    private val optimizationPasses: List<IRPass> = listOf(
        CFGSimplifyPass,
        Mem2RegPass,
    )

    fun run(module: Module): Module {
        val analysisManager = AnalysisManager(module)
        for (analysis in requiredAnalyses) {
            analysisManager.register(analysis)
        }

        var current = module
        for (pass in optimizationPasses) {
            current = invokePass(pass, current, analysisManager)
            canonicalizeTerminatorInstructions(current)
            pass.updateAnalysisManager(analysisManager)
        }
        return current
    }

    private fun canonicalizeTerminatorInstructions(module: Module) {
        for (function in module.functions) {
            for (block in function.basicBlocks) {
                val terminator = block.terminator ?: continue
                block.instructions.removeAll { it is TerminatorInst && it !== terminator }
                block.instructions.remove(terminator)
                block.instructions.add(terminator)
            }
        }
    }

    private fun invokePass(pass: IRPass, module: Module, analysisManager: AnalysisManager): Module {
        val stdout = System.out
        val sink = PrintStream(OutputStream.nullOutputStream())
        try {
            System.setOut(sink)
            return pass.run(module, analysisManager)
        } finally {
            System.setOut(stdout)
            sink.close()
        }
    }
}
