package rusty

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import rusty.core.CompileError
import rusty.lexer.Lexer
import rusty.parser.Parser
import rusty.preprocessor.Preprocessor
import rusty.semantic.SemanticConstructor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Tag("official")
class OfficialSemanticTests {

    private data class Case(val name: String, val dir: Path, val source: Path, val info: Path, val expectedExit: Int)

    @TestFactory
    fun semanticOfficialCases(): Collection<DynamicTest> {
        val cases = collectCases()
        return cases.map { c ->
            val displayName = "${c.dir.parent.fileName}/${c.dir.fileName} (exit=${c.expectedExit})"
            DynamicTest.dynamicTest(displayName) {
                val content = File(c.source.toString()).readText()
                CompileError.registerSource(content.split('\n'))

                val actualExit = try {
                    val pre = Preprocessor.run(content)
                    val lex = Lexer.run(pre)
                    val ast = Parser.run(lex)
                    SemanticConstructor.run(ast, dumpToScreen = false)
                    0
                } catch (_: Throwable) {
                    1
                }

                val expectedBinary = if (c.expectedExit == 0) 0 else 1
                assertEquals(expectedBinary, actualExit, "Exit code mismatch for ${c.name}")
            }
        }
    }

    private fun collectCases(): List<Case> {
        val base = Paths.get("src", "test", "resources", "@official")
        val result = mutableListOf<Case>()
        listOf("semantic-1").forEach { stage ->
            val stageDir = base.resolve(stage)
            if (!Files.isDirectory(stageDir)) return@forEach
            Files.newDirectoryStream(stageDir).use { dirs ->
                for (dir in dirs) {
                    if (!Files.isDirectory(dir)) continue
                    val name = dir.fileName.toString()
                    val rx = dir.resolve("$name.rx")
                    val info = dir.resolve("testcase_info.json")
                    if (Files.exists(rx) && Files.exists(info)) {
                        val expected = readCompileExitCode(info) ?: continue
                        result += Case(name, dir, rx, info, expected)
                    }
                }
            }
        }
        return result.sortedBy { it.name }
    }

    private fun readCompileExitCode(infoPath: Path): Int? {
        return try {
            val text = File(infoPath.toString()).readText()
            val key = "\"compileexitcode\""
            val idx = text.indexOf(key)
            if (idx < 0) return null
            val after = text.substring(idx + key.length)
            val colon = after.indexOf(":")
            if (colon < 0) return null
            val rest = after.substring(colon + 1)
            val numStr = rest.takeWhile { it != ',' && it != '}' }.trim()
            numStr.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
