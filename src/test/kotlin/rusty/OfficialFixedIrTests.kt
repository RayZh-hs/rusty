package rusty

import org.junit.jupiter.api.Tag

@Tag("ir")
@Tag("fixed")
class OfficialFixedIrTests : IrValidationTestBase() {
    override val baseResourcePath: String = "@official-fixed"
    override val subdirectoryPrefix: String = "IR-"
}
