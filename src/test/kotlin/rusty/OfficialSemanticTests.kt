package rusty

import org.junit.jupiter.api.Tag

@Tag("semantic")
@Tag("official")
class OfficialSemanticTests : ExitValidationTestBase(TestMode.SEMANTIC) {
    override val baseResourcePath: String = "@official"
    override val subdirectoryPrefix: String = "semantic-"
}
