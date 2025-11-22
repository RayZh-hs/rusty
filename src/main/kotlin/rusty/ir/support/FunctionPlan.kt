package rusty.ir.support

import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.ir.support.visitors.pattern.ParameterNameExtractor
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.TypeUtils

data class FunctionPlan(
    val symbol: SemanticSymbol.Function,
    val name: Name,
    val type: FunctionType,
    val returnType: SemanticType,
    val returnsByPointer: Boolean,
    val selfParamIndex: Int?,
    val retParamIndex: Int?,
)

object FunctionPlanBuilder {
    fun build(
        symbol: SemanticSymbol.Function,
        ownerName: String? = null,
        paramNameExtractor: ParameterNameExtractor? = null,
    ): FunctionPlan {
        val semanticReturn = symbol.returnType.get()
        val returnIrType = semanticReturn.toIRType()
        val returnsDirect = returnIrType is IntegerType || returnIrType == TypeUtils.PTR
        val returnsByPointer = !returnsDirect && semanticReturn != SemanticType.UnitType

        val paramTypes = mutableListOf<space.norb.llvm.core.Type>()
        val paramNames = mutableListOf<String>()
        var selfIndex: Int? = null
        var retIndex: Int? = null

        symbol.selfParam.getOrNull()?.let {
            selfIndex = paramTypes.size
            paramTypes += TypeUtils.PTR
            paramNames += Name.auxSelf().identifier
        }

        if (returnsByPointer) {
            retIndex = paramTypes.size
            paramTypes += TypeUtils.PTR
            paramNames += Name.auxReturn().identifier
        }

        val extractedNames = paramNameExtractor?.orderedParamNames(symbol) ?: emptyList()
        symbol.funcParams.getOrNull()?.forEachIndexed { index, param ->
            paramTypes += param.type.get().toIRType()
            val userName = extractedNames.getOrNull(index)
            paramNames += userName ?: Name.ofVariable(
                symbol = SemanticSymbol.Variable(
                    identifier = "arg$index",
                    definedAt = param.pattern,
                ),
                allowSerial = false
            ).identifier
        }

        val functionIrReturn = if (returnsByPointer) TypeUtils.VOID else returnIrType
        val fnType = FunctionType(functionIrReturn, paramTypes, false, paramNames)
        val fnName = Name.ofFunction(symbol, ownerName)

        return FunctionPlan(
            symbol = symbol,
            name = fnName,
            type = fnType,
            returnType = semanticReturn,
            returnsByPointer = returnsByPointer,
            selfParamIndex = selfIndex,
            retParamIndex = retIndex,
        )
    }
}
