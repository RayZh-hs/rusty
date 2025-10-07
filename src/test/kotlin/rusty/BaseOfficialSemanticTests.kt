package rusty

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import rusty.core.CompileError
import rusty.lexer.Lexer
import rusty.parser.Parser
import rusty.preprocessor.Preprocessor
import rusty.semantic.SemanticConstructor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertFails

// This is an abstract class, not a runnable test suite itself.
abstract class BaseOfficialSemanticTests {

    // This property must be implemented by the subclasses.
    abstract val baseResourcePath: String

    private data class Case(val name: String, val dir: Path, val source: Path, val expectedExit: Int)

    @TestFactory
    fun semanticOfficialCases(): Collection<DynamicTest> {
        val cases = collectCases()
        return cases.map { c ->
            val displayName = "${c.dir.parent.parent.fileName}/${c.dir.fileName} (exit=${c.expectedExit})"
            DynamicTest.dynamicTest(displayName) {
                val content = File(c.source.toString()).readText()
                CompileError.registerSource(content.split('\n'))

                val compilationLogic = {
                    val pre = Preprocessor.run(content)
                    val lex = Lexer.run(pre)
                    val ast = Parser.run(lex)
                    SemanticConstructor.run(ast, dumpToScreen = false)
                }

                if (c.expectedExit == 0) {
                    assertDoesNotThrow(
                        "Compilation was expected to succeed for ${c.name} but failed:",
                        compilationLogic
                    )
                } else {
                    assertFails("Compilation was expected to fail for ${c.name} but it succeeded.") {
                        compilationLogic()
                    }
                }
            }
        }
    }

    private fun collectCases(): List<Case> {
        // It now uses the abstract property to find the correct directory.
        val base = Paths.get("src", "test", "resources", baseResourcePath)
        val result = mutableListOf<Case>()
        listOf("semantic-1", "semantic-2").forEach stages@{ stage ->
            val stageDir = base.resolve(stage)
            val globalJson = stageDir.resolve("global.json")

            if (!Files.exists(globalJson)) return@stages

            val jsonContent = File(globalJson.toString()).readText()
            val objectRegex = """\{.*?\}""".toRegex(RegexOption.DOT_MATCHES_ALL)

            objectRegex.findAll(jsonContent).forEach cases@{ match ->
                val objText = match.value
                val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                val exitCodeRegex = """"compileexitcode"\s*:\s*(-?\d+)""".toRegex()

                val name = nameRegex.find(objText)?.groupValues?.getOrNull(1) ?: return@cases
                val expectedExit = exitCodeRegex.find(objText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@cases

                val dir = stageDir.resolve("src").resolve(name)
                val rx = dir.resolve("$name.rx")

                if (Files.exists(rx)) {
                    result += Case(name, dir, rx, expectedExit)
                }
            }
        }
        return result.sortedBy { it.name }
    }
}