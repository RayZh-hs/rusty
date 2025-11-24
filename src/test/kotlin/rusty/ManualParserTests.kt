package rusty

import org.junit.jupiter.api.Tag

@Tag("parser")
@Tag("manual")
class ManualParserTests: ExitValidationTestBase(TestMode.PARSER) {
    override val baseResourcePath: String = "parser"
}