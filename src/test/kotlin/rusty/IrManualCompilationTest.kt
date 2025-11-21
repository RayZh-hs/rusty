package rusty

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.fail

class IrManualCompilationTest {

    @Test
    fun compileCustomFileToExecutable() {
        val target = System.getProperty(PROP_FILE) ?: run {
            assumeTrue(false) { "Skipping IR manual test: provide -D$PROP_FILE=/path/to/file.rs" }
            return
        }
        val clangBinary = System.getProperty(PROP_CLANG) ?: "clang"

        val input = resolveInput(target)
        require(Files.exists(input)) { "Input Rust file not found: $input" }

        val outputDir = Paths.get("build", "ir-manual")
        Files.createDirectories(outputDir)

        val caseName = sanitizeName(input)
        val irOutput = outputDir.resolve("$caseName.ll")
        val exeOutput = outputDir.resolve("$caseName.out")

        main(arrayOf("-i", input.toString(), "-o", irOutput.toString(), "-m", "ir"))

        assumeTrue(commandAvailable(clangBinary)) { "Skipping clang step: '$clangBinary' not found" }

        val clangResult = runProcess(listOf(clangBinary, irOutput.toString(), "-o", exeOutput.toString()))
        if (clangResult.exitCode != 0) {
            fail("clang failed (exit ${clangResult.exitCode}). Output:\n${clangResult.output}")
        }

        println("[IrManualCompilationTest] IR saved to $irOutput")
        println("[IrManualCompilationTest] Executable saved to $exeOutput")
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

    private fun sanitizeName(path: Path): String {
        val rawName = path.fileName.toString().substringBeforeLast('.')
        val hashSuffix = Integer.toHexString(path.toAbsolutePath().normalize().toString().hashCode())
        return "$rawName-$hashSuffix"
    }

    private fun commandAvailable(binary: String): Boolean {
        return try {
            val process = ProcessBuilder(binary, "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun runProcess(args: List<String>): ProcessResult {
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        return ProcessResult(exit, output)
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    companion object {
        private const val PROP_FILE = "customIrFile"
        private const val PROP_CLANG = "customIrClang"
    }
}
