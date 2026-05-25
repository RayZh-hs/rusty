package rusty

import rusty.core.RiscvTargetConfig
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
    const val PROP_QEMU_PATH = "qemuPath"
    const val PROP_QEMU_ARGS = "qemuArgs"
    const val PROP_QEMU_SYSROOT = "qemuSysroot"
    const val PROP_QEMU_CLANG_TARGET = "qemuClangTarget"

    data class ArtifactPaths(val irOutput: Path, val exeOutput: Path)
    data class ProcessResult(val exitCode: Int, val output: String, val args: List<String>)

    enum class PreludeCTarget { X86, RISCV }

    fun resolveClangBinary(): String = System.getProperty(PROP_CLANG) ?: "clang"
    fun resolveClangArgs(): List<String> = splitArgs(System.getProperty(PROP_CLANG_ARGS))
    fun resolveQemuBinary(): String = System.getProperty(PROP_QEMU_PATH) ?: "qemu-riscv64"
    fun resolveQemuArgs(): List<String> = splitArgs(System.getProperty(PROP_QEMU_ARGS))
    fun resolveQemuSysroot(): String? = System.getProperty(PROP_QEMU_SYSROOT)?.takeIf { it.isNotBlank() }
    fun resolveQemuClangTarget(): String = System.getProperty(PROP_QEMU_CLANG_TARGET) ?: "riscv64-linux-gnu"

    private fun preludeCSource(): Path =
        Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude", "prelude.c")

    private fun preludeCOutputDir(): Path = Paths.get("build", "ir-prelude")

    private fun preludeCOutputName(target: PreludeCTarget): String =
        when (target) {
            PreludeCTarget.X86 -> "prelude.c.x86.ll"
            PreludeCTarget.RISCV -> {
                val targetTag = resolveQemuClangTarget().replace(Regex("[^A-Za-z0-9._-]"), "_")
                "prelude.c.$targetTag.${RiscvTargetConfig.LINUX_ARCH}.${RiscvTargetConfig.LINUX_ABI}.ll"
            }
        }

    private fun splitArgs(raw: String?): List<String> =
        raw
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.split(Regex("\\s+"))
            ?: emptyList()

    fun ensurePreludeCIr(
        target: PreludeCTarget,
        clangBinary: String = resolveClangBinary(),
    ): Path {
        val source = preludeCSource()
        require(Files.exists(source)) { "Prelude C source missing: $source" }

        val output = preludeCOutputDir().resolve(preludeCOutputName(target))

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
                PreludeCTarget.RISCV -> addAll(
                    listOf(
                        "--target=${resolveQemuClangTarget()}",
                        "-march=${RiscvTargetConfig.LINUX_ARCH}",
                        "-mabi=${RiscvTargetConfig.LINUX_ABI}",
                    )
                )
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

    fun emitIr(input: Path, irOutput: Path, emitMode: String = "ir") {
        irOutput.parent?.let { Files.createDirectories(it) }
        main(arrayOf("-i", input.toString(), "-o", irOutput.toString(), "--emit", emitMode))
    }

    fun linkWithPrelude(irOutput: Path, exeOutput: Path, clangBinary: String = resolveClangBinary()): ProcessResult {
        val preludeDir = Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude")
        val preludeLl = preludeDir.resolve("prelude.ll")
        val preludeCLl = ensurePreludeCIr(PreludeCTarget.RISCV, clangBinary)
        listOf(preludeLl, preludeCLl).forEach {
            require(Files.exists(it)) { "Prelude IR missing: $it" }
        }

        return linkRiscvExecutable(
            inputFiles = listOf(irOutput, preludeLl, preludeCLl),
            exeOutput = exeOutput,
            clangBinary = clangBinary,
        )
    }

    fun linkRiscvExecutable(
        inputFiles: List<Path>,
        exeOutput: Path,
        clangBinary: String = resolveClangBinary(),
        extraArgs: List<String> = emptyList(),
    ): ProcessResult {
        exeOutput.parent?.let { Files.createDirectories(it) }
        val clangArgs = buildList {
            add(clangBinary)
            add("--target=${resolveQemuClangTarget()}")
            add("-march=${RiscvTargetConfig.LINUX_ARCH}")
            add("-mabi=${RiscvTargetConfig.LINUX_ABI}")
            addAll(resolveClangArgs())
            addAll(extraArgs)
            addAll(inputFiles.map(Path::toString))
            add("-o")
            add(exeOutput.toString())
        }
        return runProcess(clangArgs)
    }

    fun runExecutable(exeFile: Path, stdinContent: String): ProcessResult {
        val args = buildList {
            add(resolveQemuBinary())
            resolveQemuSysroot()?.let {
                add("-L")
                add(it)
            }
            addAll(resolveQemuArgs())
            add(exeFile.toString())
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
            add("--target=${resolveQemuClangTarget()}")
            add("-march=${RiscvTargetConfig.LINUX_ARCH}")
            add("-mabi=${RiscvTargetConfig.LINUX_ABI}")
            if (optimize) add("-O2")
            addAll(extraArgs)
            add(input.toString())
            add("-o")
            add(asmOutput.toString())
        }
        return runProcess(clangArgs)
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
