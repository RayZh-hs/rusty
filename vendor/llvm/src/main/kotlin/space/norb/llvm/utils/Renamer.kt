package space.norb.llvm.utils

object Renamer {
    private val nameCountMap = mutableMapOf<String?, Int>()

    fun another(baseName: String? = null): String {
        if (baseName !in nameCountMap) {
            nameCountMap[baseName] = 0
        } else {
            nameCountMap[baseName] = nameCountMap[baseName]!! + 1
        }
        val serial = nameCountMap[baseName]!!
        return if (baseName == null)
            serial.toString()
        else
            "${baseName}.$serial"
    }

    fun current(baseName: String? = null): String {
        val serial = nameCountMap[baseName] ?: error("No name registered for base name: $baseName")
        return if (baseName == null)
            serial.toString()
        else
            "$baseName.$serial"
    }

    fun clear(baseName: String? = null) {
        nameCountMap.remove(baseName)
    }

    fun clearAll() {
        nameCountMap.clear()
    }
}