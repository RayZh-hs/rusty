package rusty.cli

enum class Requirement {
    REQUIRED, OPTIONAL
}

enum class ArgType {
    VALUE, FLAG
}

data class CommandParserConfigEntry(
    val key: String,
    val requirement: Requirement,
    val type: ArgType = ArgType.VALUE,
    val aliases: List<String> = emptyList()
)