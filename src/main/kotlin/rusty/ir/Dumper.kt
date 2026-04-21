package rusty.ir

import com.andreapivetta.kolor.green
import space.norb.llvm.structure.Module
import java.io.File

fun IRConstructor.dump(irModule: Module, outputPath: String) {
    val file = File(outputPath)
    file.parentFile?.mkdirs()
    file.writeText(irModule.toIRString())
}

fun IRConstructor.dumpScreen(irModule: Module) {
    println("[rusty] IR dump:".green())
    val irString = irModule.toIRString()
    println(irString)
    if (!irString.endsWith("\n")) {
        println()
    }
}
