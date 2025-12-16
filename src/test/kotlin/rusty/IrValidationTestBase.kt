package rusty

import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.opentest4j.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.fail

abstract class IrValidationTestBase : TestBase() {

    open val compileTimeoutSeconds: Long = 5
    open val executionTimeoutSeconds: Long = 5

    private val noClang: Boolean =
        System.getProperty(IrPipeline.PROP_NO_CLANG)?.equals("true", ignoreCase = true) == true
    private val useReimu: Boolean =
        System.getProperty(IrPipeline.PROP_REIMU)?.equals("true", ignoreCase = true) == true

    override fun runTestCase(case: TestCase) {
        val outputRoot = Paths.get("build", "ir-tests").resolve(Paths.get(baseResourcePath))
        val clangBinary = IrPipeline.resolveClangBinary()
        
        val targetDir = outputRoot.resolve(case.stage.ifBlank { "manual" })
        val artifacts = IrPipeline.artifactPathsFor(case.source, targetDir)

        val compileThrowable = try {
            assertTimeoutPreemptively(Duration.ofSeconds(compileTimeoutSeconds)) {
                IrPipeline.emitIr(case.source, artifacts.irOutput)
            }
            null
        } catch (t: Throwable) {
            if (t is AssertionFailedError) throw t
            t
        }

        val compileExit = if (compileThrowable == null) 0 else 1
        if (compileExit != case.expectedCompileExit) {
            val hint = if (noClang) " (note: -D${IrPipeline.PROP_NO_CLANG}=true skips clang only)" else ""
            if (compileThrowable != null) {
                throw AssertionError(
                    "Compile exit $compileExit did not match expected ${case.expectedCompileExit} for ${case.name}$hint",
                    compileThrowable
                )
            } else {
                fail("Compile exit $compileExit did not match expected ${case.expectedCompileExit} for ${case.name}$hint")
            }
        }
        if (compileExit != 0) {
            // Expected failure; nothing else to do.
            return
        }

        if (noClang) {
            println("[IR tests] Skipping clang/link/run for ${case.name} due to -D${IrPipeline.PROP_NO_CLANG}=true")
            return
        }

        if (!IrPipeline.commandAvailable(clangBinary)) {
            fail(
                "clang not available (looked for '$clangBinary'); set -D${IrPipeline.PROP_NO_CLANG}=true to skip linking/execution."
            )
        }

        val stdinContent = case.input?.takeIf { Files.exists(it) }?.readText() ?: ""
        val backendTimeoutSeconds = if (useReimu) maxOf(executionTimeoutSeconds, 60) else executionTimeoutSeconds
        val runResult = assertTimeoutPreemptively<IrPipeline.ProcessResult>(Duration.ofSeconds(backendTimeoutSeconds)) {
            if (useReimu) {
                val preludeDir = Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude")
                val preludeLl = preludeDir.resolve("prelude.ll")
                val preludeCLl = IrPipeline.ensurePreludeCIr(IrPipeline.PreludeCTarget.RISCV, clangBinary)
                listOf(preludeLl, preludeCLl).forEach {
                    require(Files.exists(it)) { "Prelude missing: $it" }
                }

                val baseName = artifacts.irOutput.fileName.toString().substringBeforeLast('.')
                val userAsmSource = targetDir.resolve("$baseName.user.s.source")
                val preludeAsmSource = targetDir.resolve("$baseName.prelude.s.source")
                val builtinAsmSource = targetDir.resolve("$baseName.builtin.s.source")
                val userAsm = targetDir.resolve("$baseName.user.s")
                val preludeAsm = targetDir.resolve("$baseName.prelude.s")
                val builtinAsm = targetDir.resolve("$baseName.builtin.s")

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
                    IrPipeline.compileToRiscvAssembly(artifacts.irOutput, userAsmSource, clangBinary)
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

                IrPipeline.stripPltSuffix(userAsmSource, userAsm)
                IrPipeline.stripPltSuffix(preludeAsmSource, preludeAsm)
                IrPipeline.stripPltSuffix(builtinAsmSource, builtinAsm)

                IrPipeline.runReimu(listOf(userAsm, preludeAsm, builtinAsm), stdinContent)
            } else {
                val linkResult = IrPipeline.linkWithPrelude(artifacts.irOutput, artifacts.exeOutput, clangBinary)
                if (linkResult.exitCode != 0) {
                    fail(
                        buildString {
                            append("clang failed for ${case.name} (exit ${linkResult.exitCode})\n")
                            append("Command: ${linkResult.args.joinToString(" ")}\n")
                            append("Output:\n${linkResult.output}")
                        }
                    )
                }
                IrPipeline.runExecutable(artifacts.exeOutput, stdinContent)
            }
        }
        val expectedRunExit = case.expectedRunExit ?: 0
        if (runResult.exitCode != expectedRunExit) {
            fail(
                "Executable exit ${runResult.exitCode} did not match expected $expectedRunExit for ${case.name}.\n" +
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
                "Output mismatch for ${case.name}. Expected from $outPath"
            )
        }
    }
}
