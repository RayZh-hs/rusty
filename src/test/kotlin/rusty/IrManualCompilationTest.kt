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
        val clangBinary = System.getProperty(PROP_CLANG) ?: "clang"

        val input = resolveInput(target)
        require(Files.exists(input)) { "Input Rust file not found: $input" }

        val outputDir = Paths.get("build", "ir-manual")
        Files.createDirectories(outputDir)

        compileAndLinkSingle(input, clangBinary, outputDir, requireClang = false)
    }

    @TestFactory
    fun compileIrResourcesIndividually(): Collection<DynamicTest> {
        val shouldRunAll = System.getProperty(PROP_RUN_ALL)?.equals("true", ignoreCase = true) == true
        assumeTrue(shouldRunAll) { "Skipping IR clang-all run: set -D$PROP_RUN_ALL=true to enable" }

        val clangBinary = System.getProperty(PROP_CLANG) ?: "clang"
        require(commandAvailable(clangBinary)) { "clang not found; set -D$PROP_CLANG=/path/to/clang" }

        val baseDir = Paths.get("src", "test", "resources", "ir")
        require(Files.isDirectory(baseDir)) { "IR resource directory missing: $baseDir" }

        val outputDir = Paths.get("build", "ir-manual", "all")
        Files.createDirectories(outputDir)

        return Files.list(baseDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "rs" }
                .sorted()
                .map { file ->
                    DynamicTest.dynamicTest("clang IR ${file.fileName}") {
                        compileAndLinkSingle(file, clangBinary, outputDir, requireClang = true)
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
        private const val PROP_RUN_ALL = "irClangAll"
    }

    private fun compileAndLinkSingle(input: Path, clangBinary: String, outputDir: Path, requireClang: Boolean) {
        val caseName = sanitizeName(input)
        val irOutput = outputDir.resolve("$caseName.ll")
        val exeOutput = outputDir.resolve("$caseName.out")

        main(arrayOf("-i", input.toString(), "-o", irOutput.toString(), "-m", "ir"))

        if (!commandAvailable(clangBinary)) {
            if (requireClang) {
                fail("clang not available for $input")
            } else {
                assumeTrue(false) { "Skipping clang step: '$clangBinary' not found" }
            }
        }

        val preludeDir = Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude")
        val preludeLl = preludeDir.resolve("prelude.ll")
        val preludeCLl = preludeDir.resolve("prelude.c.ll")
        listOf(preludeLl, preludeCLl).forEach {
            require(Files.exists(it)) { "Prelude IR missing: $it" }
        }

        val clangArgs = listOf(
            clangBinary,
            irOutput.toString(),
            preludeLl.toString(),
            preludeCLl.toString(),
            "-o",
            exeOutput.toString()
        )
        val clangResult = runProcess(clangArgs)
        if (clangResult.exitCode != 0) {
            fail(
                buildString {
                    append("clang failed (exit ${clangResult.exitCode}) for $input.\n")
                    append("Command: ${clangArgs.joinToString(" ")}\n")
                    append("Output:\n${clangResult.output}")
                }
            )
        }

        println("[IrManualCompilationTest] IR saved to $irOutput")
        println("[IrManualCompilationTest] Executable saved to $exeOutput")
    }
}
