package rusty.core.utils

fun <T, K> Iterable<T>.associateUniquelyBy(
    keySelector: (T) -> K,
    exception: (T) -> Exception = { IllegalArgumentException("Duplicate key $it for elements found") }
): Map<K, T> {
    val map = mutableMapOf<K, T>()
    for (element in this) {
        val key = keySelector(element)
        if (map.containsKey(key)) {
            throw exception(element)
        }
        map[key] = element
    }
    return map
}