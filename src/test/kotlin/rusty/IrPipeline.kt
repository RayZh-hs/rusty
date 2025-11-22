package rusty

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shared helpers for IR compilation/linking tests.
 *
 * Provides a consistent `.rx -> .ll -> .out` pipeline so both manual and
 * official test suites generate comparable artifacts.
 */
object IrPipeline {
    const val PROP_CLANG = "customIrClang"
    const val PROP_NO_CLANG = "irNoClang"

    data class ArtifactPaths(val irOutput: Path, val exeOutput: Path)
    data class ProcessResult(val exitCode: Int, val output: String, val args: List<String>)

    fun resolveClangBinary(): String = System.getProperty(PROP_CLANG) ?: "clang"

    fun commandAvailable(binary: String): Boolean {
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

    fun artifactPathsFor(input: Path, outputDir: Path): ArtifactPaths {
        Files.createDirectories(outputDir)
        val rawName = input.fileName.toString().substringBeforeLast('.')
        val hashSuffix = Integer.toHexString(input.toAbsolutePath().normalize().toString().hashCode())
        val caseName = "$rawName-$hashSuffix"
        return ArtifactPaths(
            outputDir.resolve("$caseName.ll"),
            outputDir.resolve("$caseName.out")
        )
    }

    fun emitIr(input: Path, irOutput: Path) {
        irOutput.parent?.let { Files.createDirectories(it) }
        main(arrayOf("-i", input.toString(), "-o", irOutput.toString(), "-m", "ir"))
    }

    fun linkWithPrelude(irOutput: Path, exeOutput: Path, clangBinary: String = resolveClangBinary()): ProcessResult {
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
        return runProcess(clangArgs)
    }

    fun runExecutable(exeFile: Path, stdinContent: String): ProcessResult {
        val args = listOf(exeFile.toString())
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()

        process.outputStream.bufferedWriter().use { writer ->
            writer.write(stdinContent)
            writer.flush()
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        return ProcessResult(exit, output, args)
    }

    fun runProcess(args: List<String>): ProcessResult {
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        return ProcessResult(exit, output, args)
    }
}
