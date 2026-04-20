package rusty.opt

import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.structure.Module
import java.io.OutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException

class IROptimizer {
    companion object {
        fun run(irModule: Module, dumpToScreen: Boolean = false): Module {
            val optimized = LlvmOptimizationPipeline.run(irModule)
            if (dumpToScreen) {
                dumpScreen(optimized)
            }
            return optimized
        }
    }
}

private object LlvmOptimizationPipeline {
    private const val ANALYSIS_MANAGER = "space.norb.llvm.analysis.AnalysisManager"
    private const val ANALYSIS = "space.norb.llvm.analysis.Analysis"
    private const val IR_PASS = "space.norb.llvm.transformation.IRPass"

    private val requiredAnalyses = listOf(
        "space.norb.llvm.analysis.presets.PredecessorAnalysis",
        "space.norb.llvm.analysis.presets.UseDefAnalysis",
        "space.norb.llvm.analysis.presets.DominatorTreeAnalysis",
    )

    private val optimizationPasses = listOf(
        "space.norb.llvm.transformation.presets.CFGSimplifyPass",
        "space.norb.llvm.transformation.presets.Mem2RegPass",
    )

    fun run(module: Module): Module {
        val api = loadApi()
        val analysisManager = api.analysisManagerClass.getConstructor(Module::class.java).newInstance(module)
        val register = api.analysisManagerClass.getMethod("register", api.analysisClass)
        for (analysisName in requiredAnalyses) {
            register.invoke(analysisManager, singleton(analysisName))
        }

        val passRun = api.passClass.getMethod("run", Module::class.java, api.analysisManagerClass)
        val updateAnalysisManager = api.passClass.getMethod("updateAnalysisManager", api.analysisManagerClass)

        var current = module
        for (passName in optimizationPasses) {
            val pass = singleton(passName)
            current = invokePass(passRun, pass, current, analysisManager)
            canonicalizeTerminatorInstructions(current)
            updateAnalysisManager.invoke(pass, analysisManager)
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

    private fun loadApi(): PassApi = try {
        PassApi(
            analysisManagerClass = Class.forName(ANALYSIS_MANAGER),
            analysisClass = Class.forName(ANALYSIS),
            passClass = Class.forName(IR_PASS),
        )
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException(
            "LLVM optimization passes are unavailable. Use a space.norb:llvm build that includes " +
                "space.norb.llvm.transformation, or set LLVM_LIB_PATH to the local LLVM library project.",
            e,
        )
    }

    private fun singleton(className: String): Any {
        val klass = Class.forName(className)
        return klass.getField("INSTANCE").get(null)
    }

    private fun invokePass(method: java.lang.reflect.Method, pass: Any, module: Module, analysisManager: Any): Module {
        val stdout = System.out
        val sink = PrintStream(OutputStream.nullOutputStream())
        try {
            System.setOut(sink)
            return method.invoke(pass, module, analysisManager) as Module
        } catch (e: InvocationTargetException) {
            throw e.targetException
        } finally {
            System.setOut(stdout)
            sink.close()
        }
    }

    private data class PassApi(
        val analysisManagerClass: Class<*>,
        val analysisClass: Class<*>,
        val passClass: Class<*>,
    )
}
