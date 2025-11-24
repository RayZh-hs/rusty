package rusty

import org.junit.jupiter.api.Tag

@Tag("lexer")
@Tag("preprocessor")
class ManualPreprocessorTests: ExitValidationTestBase(TestMode.PREPROCESSOR) {
    override val baseResourcePath: String = "preprocessor"
}