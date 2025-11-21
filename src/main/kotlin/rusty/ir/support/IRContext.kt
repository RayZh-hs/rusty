package rusty.ir.support

import rusty.semantic.support.SemanticSymbol
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.llvm.types.StructType

class IRContext {
    companion object {
        data class EnumValue(val of: SemanticSymbol, val string: String)

        var module = Module("rusty_generated_module")

        val enumIntegerLookup = mutableMapOf<EnumValue, Int>()
        val functionNameLookup = mutableMapOf<SemanticSymbol.Function, Name>()
        val functionLookup = mutableMapOf<SemanticSymbol.Function, Function>()
        val functionPlans = mutableMapOf<SemanticSymbol.Function, FunctionPlan>()

        // Original -> LLVM Struct Type
        val structTypeLookup = mutableMapOf<String, StructType.NamedStructType>()

        // Literal caches
        val stringLiteralLookup = mutableMapOf<String, GlobalVariable>()
        val cStringLiteralLookup = mutableMapOf<String, GlobalVariable>()

        fun reset(moduleName: String = "rusty_generated_module") {
            module = Module(moduleName)
            enumIntegerLookup.clear()
            functionNameLookup.clear()
            functionLookup.clear()
            functionPlans.clear()
            structTypeLookup.clear()
            stringLiteralLookup.clear()
            cStringLiteralLookup.clear()
            Name.reset()
        }
    }
}
