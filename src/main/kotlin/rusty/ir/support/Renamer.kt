package rusty.ir.support

/**
 * Simple serial generator for SSA-friendly names. The counter is tracked per
 * base name and starts at 0 on first use.
 */
class Renamer {
    private val counters = mutableMapOf<String, Int>()

    fun next(base: String): Int {
        val current = counters.getOrDefault(base, -1) + 1
        counters[base] = current
        return current
    }

    fun clear(base: String) {
        counters.remove(base)
    }

    fun clearAll() {
        counters.clear()
    }
}
