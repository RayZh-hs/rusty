package space.norb.llvm.examples

import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.enums.*
import space.norb.llvm.structure.*
import space.norb.llvm.types.*
import space.norb.llvm.values.constants.*
import space.norb.llvm.utils.*

fun main() {
    val module = Module("HelloWorldModule").apply {
        val builder = IRBuilder(this)

        val printf = this.registerFunction(
            name = "printf",
            type = FunctionType(
                returnType = IntegerType.I32,
                paramTypes = listOf(PointerType),
                isVarArg = true
            ),
            linkage = LinkageType.EXTERNAL,
        )
        val strLiteral = "Hello, World!\n\u0000"
        val strType = ArrayType(strLiteral.length, IntegerType.I8)
        val strVar = this.registerGlobalVariable(
            name = "str.literal",
            initialValue = ArrayConstant(elements = strLiteral.map {
                IntConstant(it.code.toLong(), IntegerType.I8)
            }, type = strType,
        ))

        val mainFunction = this.registerFunction(
            name = "main",
            returnType = IntegerType.I32,
            parameterTypes = emptyList(),
        ).apply {
            this.insertBasicBlock("entry", setAsEntrypoint = true).apply {
                builder.positionAtEnd(this)
                val strPtr = builder.insertGep(
                    elementType = strType,
                    address = strVar,
                    indices = listOf(0.asConst(32), 0.asConst(32)),
                    name = "str.ptr"
                )
                builder.insertCall(
                    function = printf,
                    args = listOf(strPtr),
                )
                builder.insertRet(0.asConst(32))
            }
        }
    }.also {
        println(it.emitIR())
    }
}