package rusty.core.utils

/**
 * A slot is a container that can hold a value of type T.
 *
 * Consider it a val that can be set only once.
 * */
class Slot <T> {
    constructor() {
        this.value = null
    }
    constructor(value: T) {
        this.value = value
    }
    private var value: T? = null

    fun isReady(): Boolean = (value != null)

    fun get(): T {
        return value ?: throw IllegalStateException("Slot is not ready")
    }

    fun getOrNull(): T? {
        return value
    }

    fun set(value: T) {
        if (this.value != null)
            throw IllegalStateException("Slot is already set")
        this.value = value
    }
}