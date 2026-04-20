package rusty.opt

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import space.norb.llvm.builder.IRBuilder
import space.norb.llvm.structure.Module
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.values.constants.IntConstant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IROptimizerTest {
    @Test
    fun `runs cfg simplify before mem2reg`() {
        assumeTrue(passApiAvailable(), "LLVM transformation passes are not on the test classpath")

        val module = Module("optimizer-test")
        val builder = IRBuilder(module)
        val function = module.registerFunction("main", FunctionType(IntegerType.I32, emptyList()))
        val entry = function.insertBasicBlock("entry")
        val next = function.insertBasicBlock("next")

        builder.positionAtEnd(entry)
        val slot = builder.insertAlloca(IntegerType.I32, "slot")
        builder.insertStore(IntConstant(7, IntegerType.I32), slot)
        builder.insertBr(next)

        builder.positionAtEnd(next)
        val value = builder.insertLoad(IntegerType.I32, slot, "value")
        builder.insertRet(value)

        val optimized = IROptimizer.run(module)
        val ir = optimized.toIRString()

        assertTrue(ir.contains("ret i32 7"))
        assertFalse(ir.contains("alloca"))
        assertFalse(ir.contains("load"))
        assertFalse(ir.contains("store"))
        assertFalse(ir.contains("br label"))
    }

    private fun passApiAvailable(): Boolean = try {
        Class.forName("space.norb.llvm.transformation.presets.CFGSimplifyPass")
        Class.forName("space.norb.llvm.transformation.presets.Mem2RegPass")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
