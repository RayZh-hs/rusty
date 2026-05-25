package space.norb.llvm.transformation.presets

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.ReadonlyIRPass
import space.norb.llvm.visitors.IRValidator

object ValidationPass : ReadonlyIRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        val validator = IRValidator()
        if (!validator.validate(module)) {
            val errors = validator.getErrors()
            throw IllegalStateException("Module validation failed:\n" + errors.joinToString("\n") { "  - $it" })
        }
        return module
    }
}