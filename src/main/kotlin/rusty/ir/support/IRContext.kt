package rusty.ir.support

import rusty.semantic.support.SemanticSymbol
import space.norb.llvm.structure.Module
import space.norb.llvm.types.StructType

class IRContext {
    companion object {
        data class EnumValue(val of: SemanticSymbol, val string: String)

        val module = Module("rusty_generated_module")

        val enumIntegerLookup = mutableMapOf<EnumValue, Int>()
        val functionNameLookup = mutableMapOf<SemanticSymbol.Function, Name>()

        // Original -> LLVM Struct Type
        val structTypeLookup = mutableMapOf<String, StructType.NamedStructType>()
    }
}