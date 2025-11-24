package rusty

import org.junit.jupiter.api.assertDoesNotThrow
import rusty.core.CompileError
import rusty.lexer.Lexer
import rusty.parser.Parser
import rusty.preprocessor.Preprocessor
import rusty.semantic.SemanticConstructor
import java.io.File
import kotlin.test.assertFails

abstract class ExitValidationTestBase(val mode: TestMode) : TestBase() {

    private fun shouldRun(stage: TestMode): Boolean {
        return mode.ordinal >= stage.ordinal
    }

    override fun runTestCase(case: TestCase) {
        val content = File(case.source.toString()).readText()
        CompileError.registerSource(content.split('\n'))

        val compilationLogic = compiler@{
            val pre = Preprocessor.run(content)
            if (!shouldRun(TestMode.LEXER)) return@compiler pre
            val lex = Lexer.run(pre)
            if (!shouldRun(TestMode.PARSER)) return@compiler lex
            val ast = Parser.run(lex)
            if (!shouldRun(TestMode.SEMANTIC)) return@compiler ast
            val sem = SemanticConstructor.run(ast, dumpToScreen = false)
            return@compiler sem
        }

        if (case.expectedCompileExit == 0) {
            assertDoesNotThrow(
                "Compilation was expected to succeed for ${case.name} but failed:",
                compilationLogic
            )
        } else {
            assertFails("Compilation was expected to fail for ${case.name} but it succeeded.") {
                compilationLogic()
            }
        }
    }
}
