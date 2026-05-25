package space.norb.llvm.transformation.utility

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.structure.Module

/**
 * An IRPassSequence is the composition of multiple IRPasses.
 *
 * It runs multiple IRPasses in sequence, passing the output of one pass as the input to the next, and handles analysis
 * result forwarding automatically.
 *
 * @see IRPass
 * */
class IRPassSequence : IRPass() {
    companion object {
        fun of(vararg passes: IRPass) = IRPassSequence().apply { passes.forEach { append(it) } }
        fun of(passes: List<IRPass>) = IRPassSequence().apply { passes.forEach { append(it) } }

        @Suppress("UNUSED")
        val O0 = this.of()
    }

    private val passes = mutableListOf<IRPass>()

    fun append(pass: IRPass) = apply { passes.add(pass) }
    override fun run(module: Module, am: AnalysisManager) =
        passes.fold(module) { mod, pass -> pass.run(mod, am).also { pass.updateAnalysisManager(am) } }

    /**
     * Shorthand for running the sequence on a module with a fresh analysis manager.
     */
    fun run(module: Module) = run(module, AnalysisManager(module))
}