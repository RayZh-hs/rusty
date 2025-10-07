package rusty

import org.junit.jupiter.api.Tag

@Tag("official-fixed")
class OfficialFixedSemanticTests : BaseOfficialSemanticTests() {
    override val baseResourcePath: String = "@official-fixed"
}
