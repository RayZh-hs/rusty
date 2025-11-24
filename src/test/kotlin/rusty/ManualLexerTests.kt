package rusty

import org.junit.jupiter.api.Tag

@Tag("lexer")
@Tag("manual")
class ManualLexerTests: ExitValidationTestBase(TestMode.LEXER) {
    override val baseResourcePath: String = "lexer"
}