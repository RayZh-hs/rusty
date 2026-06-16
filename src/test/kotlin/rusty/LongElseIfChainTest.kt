package rusty

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
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
}
