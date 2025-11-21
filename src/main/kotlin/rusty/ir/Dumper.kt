package rusty.ir

import com.andreapivetta.kolor.green
import java.io.File

fun IRConstructor.Companion.dump(irModule: String, outputPath: String) {
    val file = File(outputPath)
    file.parentFile?.mkdirs()
    file.writeText(irModule)
}

fun IRConstructor.Companion.dumpScreen(irModule: String) {
    println("[rusty] IR dump:".green())
    println(irModule)
    if (!irModule.endsWith("\n")) {
        println()
    }
}
