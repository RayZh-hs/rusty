package rusty

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import rusty.core.CompileMode
import rusty.core.CompileModeMap
import rusty.lexer.Lexer
import rusty.lexer.dumpScreen
import rusty.parser.Parser
import rusty.parser.dumpScreen
import rusty.preprocessor.Preprocessor
import rusty.preprocessor.dumpScreen
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Local manual tester.
 *
 * This test is intentionally skipped during normal CI runs unless you supply the system property 'localTestFile'.
 * It lets you quickly dump the preprocessing / lexing / parsing result of a single `.rs` test resource file.
 *
 * Usage examples:
 *   # Dump parser AST (default) for a parser test case
 *   ./gradlew test --tests rusty.LocalManualTest -DlocalTestFile=parser/naive.rs
 *
 *   # Dump only lexer tokens
 *   ./gradlew test --tests rusty.LocalManualTest -DlocalTestFile=lexer/naive_print.rs -DlocalTestMode=lex
 *
 *   # Dump just preprocessor output
 *   ./gradlew test --tests rusty.LocalManualTest -DlocalTestFile=preprocessor/raw_strings.rs -DlocalTestMode=pp
 *
 * Supported localTestMode values (aliases match those in Main.kt):
 *   pp | preprocess  -> Preprocessor output
 *   lex              -> Tokens
 *   parse | parser   -> AST (default)
 */
class LocalManualTest {

    @Test
    fun runLocalSingle() {
        val relFile = System.getProperty(PROP_FILE) ?: run {
            // Skip when no target provided (normal CI run)
            assumeTrue(false) { "Skipping LocalManualTest: no -D$PROP_FILE provided" }
            return
        }
        val modeStr = System.getProperty(PROP_MODE) ?: "parse"

        val mode = CompileModeMap[modeStr] ?: when (modeStr.lowercase()) {
            // Accept some shorthand / fallback
            "pp" -> CompileMode.PREPROCESS
            "preprocess" -> CompileMode.PREPROCESS
            "lex" -> CompileMode.LEX
            "parse", "parser" -> CompileMode.PARSE
            else -> error("Unknown localTestMode '$modeStr'")
        }

        val base: Path = Path.of("src", "test", "resources")
        val target: Path = base.resolve(relFile)
        require(Files.exists(target)) { "Test resource file not found: $target" }

        println("[LocalManualTest] Running mode=$mode on $relFile")
        val raw = target.readText()

        try {
            // 1. Preprocess
            val preprocessed = Preprocessor.run(raw)
            if (mode == CompileMode.PREPROCESS) {
                Preprocessor.dumpScreen(preprocessed)
                return
            }

            // 2. Lex
            val tokens = Lexer.run(preprocessed)
            if (mode == CompileMode.LEX) {
                Lexer.dumpScreen(tokens)
                return
            }

            // 3. Parse
            val ast = Parser.run(tokens)
            Parser.dumpScreen(ast)
        } catch (t: Throwable) {
            println("[LocalManualTest] ERROR: ${t::class.simpleName}: ${t.message}")
            t.printStackTrace(System.out)
            throw t
        }
    }

    companion object {
        private const val PROP_FILE = "localTestFile"
        private const val PROP_MODE = "localTestMode"
    }
}
