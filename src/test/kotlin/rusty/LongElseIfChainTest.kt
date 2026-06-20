package rusty

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.writeText

class LongElseIfChainTest {
    @Test
    fun `asm compilation handles very long else-if chains`() {
        val branchCount = 3_000
        val source = buildString {
            appendLine("fn main() {")
            appendLine("    let x: i32 = ${branchCount - 1};")
            appendLine("    let mut y: i32 = -1;")
            repeat(branchCount) { index ->
                val keyword = if (index == 0) "if" else "else if"
                appendLine("    $keyword (x == $index) {")
                appendLine("        y = $index;")
                appendLine("    }")
            }
            appendLine("    else {")
            appendLine("        y = -2;")
            appendLine("    }")
            appendLine("    printlnInt(y);")
            appendLine("    exit(0);")
            appendLine("}")
        }
        val input = Files.createTempFile("long-else-if-chain", ".rs")
        val output = Files.createTempFile("long-else-if-chain", ".s")
        input.writeText(source)

        assertDoesNotThrow {
            main(arrayOf("-i", input.toString(), "-o", output.toString(), "--emit", "asm"))
        }
    }

    @Test
    fun `asm compilation allows unreachable code after exhaustive exit chain`() {
        val branchCount = 3_000
        val source = buildString {
            appendLine("fn main() {")
            appendLine("    let x: i32 = ${branchCount - 1};")
            repeat(branchCount) { index ->
                val keyword = if (index == 0) "if" else "else if"
                appendLine("    $keyword (x == $index) {")
                appendLine("        exit($index);")
                appendLine("    }")
            }
            appendLine("    else {")
            appendLine("        exit(-1);")
            appendLine("    }")
            appendLine("    exit(0);")
            appendLine("}")
        }
        val input = Files.createTempFile("long-exit-chain", ".rs")
        val output = Files.createTempFile("long-exit-chain", ".s")
        input.writeText(source)

        assertDoesNotThrow {
            main(arrayOf("-i", input.toString(), "-o", output.toString(), "--emit", "asm"))
        }
    }

    @Test
    fun `asm compilation handles many live variables`() {
        val variableCount = 1_200
        val source = buildString {
            append("fn sink(")
            repeat(variableCount) { index ->
                if (index > 0) append(", ")
                append("p$index: i32")
            }
            appendLine(") {")
            appendLine("    printlnInt(0);")
            appendLine("}")
            appendLine("fn main() {")
            repeat(variableCount) { index ->
                appendLine("    let v$index: i32 = getInt();")
            }
            appendLine("    sink(")
            repeat(variableCount) { index ->
                val comma = if (index + 1 < variableCount) "," else ""
                appendLine("        v$index$comma")
            }
            appendLine("    );")
            appendLine("    exit(0);")
            appendLine("}")
        }
        val input = Files.createTempFile("many-live-vars", ".rs")
        val output = Files.createTempFile("many-live-vars", ".s")
        input.writeText(source)

        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            main(arrayOf("-i", input.toString(), "-o", output.toString(), "--emit", "asm"))
        }
    }

    @Test
    fun `asm compilation handles many stack variables`() {
        val variableCount = 2_000
        val source = buildString {
            appendLine("fn main() {")
            repeat(variableCount) { index ->
                appendLine("    let mut v$index: [i32; 1] = [0];")
            }
            appendLine("    exit(0);")
            appendLine("}")
        }
        val input = Files.createTempFile("many-stack-vars", ".rs")
        val output = Files.createTempFile("many-stack-vars", ".s")
        input.writeText(source)

        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            main(arrayOf("-i", input.toString(), "-o", output.toString(), "--emit", "asm"))
        }
    }
}
