package rusty

import org.junit.jupiter.api.Tag

@Tag("semantic")
@Tag("manual")
class ManualSemanticTests: ExitValidationTestBase(TestMode.SEMANTIC) {
    override val baseResourcePath: String = "semantic"
}