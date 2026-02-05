package rusty.cli

// A simple keyword-only cli parser
class CommandParser(private val config: List<CommandParserConfigEntry>) {

    /**
     * @throws IllegalArgumentException when parsing fails
     * */
    fun parse(args: Array<String>): Map<String, String> {
        val providedArgs = mutableMapOf<String, String>()

        // Create a lookup map for keys and aliases -> ConfigEntry
        val keyLookup = mutableMapOf<String, CommandParserConfigEntry>()
        for (entry in config) {
            keyLookup[entry.key] = entry
            for (alias in entry.aliases) {
                keyLookup[alias] = entry
            }
        }

        var i = 0
        while (i < args.size) {
            val rawKey = args[i]
            // Assert that the key starts with -
            if (!rawKey.startsWith("-")) {
                throw IllegalArgumentException("Expected a key (e.g., -k or --key), but got '$rawKey'")
            }

            // Normalize key: remove leading dashes
            val normalizedKey = rawKey.trimStart('-')

            val configEntry = keyLookup[normalizedKey]
                ?: throw IllegalArgumentException("Unexpected argument provided: '$rawKey'")

            val canonicalKey = configEntry.key

            if (providedArgs.containsKey(canonicalKey)) {
                throw IllegalArgumentException("Repetitive key: '$canonicalKey' (via '$rawKey')")
            }

            if (configEntry.type == ArgType.FLAG) {
                // Binary flag: just mark as present (true) and move to next arg
                providedArgs[canonicalKey] = "true"
                i++
            } else {
                // Value argument: Expect a value next
                if (i + 1 >= args.size) {
                    throw IllegalArgumentException("Missing value for key '$rawKey'")
                }

                val value: String
                val rawValue = args[i + 1]

                if (rawValue.startsWith("\"")) {
                    // Find the end of the quoted string
                    val valueBuilder = StringBuilder()
                    var foundEndQuote = false
                    // Start looking from the value index
                    var jumpIndex = i + 1

                    for (j in i + 1 until args.size) {
                        val part = args[j]
                        if (j == i + 1) {
                            valueBuilder.append(part.substring(1))
                        } else {
                            // Assume that the whitespace is one space
                            valueBuilder.append(" ").append(part)
                        }

                        if (part.endsWith("\"")) {
                            valueBuilder.setLength(valueBuilder.length - 1) // Remove closing quote
                            foundEndQuote = true
                            jumpIndex = j + 1   // move the cursor past this part
                            break
                        }
                    }

                    if (!foundEndQuote) {
                        throw IllegalArgumentException("Unpaired quote for value to key '$rawKey'")
                    }
                    value = valueBuilder.toString()
                    i = jumpIndex
                } else {
                    // Unquoted
                    value = rawValue
                    i += 2
                }
                providedArgs[canonicalKey] = value
            }
        }

        validate(providedArgs)
        return providedArgs
    }

    private fun validate(providedArgs: Map<String, String>) {
        // Check for missing required arguments
        val missingRequired = config
            .filter { it.requirement == Requirement.REQUIRED && it.key !in providedArgs }
            .map { it.key }

        if (missingRequired.isNotEmpty()) {
            throw IllegalArgumentException("Missing required arguments: $missingRequired")
        }

        // Unexpected keys validation is now handled during the lookup phase in the loop
    }
}