package rusty

import org.junit.jupiter.api.Tag

@Tag("official-ir")
class OfficialIrTests : BaseIrCompilationTests() {
    override val baseResourcePath: String = "@official"
}
