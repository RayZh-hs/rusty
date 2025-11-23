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
        val functionRenamers = mutableMapOf<SemanticSymbol.Function, Renamer>()

        // Original -> LLVM Struct Type
        val structTypeLookup = mutableMapOf<String, StructType.NamedStructType>()
        val structSizeFunctionLookup = mutableMapOf<String, Function>()

        // Literal caches
        val stringLiteralLookup = mutableMapOf<String, GlobalVariable>()
        val cStringLiteralLookup = mutableMapOf<String, GlobalVariable>()

        fun renamerFor(function: SemanticSymbol.Function): Renamer {
            return functionRenamers.getOrPut(function) { Renamer() }
        }

        fun reset(moduleName: String = "rusty_generated_module") {
            module = Module(moduleName)
            enumIntegerLookup.clear()
            functionNameLookup.clear()
            functionLookup.clear()
            functionPlans.clear()
            functionRenamers.clear()
            structTypeLookup.clear()
            structSizeFunctionLookup.clear()
            stringLiteralLookup.clear()
            cStringLiteralLookup.clear()
            Name.reset()
        }
    }
}
