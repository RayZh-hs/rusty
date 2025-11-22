package rusty

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.streams.toList
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Shared IR test harness for both manual and official resources.
 *
 * Inspired by [BaseOfficialSemanticTests]: collects cases from a resource root,
 * emits LLVM IR, optionally links with clang, and (when output files exist)
 * asserts program stdout matches the expected content.
 */
abstract class BaseIrCompilationTests {

    abstract val baseResourcePath: String

    private data class IrCase(
        val name: String,
        val stage: String,
        val source: Path,
        val input: Path?,
        val expectedOutput: Path?,
        val expectedCompileExit: Int,
        val expectedRunExit: Int?
    )

    private val noClang: Boolean =
        System.getProperty(IrPipeline.PROP_NO_CLANG)?.equals("true", ignoreCase = true) == true

    @TestFactory
    fun irCompilationCases(): Collection<DynamicTest> {
        val cases = collectCases()
        val outputRoot = Paths.get("build", "ir-tests").resolve(Paths.get(baseResourcePath))
        val clangBinary = IrPipeline.resolveClangBinary()

        return cases.map { c ->
            val displayName = buildString {
                append(baseResourcePath)
                append("/")
                if (c.stage.isNotBlank()) {
                    append(c.stage)
                    append("/")
                }
                append(c.name)
            }
            DynamicTest.dynamicTest(displayName) {
                val targetDir = outputRoot.resolve(c.stage.ifBlank { "manual" })
                val artifacts = IrPipeline.artifactPathsFor(c.source, targetDir)

                val compileThrowable = try {
                    IrPipeline.emitIr(c.source, artifacts.irOutput)
                    null
                } catch (t: Throwable) {
                    t
                }

                val compileExit = if (compileThrowable == null) 0 else 1
                if (compileExit != c.expectedCompileExit) {
                    val hint = if (noClang) " (note: -D${IrPipeline.PROP_NO_CLANG}=true skips clang only)" else ""
                    if (compileThrowable != null) {
                        throw AssertionError(
                            "Compile exit $compileExit did not match expected ${c.expectedCompileExit} for ${c.name}$hint",
                            compileThrowable
                        )
                    } else {
                        fail("Compile exit $compileExit did not match expected ${c.expectedCompileExit} for ${c.name}$hint")
                    }
                }
                if (compileExit != 0) {
                    // Expected failure; nothing else to do.
                    return@dynamicTest
                }

                if (noClang) {
                    println("[IR tests] Skipping clang/link/run for ${c.name} due to -D${IrPipeline.PROP_NO_CLANG}=true")
                    return@dynamicTest
                }

                if (!IrPipeline.commandAvailable(clangBinary)) {
                    fail(
                        "clang not available (looked for '$clangBinary'); set -D${IrPipeline.PROP_NO_CLANG}=true to skip linking/execution."
                    )
                }

                val linkResult = IrPipeline.linkWithPrelude(artifacts.irOutput, artifacts.exeOutput, clangBinary)
                if (linkResult.exitCode != 0) {
                    fail(
                        buildString {
                            append("clang failed for ${c.name} (exit ${linkResult.exitCode})\n")
                            append("Command: ${linkResult.args.joinToString(" ")}\n")
                            append("Output:\n${linkResult.output}")
                        }
                    )
                }

                val stdinContent = c.input?.takeIf { Files.exists(it) }?.readText() ?: ""
                val runResult = IrPipeline.runExecutable(artifacts.exeOutput, stdinContent)
                val expectedRunExit = c.expectedRunExit ?: 0
                if (runResult.exitCode != expectedRunExit) {
                    fail(
                        "Executable exit ${runResult.exitCode} did not match expected $expectedRunExit for ${c.name}.\n" +
                            "Args: ${runResult.args.joinToString(" ")}\nOutput:\n${runResult.output}"
                    )
                }

                c.expectedOutput?.takeIf { Files.exists(it) }?.let { outPath ->
                    val expected = outPath.readText().trimEnd('\n', '\r')
                    val actual = runResult.output.trimEnd('\n', '\r')
                    assertEquals(
                        expected,
                        actual,
                        "Output mismatch for ${c.name}. Expected from $outPath"
                    )
                }
            }
        }
    }

