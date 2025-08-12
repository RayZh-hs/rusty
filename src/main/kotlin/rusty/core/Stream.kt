package rusty.core

import java.util.Stack
import kotlin.math.min

// Stream provides a programmatic way of traversing a const iterable object
class Stream<T> (private val raw: List<T>) {
    data class CursorItem(val name: String, val ptr: Int)

    private val curs: Stack<CursorItem> = Stack<CursorItem>()
    var cur
        get() = curs.peek().ptr
        set(value) {
            assert(curs.isNotEmpty())
            val item = curs.pop()
            curs.push(CursorItem(item.name, value))
        }

    init {
        // the main cursor is the crate cursor
        curs.push(CursorItem("crate", 0))
    }

    fun atEnd(): Boolean {
        return cur >= raw.size
    }

    fun atBeginning(): Boolean {
        return cur <= 0
    }

    // This will return T and consume one object
    fun read(): T {
        cur += 1
        return raw[cur - 1]
    }

    fun readOrNull(): T? {
        if (cur >= raw.size) return null
        cur += 1
        return raw[cur - 1]
    }

    // This will return T without consuming any object
    fun peek(): T {
        return raw[cur]
    }

    fun peekOrNull(): T? {
        if (cur >= raw.size) return null
        return raw[cur]
    }

    fun peekAt(index: Int): T {
        return raw[index]
    }

    fun peekAtOrNull(index: Int): T? {
        if (cur >= raw.size || cur < 0) return null
        return raw[index]
    }

    fun consume(x: Int) {
        cur = min(cur + x, raw.size)
    }

    fun isEmpty(): Boolean {
        return raw.isEmpty()
    }

    fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    fun pushCursor(name: String) {
        val topCurPtr = curs.peek().ptr
        curs.push(CursorItem(name, topCurPtr))
    }

    fun popCursor(): CursorItem {
        assert(curs.isNotEmpty())
        return curs.pop()
    }

    fun popCursorWithApply(): CursorItem {
        val oriTop = curs.pop()
        val oriSecondTop = curs.pop()
        return curs.push(CursorItem(oriSecondTop.name, oriTop.ptr))
    }
}
