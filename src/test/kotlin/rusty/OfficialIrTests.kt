package rusty

import org.junit.jupiter.api.Tag

@Tag("ir")
@Tag("official")
class OfficialIrTests : IrValidationTestBase() {
    override val baseResourcePath: String = "@official"
    override val subdirectoryPrefix: String = "IR-"
}
