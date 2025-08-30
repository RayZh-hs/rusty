package rusty.core.utils

import rusty.parser.nodes.utils.afterWhich

@Suppress("unused")
class Volatile<T> {
    private var value: T? = null

    fun write(newValue: T) {
        value = newValue
    }

    fun readOrNull(): T? {
        return value.afterWhich {
            value = null
        }
    }

    fun readOrValue(defaultValue: T): T {
        return (value ?: defaultValue).afterWhich {
            value = null
        }
    }

    fun peekOrNull(): T? {
        return value
    }

    fun peekOrValue(defaultValue: T): T {
        return value ?: defaultValue
    }
}

@Suppress("unused")
class VolatileWithDefault<T>(private val defaultValue: T) {
    private var value: T? = null

    fun write(newValue: T) {
        value = newValue
    }

    fun readOrNull(): T? {
        return value.afterWhich {
            value = null
        }
    }

    fun readOrDefault(): T {
        return (value ?: defaultValue).afterWhich {
            value = null
        }
    }

    fun peekOrNull(): T? {
        return value
    }

    fun peekOrDefault(): T {
        return value ?: defaultValue
    }
}