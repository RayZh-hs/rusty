package rusty

import org.junit.jupiter.api.Tag

@Tag("official-fixed-ir")
class OfficialFixedIrTests : BaseIrCompilationTests() {
    override val baseResourcePath: String = "@official-fixed"
}
