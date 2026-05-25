package space.norb.riscv

public enum class XLen {
    RV32,
    RV64,
}

public enum class RiscvExtension {
    I,
    M,
}

public data class RiscvTarget(
    public val xlen: XLen,
    public val extensions: Set<RiscvExtension>,
) {
    public fun supports(extension: RiscvExtension): Boolean = extension in extensions

    public companion object {
        public val RV32I: RiscvTarget = RiscvTarget(XLen.RV32, setOf(RiscvExtension.I))
        public val RV32IM: RiscvTarget = RiscvTarget(XLen.RV32, setOf(RiscvExtension.I, RiscvExtension.M))
    }
}
