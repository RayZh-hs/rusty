package rusty.core

// A memory bank stores information for quick retrieval
class MemoryBank<Source, Target> {
    private val storage: MutableMap<Source, Target> = mutableMapOf()

    fun recall(source: Source, lazyEval: () -> Target): Target {
        println("Recalling from MemoryBank: $source, set to: ${lazyEval()}")
        return storage.getOrPut(source) { lazyEval() }
    }

    fun overwrite(source: Source, target: Target) {
        storage[source] = target
    }

    fun clear() {
        storage.clear()
    }
}