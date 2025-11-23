package rusty.ir.support.visitors

import rusty.ir.support.IRContext
import space.norb.llvm.builder.BuilderUtils
import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.enums.LinkageType
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.TypeUtils

class StructSizeFunctionGenerator {
    fun run() {
        if (IRContext.structTypeLookup.isEmpty()) return
        val builder = IRBuilder(IRContext.module)
        val size32Type = TypeUtils.I32 as IntegerType
        val size64Type = TypeUtils.I64 as IntegerType
        val indexType = TypeUtils.I32 as IntegerType

        for ((identifier, structType) in IRContext.structTypeLookup) {
            if (IRContext.structSizeFunctionLookup.containsKey(identifier)) continue

            val name = "aux.func.sizeof.$identifier"
            val fnType = FunctionType(TypeUtils.I32, emptyList(), false, emptyList())
            val fn = IRContext.module.registerFunction(name, fnType, LinkageType.EXTERNAL, false)

            val entry = fn.insertBasicBlock("aux.block.sizeof.$identifier", setAsEntrypoint = true)
            builder.positionAtEnd(entry)

            val scratch = builder.insertAlloca(structType, null)
            val baseInt = builder.insertPtrToInt(scratch, size64Type, null)
            val gep = builder.insertGep(
                structType,
                scratch,
                listOf(BuilderUtils.getIntConstant(1, indexType)),
                null
            )
            val nextInt = builder.insertPtrToInt(gep, size64Type, null)
            val diff = builder.insertSub(nextInt, baseInt, null)
            val size32 = builder.insertTrunc(diff, size32Type, null)
            builder.insertRet(size32)

            IRContext.structSizeFunctionLookup[identifier] = fn
        }
    }
}
