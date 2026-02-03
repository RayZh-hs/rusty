
package rusty

import org.junit.jupiter.api.Tag

@Tag("ir")
@Tag("official")
class OfficialIrTests : IrValidationTestBase() {
	override val baseResourcePath: String = "@official"
	override val subdirectoryPrefix: String = "IR-"
}

@Tag("semantic")
@Tag("official")
class OfficialSemanticTests : ExitValidationTestBase(TestMode.SEMANTIC) {
	override val baseResourcePath: String = "@official"
	override val subdirectoryPrefix: String = "semantic-"
}

@Tag("ir")
@Tag("fixed")
class OfficialFixedIrTests : IrValidationTestBase() {
	override val baseResourcePath: String = "@official-fixed"
	override val subdirectoryPrefix: String = "IR-"
}

@Tag("semantic")
@Tag("fixed")
class OfficialFixedSemanticTests : ExitValidationTestBase(TestMode.SEMANTIC) {
	override val baseResourcePath: String = "@official-fixed"
	override val subdirectoryPrefix: String = "semantic-"
}

@Tag("ir")
@Tag("manual")
class ManualIrTests : IrValidationTestBase() {
	override val baseResourcePath: String = "ir"
}

@Tag("lexer")
@Tag("manual")
class ManualLexerTests: ExitValidationTestBase(TestMode.LEXER) {
	override val baseResourcePath: String = "lexer"
}

@Tag("parser")
@Tag("manual")
class ManualParserTests: ExitValidationTestBase(TestMode.PARSER) {
	override val baseResourcePath: String = "parser"
}

@Tag("lexer")
@Tag("preprocessor")
class ManualPreprocessorTests: ExitValidationTestBase(TestMode.PREPROCESSOR) {
	override val baseResourcePath: String = "preprocessor"
}

@Tag("semantic")
@Tag("manual")
class ManualSemanticTests: ExitValidationTestBase(TestMode.SEMANTIC) {
	override val baseResourcePath: String = "semantic"
}
