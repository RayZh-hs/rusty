package space.norb.llvm.utils

import com.sun.jdi.CharType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant

operator fun IntegerType.invoke(value: Long): IntConstant {
    return IntConstant(value, this)
}

operator fun IntegerType.invoke(value: Char): IntConstant {
    return IntConstant(value.code.toLong(), this)
}

operator fun CharType.invoke(value: Char): IntConstant {
    return IntConstant(value.code.toLong(), IntegerType.I8)
}

// Direct call on Kotlin Int

fun Int.asConst(bits: Int = 32): IntConstant {
    return IntConstant(this.toLong(), IntegerType(bits))
}

fun Long.asConst(bits: Int = 32): IntConstant {
    return IntConstant(this, IntegerType(bits))
}