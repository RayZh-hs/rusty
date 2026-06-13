package rusty

import kotlin.test.Test
import kotlin.test.assertContains
import space.norb.riscv.RiscvExtension
import space.norb.riscv.RiscvTarget
import space.norb.riscv.XLen
import space.norb.riscv.a0
import space.norb.riscv.a1
import space.norb.riscv.riscv

class RiscvAsmTargetTest {
    @Test
    fun `rv64 target accepts 64 bit shift immediates`() {
        val asm = riscv(target = RiscvTarget(XLen.RV64, setOf(RiscvExtension.I))) {
            slli(a0, a1, 63)
        }.render()

        assertContains(asm, "slli a0, a1, 63")
    }
}
