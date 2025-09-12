package rusty.core.utils

/** A slot is a container that can hold a value of type T.
 *
 * Consider it a val that can be set only once.
 **/
class Slot<T> {
    constructor() {
        this.value = UNSET
    }
    constructor(value: T) {
        this.value = value
    }

    private object UNSET

    private var value: Any? = UNSET

    fun isReady(): Boolean = value !== UNSET

    fun get(): T {
        if (value === UNSET) throw IllegalStateException("Slot is not ready")
        @Suppress("unchecked_cast") return value as T
    }

    fun getOrNull(): T? {
        @Suppress("unchecked_cast") return if (value === UNSET) null else value as T
    }

    fun set(value: T) {
        if (this.value !== UNSET) throw IllegalStateException("Slot is already set")
        this.value = value
    }
}

fun <T> T.toSlot() = Slot<T>().apply { set(this@toSlot) }