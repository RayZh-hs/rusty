package rusty.ir

import java.io.File

fun IRConstructor.Companion.dump(irModule: String, outputPath: String) {
    val file = File(outputPath)
    file.parentFile?.mkdirs()
    file.writeText(irModule)
}

fun IRConstructor.Companion.dumpScreen(irModule: String) {
    println("[rusty] IR dump:")
    println(irModule)
    if (!irModule.endsWith("\n")) {
        println()
    }
}
