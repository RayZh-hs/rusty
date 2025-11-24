package rusty

import org.junit.jupiter.api.Tag

@Tag("ir")
@Tag("manual")
class ManualIrTests : IrValidationTestBase() {
    override val baseResourcePath: String = "ir"
}
