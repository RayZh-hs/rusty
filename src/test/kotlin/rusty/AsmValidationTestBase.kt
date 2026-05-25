package rusty

import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.opentest4j.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.fail

abstract class AsmValidationTestBase : TestBase() {

    open val compileTimeoutSeconds: Long = 30
    open val executionTimeoutSeconds: Long = 120

    override fun runTestCase(case: TestCase) {
        val outputRoot = Paths.get("build", "asm-tests").resolve(Paths.get(baseResourcePath))
        val clangBinary = IrPipeline.resolveClangBinary()
        val qemuBinary = IrPipeline.resolveQemuBinary()
        val targetDir = outputRoot.resolve(case.stage.ifBlank { "manual" })
        Files.createDirectories(targetDir)

        val rawName = case.source.fileName.toString().substringBeforeLast('.')
        val hashSuffix = Integer.toHexString(case.source.toAbsolutePath().normalize().toString().hashCode())
        val baseName = "$rawName-$hashSuffix"
        val userIr = targetDir.resolve("$baseName.user.ll")
        val userAsmSource = targetDir.resolve("$baseName.user.s")
        val exeOutput = targetDir.resolve("$baseName.out")

        val compileThrowable = try {
            assertTimeoutPreemptively(Duration.ofSeconds(compileTimeoutSeconds)) {
                IrPipeline.emitIr(case.source, userIr, emitMode = "ir")
            }
            null
        } catch (t: Throwable) {
            if (t is AssertionFailedError) throw t
            t
        }

        val compileExit = if (compileThrowable == null) 0 else 1
        if (compileExit != case.expectedCompileExit) {
            if (compileThrowable != null) {
                throw AssertionError(
                    "ASM compile exit $compileExit did not match expected ${case.expectedCompileExit} for ${case.name}",
                    compileThrowable
                )
            } else {
                fail("ASM compile exit $compileExit did not match expected ${case.expectedCompileExit} for ${case.name}")
            }
        }
        if (compileExit != 0) return

        if (!IrPipeline.commandAvailable(clangBinary)) {
            fail("clang not available (looked for '$clangBinary'); ASM tests need clang for runtime/prelude assembly.")
        }
        if (!IrPipeline.commandAvailable(qemuBinary)) {
            fail(
                "QEMU rv64 backend not available (looked for '$qemuBinary'); set -D${IrPipeline.PROP_QEMU_PATH}=... to override."
            )
        }

        val stdinContent = case.input?.takeIf { Files.exists(it) }?.readText() ?: ""
        val runResult = assertTimeoutPreemptively<IrPipeline.ProcessResult>(Duration.ofSeconds(executionTimeoutSeconds)) {
            val preludeDir = Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude")
            val preludeLl = preludeDir.resolve("prelude.ll")
            val preludeCLl = IrPipeline.ensurePreludeCIr(IrPipeline.PreludeCTarget.RISCV, clangBinary)
            listOf(preludeLl, preludeCLl).forEach {
                require(Files.exists(it)) { "Prelude missing: $it" }
            }

            val preludeAsmSource = targetDir.resolve("$baseName.prelude.s")
            val builtinAsmSource = targetDir.resolve("$baseName.builtin.s")

            fun ensureOk(label: String, result: IrPipeline.ProcessResult) {
                if (result.exitCode != 0) {
                    fail(
                        "$label failed for ${case.name} (exit ${result.exitCode})\n" +
                            "Command: ${result.args.joinToString(" ")}\n" +
                            "Output:\n${result.output}"
                    )
                }
            }

            ensureOk(
                "clang (riscv asm from user IR)",
                IrPipeline.compileToRiscvAssembly(userIr, userAsmSource, clangBinary)
            )
            ensureOk(
                "clang (riscv asm from prelude.ll)",
                IrPipeline.compileToRiscvAssembly(preludeLl, preludeAsmSource, clangBinary)
            )
            ensureOk(
                "clang (riscv asm from prelude.c.ll)",
                IrPipeline.compileToRiscvAssembly(
                    preludeCLl,
                    builtinAsmSource,
                    clangBinary,
                    optimize = true,
                    extraArgs = listOf("-fno-builtin"),
                )
            )

            val linkResult = IrPipeline.linkRiscvExecutable(
                inputFiles = listOf(userAsmSource, preludeAsmSource, builtinAsmSource),
                exeOutput = exeOutput,
                clangBinary = clangBinary,
            )
            if (linkResult.exitCode != 0) {
                fail(
                    "clang (link rv64 qemu executable) failed for ${case.name} (exit ${linkResult.exitCode})\n" +
                        "Command: ${linkResult.args.joinToString(" ")}\n" +
                        "Output:\n${linkResult.output}"
                )
            }

            IrPipeline.runExecutable(exeOutput, stdinContent)
        }

        val expectedRunExit = case.expectedRunExit ?: 0
        if (runResult.exitCode != expectedRunExit) {
            fail(
                "ASM executable exit ${runResult.exitCode} did not match expected $expectedRunExit for ${case.name}.\n" +
                    "Args: ${runResult.args.joinToString(" ")}\nOutput:\n${runResult.output}"
            )
        }

        case.expectedOutput?.takeIf { Files.exists(it) }?.let { outPath ->
            fun normalize(text: String): String =
                text.replace("\r\n", "\n").trimEnd('\n', '\r')
            val expected = normalize(outPath.readText())
            val actual = normalize(runResult.output)
            assertEquals(
                expected,
                actual,
                "ASM output mismatch for ${case.name}. Expected from $outPath"
            )
        }
    }
}
