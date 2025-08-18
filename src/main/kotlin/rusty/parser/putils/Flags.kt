package rusty.parser.putils

class Flags {
    private val registry: MutableSet<String> = mutableSetOf()

    private fun guardEmptyName(name: String) {
        if (name.isEmpty()) {
            throw IllegalArgumentException("Flag name cannot be empty")
        }
    }

    fun up(name: String) {
        guardEmptyName(name)
        registry.add(name)
    }

    fun down(name: String) {
        guardEmptyName(name)
        registry.remove(name)
    }

    fun get(name: String) = {
        guardEmptyName(name)
        registry.contains(name)
    }

    fun <T> scope(name: String, block: () -> T): T {
        guardEmptyName(name)
        up(name)
        return try {
            block()
        } finally {
            down(name)
        }
    }
}