import cmd.CommandParserConfigEntry
import cmd.Requirement
import java.io.File

fun main(args: Array<String>) {
    val parser = cmd.CommandParser(listOf(
        CommandParserConfigEntry("i", Requirement.REQUIRED),    // input file path
        CommandParserConfigEntry("m", Requirement.OPTIONAL),    // mode of compilation
        CommandParserConfigEntry("o", Requirement.REQUIRED)     // output file path
    ))
    val parsed = parser.parse(args)
    for (entry in parsed) {
        println("[${entry.key}] ${entry.value}")
    }
}