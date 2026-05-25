package space.norb.llvm.examples

import space.norb.llvm.builder.BuilderUtils
import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.structure.Module

fun main() {
    val module = Module("StructExample").apply {
        val builder = IRBuilder(this)
        val pointType = this.registerNamedStructType(
            name = "Point",
            elementTypes = listOf(
                BuilderUtils.getIntType(32),    // x coordinate
                BuilderUtils.getIntType(32),    // y coordinate
            ),
        )
        val fooFunction = this.registerFunction(
            name = "PointFactory",
            parameterTypes = listOf(
                BuilderUtils.getIntType(32),    // x coordinate
                BuilderUtils.getIntType(32),    // y coordinate
            ),
            returnType =  pointType,
        ).apply {
            val fooFunction = this
            this.insertBasicBlock("entry", setAsEntrypoint = true).apply {
                builder.positionAtEnd(this)
                val (xParam, yParam) = fooFunction.parameters
                val ptr = builder.insertAlloca(pointType, "pointAlloca")
                val xFieldPtr = builder.insertGep(pointType, ptr, listOf(BuilderUtils.getIntConstant(0L, 32), BuilderUtils.getIntConstant(0L, 32)), "xFieldPtr")
                val yFieldPtr = builder.insertGep(pointType, ptr, listOf(BuilderUtils.getIntConstant(0L, 32), BuilderUtils.getIntConstant(1L, 32)), "yFieldPtr")
                builder.insertStore(xParam, xFieldPtr)
                builder.insertStore(yParam, yFieldPtr)
                val loadedPoint = builder.insertLoad(pointType, ptr, "loadedPoint")
                builder.insertRet(loadedPoint)
            }
        }
    }
    val irCode = module.toIRString()
    println(irCode)
}