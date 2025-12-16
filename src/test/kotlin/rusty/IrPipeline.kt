package rusty

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

/**
 * Shared helpers for IR compilation/linking tests.
 *
 * Provides a consistent `.rx -> .ll -> .out` pipeline so both manual and
 * official test suites generate comparable artifacts.
 */
object IrPipeline {
    const val PROP_CLANG = "clangPath"
    const val PROP_NO_CLANG = "noClang"
    const val PROP_CLANG_ARGS = "clangArgs"
    const val PROP_REIMU = "irReimu"
    const val PROP_REIMU_PATH = "reimuPath"
    const val PROP_REIMU_MEMORY = "reimuMemory"
    const val PROP_REIMU_STACK = "reimuStack"

    data class ArtifactPaths(val irOutput: Path, val exeOutput: Path)
    data class ProcessResult(val exitCode: Int, val output: String, val args: List<String>)

    enum class PreludeCTarget { X86, RISCV }

    fun resolveClangBinary(): String = System.getProperty(PROP_CLANG) ?: "clang"
    fun resolveClangArgs(): String = System.getProperty(PROP_CLANG_ARGS) ?: ""
    private fun resolveReimuBinary(): Path =
        System.getProperty(PROP_REIMU_PATH)?.let { Paths.get(it) }
            ?: Paths.get("src", "test", "resources", "bin", "reimu")

    private fun preludeCSource(): Path =
        Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude", "prelude.c")

    private fun preludeCOutputDir(): Path = Paths.get("build", "ir-prelude")

    fun ensurePreludeCIr(
        target: PreludeCTarget,
        clangBinary: String = resolveClangBinary(),
    ): Path {
        val source = preludeCSource()
        require(Files.exists(source)) { "Prelude C source missing: $source" }

        val outputName = when (target) {
            PreludeCTarget.X86 -> "prelude.c.x86.ll"
            PreludeCTarget.RISCV -> "prelude.c.riscv.ll"
        }
        val output = preludeCOutputDir().resolve(outputName)

        val sourceMtime = Files.getLastModifiedTime(source)
        val outputMtime = if (Files.exists(output)) Files.getLastModifiedTime(output) else FileTime.fromMillis(0)
        if (outputMtime >= sourceMtime) return output

        Files.createDirectories(output.parent)
        val args = buildList {
            add(clangBinary)
            add("-S")
            add("-emit-llvm")
            add("-O0")
            when (target) {
                PreludeCTarget.X86 -> Unit
                PreludeCTarget.RISCV -> addAll(listOf("--target=riscv32-unknown-elf", "-march=rv32im", "-mabi=ilp32"))
            }
            add(source.toString())
            add("-o")
            add(output.toString())
        }
        val result = runProcess(args)
        require(result.exitCode == 0) {
            "Failed to compile $source to $output (exit ${result.exitCode})\n" +
                "Command: ${result.args.joinToString(" ")}\n" +
                "Output:\n${result.output}"
        }
        return output
    }

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
        val preludeCLl = ensurePreludeCIr(PreludeCTarget.X86, clangBinary)
        listOf(preludeLl, preludeCLl).forEach {
            require(Files.exists(it)) { "Prelude IR missing: $it" }
        }

        val clangArgs = listOf(
            clangBinary,
            irOutput.toString(),
            preludeLl.toString(),
            preludeCLl.toString(),
            resolveClangArgs(),
            "-o",
            exeOutput.toString(),
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

    fun compileToRiscvAssembly(
        input: Path,
        asmOutput: Path,
        clangBinary: String = resolveClangBinary(),
        optimize: Boolean = false,
        extraArgs: List<String> = emptyList(),
    ): ProcessResult {
        asmOutput.parent?.let { Files.createDirectories(it) }
        val clangArgs = buildList {
            add(clangBinary)
            add("-S")
            add("--target=riscv32-unknown-elf")
            add("-march=rv32im")
            add("-mabi=ilp32")
            if (optimize) add("-O2")
            addAll(extraArgs)
            add(input.toString())
            add("-o")
            add(asmOutput.toString())
        }
        return runProcess(clangArgs)
    }

    fun stripPltSuffix(inputAsm: Path, outputAsm: Path = inputAsm): Path {
        val content = Files.readString(inputAsm)
        val stripped = content.replace("@plt", "")
        if (outputAsm == inputAsm) {
            Files.writeString(inputAsm, stripped)
        } else {
            outputAsm.parent?.let { Files.createDirectories(it) }
            Files.writeString(outputAsm, stripped)
        }
        return outputAsm
    }

    fun runReimu(
        asmFiles: List<Path>,
        stdinContent: String,
        memory: String = System.getProperty(PROP_REIMU_MEMORY) ?: "64M",
        stack: String = System.getProperty(PROP_REIMU_STACK) ?: "8M",
    ): ProcessResult {
        val reimu = resolveReimuBinary()
        require(Files.exists(reimu)) { "reimu binary not found at $reimu (set -D$PROP_REIMU_PATH=... to override)" }

        val args = buildList {
            add(reimu.toString())
            add("--silent")
            add("-m=$memory")
            add("-s=$stack")
            add("-i=<stdin>")
            add("-o=<stdout>")
            add("-f=${asmFiles.joinToString(",") { it.toAbsolutePath().normalize().toString() }}")
        }
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
