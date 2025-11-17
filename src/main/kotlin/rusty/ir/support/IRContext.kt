package rusty.ir.support

import rusty.semantic.support.SemanticSymbol
import space.norb.llvm.structure.Module

class IRContext {
    companion object {
        data class EnumValue(val of: SemanticSymbol, val string: String)

        val module = Module("rusty_generated_module")

        val structNameLookup = mutableMapOf<SemanticSymbol.Struct, String>()
        val enumIntegerLookup = mutableMapOf<EnumValue, Int>()
        val functionNameLookup = mutableMapOf<SemanticSymbol.Function, String>()
    }
}