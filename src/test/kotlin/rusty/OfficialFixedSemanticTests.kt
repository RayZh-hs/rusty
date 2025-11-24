package rusty

import org.junit.jupiter.api.Tag

@Tag("semantic")
@Tag("fixed")
class OfficialFixedSemanticTests : ExitValidationTestBase(TestMode.SEMANTIC) {
    override val baseResourcePath: String = "@official-fixed"
    override val subdirectoryPrefix: String = "semantic-"
}
