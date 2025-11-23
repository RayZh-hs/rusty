package rusty

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.test.fail

class IrManualCompilationTest {

    @Test
    fun compileCustomFileToExecutable() {
        val target = System.getProperty(PROP_FILE) ?: run {
            assumeTrue(false) { "Skipping IR manual test: provide -D$PROP_FILE=/path/to/file.rs" }
            return
        }
        val input = resolveInput(target)
        require(Files.exists(input)) { "Input Rust file not found: $input" }

        val outputDir = Paths.get("build", "ir-manual")
        Files.createDirectories(outputDir)

        compileAndLinkSingle(input, outputDir, requireClang = !skipClang)
    }

    @TestFactory
    fun compileIrResourcesIndividually(): Collection<DynamicTest> {
        val baseDir = Paths.get("src", "test", "resources", "ir")
        require(Files.isDirectory(baseDir)) { "IR resource directory missing: $baseDir" }

        val outputDir = Paths.get("build", "ir-manual", "all")
        Files.createDirectories(outputDir)

        return Files.list(baseDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "rs" }
                .sorted()
                .map { file ->
                    DynamicTest.dynamicTest("clang IR ${file.fileName}") {
                        compileAndLinkSingle(file, outputDir, requireClang = !skipClang)
                    }
                }
                .toList()
        }
    }

    private fun resolveInput(pathStr: String): Path {
        val candidate = Paths.get(pathStr)
        if (candidate.isAbsolute && Files.exists(candidate)) return candidate

        val fromCwd = candidate.normalize()
        if (!candidate.isAbsolute && Files.exists(fromCwd)) return fromCwd

        val fromResources = Paths.get("src", "test", "resources").resolve(pathStr).normalize()
        if (Files.exists(fromResources)) return fromResources

        return candidate.toAbsolutePath().normalize()
    }

    companion object {
        private const val PROP_FILE = "customIrFile"
        private const val PROP_CLANG = "customIrClang"
    }

    private val skipClang: Boolean =
        System.getProperty(IrPipeline.PROP_NO_CLANG)?.equals("true", ignoreCase = true) == true

    private fun compileAndLinkSingle(input: Path, outputDir: Path, requireClang: Boolean) {
        val clangBinary = System.getProperty(PROP_CLANG) ?: IrPipeline.resolveClangBinary()
        val artifacts = IrPipeline.artifactPathsFor(input, outputDir)

        IrPipeline.emitIr(input, artifacts.irOutput)

        if (skipClang) {
            println("[IrManualCompilationTest] Skipping clang/link for $input due to -D${IrPipeline.PROP_NO_CLANG}=true")
            return
        }

        if (!IrPipeline.commandAvailable(clangBinary)) {
            if (requireClang) {
                fail("clang not available for $input")
            } else {
                assumeTrue(false) { "Skipping clang step: '$clangBinary' not found" }
            }
        }

        val clangResult = IrPipeline.linkWithPrelude(artifacts.irOutput, artifacts.exeOutput, clangBinary)
        if (clangResult.exitCode != 0) {
            fail(
                buildString {
                    append("clang failed (exit ${clangResult.exitCode}) for $input.\n")
                    append("Command: ${clangResult.args.joinToString(" ")}\n")
                    append("Output:\n${clangResult.output}")
                }
            )
        }

        println("[IrManualCompilationTest] IR saved to ${artifacts.irOutput}")
        println("[IrManualCompilationTest] Executable saved to ${artifacts.exeOutput}")
    }
}
