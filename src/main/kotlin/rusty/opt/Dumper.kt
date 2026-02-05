package rusty.opt

import com.andreapivetta.kolor.green
import space.norb.llvm.structure.Module
import java.io.File

fun IROptimizer.Companion.dump(irModule: Module, outputPath: String) {
    val file = File(outputPath)
    file.parentFile?.mkdirs()
    file.writeText(irModule.toIRString())
}

fun IROptimizer.Companion.dumpScreen(irModule: Module) {
    println("[rusty] IR Opt dump:".green())
    val irString = irModule.toIRString()
    println(irString)
    if (!irString.endsWith("\n")) {
        println()
    }
}
