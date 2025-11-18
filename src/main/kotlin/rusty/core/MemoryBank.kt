package rusty.core

// A memory bank stores information for quick retrieval
class MemoryBank<Source, Target> {
    private val storage: MutableMap<Source, Target> = mutableMapOf()

    /**
     * Recall a value for [source], computing it via [lazyEval] only when needed.
     * Avoid calling [lazyEval] more than once to prevent side-effects (e.g. scope changes).
     */
    fun recall(source: Source, lazyEval: () -> Target): Target {
        val existing = storage[source]
        if (existing != null) return existing
        val value = lazyEval()
        storage[source] = value
        return value
    }

    fun overwrite(source: Source, target: Target) {
        storage[source] = target
    }

    fun clear() {
        storage.clear()
    }
}