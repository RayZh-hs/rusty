package rusty

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.fail

/**
 * Test harness that iterates through all .rs files under src/test/resources
 * and invokes the main compiler pipeline on them. A test passes if the
 * program finishes without throwing for normal cases. If the file contains a
 * line that starts with `// ! Expected to fail` then we expect an exception.
 */
class AllRsResourceTests {
    private val resourcesRoot = File("src/test/resources")

    @TestFactory
    fun rustResourceTests(): Collection<DynamicTest> {
        val rsFiles = resourcesRoot.walkTopDown().filter { it.isFile && it.extension == "rs" }.toList()
        val outputDir = File("build/test-generated").toPath()
        outputDir.createDirectories()

        return rsFiles.map { file ->
            DynamicTest.dynamicTest(file.relativeTo(resourcesRoot).path) {
                val expectsFailure = file.readLines().any { it.trimStart().startsWith("// ! Expected to fail") }
                val outFile = outputDir.resolve(file.name + ".out").toFile()
                val mode = when {
                    file.path.contains("/preprocessor/") -> "preprocess"
                    file.path.contains("/lexer/") -> "lex"
                    else -> "parse"
                }
                val args = arrayOf("-i", file.path, "-o", outFile.path, "-m", mode)
                var threw = false
                try {
                    main(args)
                } catch (t: Throwable) {
                    threw = true
                    if (!expectsFailure) {
                        t.printStackTrace()
                        fail("Unexpected failure processing ${file.path}: ${t.message}")
                    }
                }
                if (expectsFailure && !threw) {
                    fail("Expected failure but succeeded for ${file.path}")
                }
            }
        }
    }
}