    private fun collectCases(): List<IrCase> {
        val base = Paths.get("src", "test", "resources").resolve(baseResourcePath)
        require(Files.exists(base)) { "IR resource root missing: $base" }

        val irStageDirs = Files.list(base)
            .use { stream -> stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("IR-") }.toList() }
            .sortedBy { it.fileName.toString() }
        return when {
            irStageDirs.isNotEmpty() -> irStageDirs.flatMap { collectOfficialCases(it) }
            Files.exists(base.resolve("global.json")) && Files.isDirectory(base.resolve("src")) -> collectOfficialCases(base)
            else -> collectManualCases(base)
        }.sortedBy { "${it.stage}/${it.name}" }
    }

    private fun collectManualCases(base: Path): List<IrCase> {
        return Files.walk(base)
            .use { stream -> stream.filter { Files.isRegularFile(it) && it.extension == "rs" }.toList() }
            .map { source ->
                val stem = source.nameWithoutExtension
                val input = source.parent.resolve("$stem.in").takeIf { Files.exists(it) }
                val output = source.parent.resolve("$stem.out").takeIf { Files.exists(it) }
                val relativeStage = source.parent?.let { base.relativize(it).toString() }.orEmpty()
                val stage = if (relativeStage.isBlank()) "manual" else relativeStage
                IrCase(
                    name = source.fileName.toString(),
                    stage = stage,
                    source = source,
                    input = input,
                    expectedOutput = output,
                    expectedCompileExit = 0,
                    expectedRunExit = 0
                )
            }
    }

    private fun collectOfficialCases(stageDir: Path): List<IrCase> {
        val global = stageDir.resolve("global.json")
        if (!Files.exists(global)) return emptyList()

        val jsonObjects = splitTopLevelObjects(global.readText())
        return jsonObjects.mapNotNull { obj ->
            val name = obj.extractString("name") ?: return@mapNotNull null
            val active = obj.extractBoolean("active") ?: true
            if (!active) return@mapNotNull null

            val sourceRel = obj.extractArrayPath("source") ?: "src/$name/$name.rx"
            val inputRel = obj.extractArrayPath("input")
            val outputRel = obj.extractArrayPath("output")
            val compileExit = obj.extractInt("compileexitcode") ?: 0
            val runExit = obj.extractInt("exitcode") ?: 0

            val source = stageDir.resolve(sourceRel)
            if (!Files.exists(source)) return@mapNotNull null

            val input = inputRel?.let { stageDir.resolve(it) }?.takeIf { Files.exists(it) }
            val expectedOutput = outputRel?.let { stageDir.resolve(it) }?.takeIf { Files.exists(it) }

            IrCase(
                name = name,
                stage = stageDir.fileName.toString(),
                source = source,
                input = input,
                expectedOutput = expectedOutput,
                expectedCompileExit = compileExit,
                expectedRunExit = if (compileExit == 0) runExit else null
            )
        }
    }

    private fun String.extractString(key: String): String? {
        val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(this)?.groupValues?.getOrNull(1)
    }

    private fun String.extractInt(key: String): Int? {
        val regex = """"$key"\s*:\s*(-?\d+)""".toRegex()
        return regex.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun String.extractBoolean(key: String): Boolean? {
        val regex = """"$key"\s*:\s*(true|false)""".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(this)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    private fun String.extractArrayPath(key: String): String? {
        val regex = """"$key"\s*:\s*\[\s*"([^"]+)"""".toRegex()
        return regex.find(this)?.groupValues?.getOrNull(1)
    }

    private fun splitTopLevelObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaping = false
        json.forEachIndexed { index, ch ->
            if (escaping) {
                escaping = false
                return@forEachIndexed
            }
            if (ch == '\\') {
                escaping = true
                return@forEachIndexed
            }
            if (ch == '"' && depth >= 0) {
                inString = !inString
                return@forEachIndexed
            }
            if (inString) return@forEachIndexed
            when (ch) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects += json.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }
}
