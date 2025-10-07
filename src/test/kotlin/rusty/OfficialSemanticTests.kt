package rusty

import org.junit.jupiter.api.Tag

@Tag("official")
class OfficialSemanticTests : BaseOfficialSemanticTests() {
    override val baseResourcePath: String = "@official"
}
