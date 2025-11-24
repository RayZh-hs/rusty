package rusty

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

enum class TestMode {
    PREPROCESSOR,
    LEXER,
    PARSER,
    SEMANTIC,
    IR,
}

data class TestCase(
    val name: String,
    val stage: String,
    val source: Path,
    val input: Path?,
    val expectedOutput: Path?,
    val expectedCompileExit: Int,
    val expectedRunExit: Int?,
    val skipReason: String? = null
)

abstract class TestBase {

    abstract val baseResourcePath: String
    open val subdirectoryPrefix: String? = null

    abstract fun runTestCase(case: TestCase)

    @TestFactory
    fun tests(): Collection<DynamicTest> {
        val filter = System.getProperty("name") // Generic filter
        val cases = collectCases().filter {
            filter.isNullOrBlank() || it.name.contains(filter)
        }
        
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
                if (c.skipReason != null) {
                    Assumptions.assumeTrue(false, c.skipReason)
                }
                runTestCase(c)
            }
        }
    }

    private fun collectCases(): List<TestCase> {
        val base = Paths.get("src", "test", "resources", baseResourcePath)
        if (!Files.exists(base)) {
            // It might be that the base path is relative to project root, let's try to find it.
            // But usually src/test/resources is correct.
            // If it doesn't exist, maybe return empty or throw.
            // For now, let's assume it exists or return empty.
            return emptyList()
        }

        if (subdirectoryPrefix != null) {
            val dirs = Files.list(base).use { stream ->
                stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith(subdirectoryPrefix!!) }.toList()
            }.sortedBy { it.fileName.toString() }
            
            if (dirs.isNotEmpty()) {
                return dirs.flatMap { collectCasesFromDir(it) }
            }
        }

        return collectCasesFromDir(base)
    }

    private fun collectCasesFromDir(dir: Path): List<TestCase> {
        if (Files.exists(dir.resolve("global.json"))) {
            return collectOfficialCases(dir)
        }
        return collectManualCases(dir)
    }

    private fun collectManualCases(base: Path): List<TestCase> {
        return Files.walk(base)
            .use {
                stream -> stream.filter {
                    Files.isRegularFile(it) && (it.extension == "rs" || it.extension == "rx")
                }.toList()
            }
            .map { source ->
                val directives = parseManualDirectives(source)
                val stem = source.nameWithoutExtension
                val input = source.parent.resolve("$stem.in").takeIf { Files.exists(it) }
                val output = source.parent.resolve("$stem.out").takeIf { Files.exists(it) }
                val relativeStage = source.parent?.let { base.relativize(it).toString() }.orEmpty()
                val stage = if (relativeStage.isBlank()) "manual" else relativeStage
                val compileExit = if (directives.expectFailure) 1 else 0
                TestCase(
                    name = source.fileName.toString(),
                    stage = stage,
                    source = source,
                    input = input,
                    expectedOutput = output,
                    expectedCompileExit = compileExit,
                    expectedRunExit = if (compileExit == 0) 0 else null,
                    skipReason = directives.skipReason
                )
            }
            .sortedBy { "${it.stage}/${it.name}" }
    }

    private fun collectOfficialCases(stageDir: Path): List<TestCase> {
        val global = stageDir.resolve("global.json")
        if (!Files.exists(global)) return emptyList()

        val jsonObjects = splitTopLevelObjects(global.readText())
        return jsonObjects.mapNotNull { obj ->
            val name = obj.extractString("name") ?: return@mapNotNull null
            val active = obj.extractBoolean("active") ?: true
            if (!active) return@mapNotNull null

            val sourceRel = obj.extractArrayPath("source") ?: "src/$name/$name.rx" // Default to .rx for official? Or .rs?
            
            val inputRel = obj.extractArrayPath("input")
            val outputRel = obj.extractArrayPath("output")
            val compileExit = obj.extractInt("compileexitcode") ?: 0
            val runExit = obj.extractInt("exitcode") ?: 0

            val source = stageDir.resolve(sourceRel)
            // If source doesn't exist, maybe try .rs if .rx was default?
            // But let's stick to what was there.
            if (!Files.exists(source)) return@mapNotNull null

            val input = inputRel?.let { stageDir.resolve(it) }?.takeIf { Files.exists(it) }
            val expectedOutput = outputRel?.let { stageDir.resolve(it) }?.takeIf { Files.exists(it) }

            TestCase(
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

    // JSON helpers
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
        // Matches ["path"]
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

    private data class ManualTestDirectives(
        val skipReason: String? = null,
        val expectFailure: Boolean = false
    )

    private fun parseManualDirectives(source: Path): ManualTestDirectives {
        Files.newBufferedReader(source).use { reader ->
            while (true) {
                val rawLine = reader.readLine() ?: break
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                if (!trimmed.startsWith("//")) break
                val commentBody = trimmed.removePrefix("//").trim()
                if (!commentBody.startsWith("!")) break
                val directive = commentBody.removePrefix("!").trim()
                val normalized = directive.lowercase()
                return when {
                    normalized.startsWith("skip") -> ManualTestDirectives(
                        skipReason = "Skipped by directive in ${source.fileName}: $directive",
                        expectFailure = false
                    )
                    normalized.startsWith("expected to fail") -> ManualTestDirectives(expectFailure = true)
                    else -> ManualTestDirectives()
                }
            }
        }
        return ManualTestDirectives()
    }
}
