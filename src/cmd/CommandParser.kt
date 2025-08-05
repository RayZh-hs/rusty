package cmd

// A simple keyword-only cli parser
class CommandParser(private val config: CommandParserConfigType) {

    /**
     * @throws IllegalArgumentException when parsing fails
     * */
    fun parse(args: Array<String>): Map<String, String> {
        val providedArgs = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val rawKey = args[i]
            // Assert that the key starts with -
            if (!rawKey.startsWith("-")) {
                throw IllegalArgumentException("Expected a key (e.g., -k or --key), but got '$rawKey'")
            }
            // This trick should remove the prefix (call remove prefix on - twice should also work)
            val key = rawKey.removePrefix("--").removePrefix("-")

            if (i + 1 >= args.size) {
                throw IllegalArgumentException("Missing value for key '$key'")
            }
            if (providedArgs.containsKey(key)) {
                throw IllegalArgumentException("Repetitive key: '$key'")
            }

            val value: String

            if (args[i + 1].startsWith("\"")) {
                // Find the end of the quoted string
                val valueBuilder = StringBuilder()
                var foundEndQuote = false
                for (j in i + 1..< args.size) {
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
                        i = j + 1   // move the cursor
                        break
                    }
                }

                if (!foundEndQuote) {
                    throw IllegalArgumentException("Unpaired quote for value to key '$key'")
                }
                value = valueBuilder.toString()

            } else {
                // Unquoted
                value = args[i + 1]
                i += 2
            }
            providedArgs[key] = value
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

        // Check for unexpected arguments
        val allowedKeys = config.map { it.key }.toSet()
        val unexpectedKeys = providedArgs.keys - allowedKeys

        if (unexpectedKeys.isNotEmpty()) {
            throw IllegalArgumentException("Unexpected arguments provided: $unexpectedKeys")
        }
    }
}