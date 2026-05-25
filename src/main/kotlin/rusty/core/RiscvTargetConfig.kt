package rusty.core

import space.norb.riscv.RiscvExtension
import space.norb.riscv.RiscvTarget
import space.norb.riscv.XLen

object RiscvTargetConfig {
    const val ARCH = "rv64im"
    const val CLANG_TRIPLE = "riscv64-unknown-elf"
    const val LLVM_TRIPLE = "riscv64-unknown-unknown-elf"
    const val ABI = "lp64"
    const val DATA_LAYOUT = "e-m:e-p:64:64-i64:64-i128:128-n32:64-S128"
    const val POINTER_WIDTH_BITS = 64
    const val REGISTER_BYTES = POINTER_WIDTH_BITS / 8

    val ASM_TARGET: RiscvTarget =
        RiscvTarget(XLen.RV64, setOf(RiscvExtension.I, RiscvExtension.M))
}
