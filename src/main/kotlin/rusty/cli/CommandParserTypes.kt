package rusty.cli

enum class Requirement {
    REQUIRED, OPTIONAL,
}

data class CommandParserConfigEntry(val key: String, val requirement: Requirement)
typealias CommandParserConfigType = List<CommandParserConfigEntry>
