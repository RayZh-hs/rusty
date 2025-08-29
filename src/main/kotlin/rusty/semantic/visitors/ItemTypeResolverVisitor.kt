package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.support.FunctionParamNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.companions.SelfResolverCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern

class ItemTypeResolverVisitor(ctx: Context) : ScopeAwareVisitorBase(ctx) {
    val staticResolver: StaticResolverCompanion = StaticResolverCompanion(ctx)
    val selfResolver: SelfResolverCompanion = SelfResolverCompanion()

    private fun resolveFunctionSymbol(functionSymbol: SemanticSymbol.Function, scope: Scope) {
        val defNode = functionSymbol.definedAt
        if (defNode != null) {
            val node = defNode as? ItemNode.FunctionItemNode
                ?: throw CompileError("Function symbol defined at a non-function node")
                    .at(functionSymbol.definedAt.pointer)
            node.functionParamsNode.selfParam?.let {
                val selfSymbol = selfResolver.getSelf()
                    ?: throw CompileError("'self' parameter found outside of an impl block")
                        .with(it).with(selfResolver).at(node.pointer)
                when (selfSymbol) {
                    is SemanticSymbol.Struct -> {
                        if (!functionSymbol.selfParam.get()!!.type.isReady())
                            functionSymbol.selfParam.get()!!.type.set(selfSymbol.definesType)
                        selfSymbol
                    }
                    is SemanticSymbol.Enum -> {
                        if (!functionSymbol.selfParam.get()!!.type.isReady())
                            functionSymbol.selfParam.get()!!.type.set(selfSymbol.definesType)
                        selfSymbol
                    }
                    is SemanticSymbol.Trait -> {
                        // TODO: Handle trait 'self' type resolution properly
                        selfSymbol
                    }
                    else -> throw CompileError("'self' parameter found outside of a struct impl block")
                        .with(it).at(node.pointer)
                }
            }
            node.functionParamsNode.functionParams.forEach { param ->
                // resolve the type of the parameter
                when (param) {
                    is FunctionParamNode.FunctionParamTypedPatternNode -> {
                        val type = staticResolver.resolveTypeNode(
                            param.type ?: throw CompileError("Missing type annotation for function parameter")
                                .with(param).at(node.pointer),
                            currentScope())

                        val iteratedPatterns = extractSymbolsFromTypedPattern(param.pattern, type, currentScope())
                        iteratedPatterns.forEach {
                            scope.variableST.declare(it)
                        }
                    }
                    else -> throw CompileError("Unsupported function parameter pattern")
                        .with(param).at(node.pointer)
                }
            }
            node.returnTypeNode.let {
                if (it == null) // assume that no return type means unit
                    functionSymbol.returnType.set(SemanticType.UnitType)
                else if (!functionSymbol.returnType.isReady()) {
                    val retType = staticResolver.resolveTypeNode(it, scope)
                    functionSymbol.returnType.set(retType)
                }
            }
        }
    }

    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        staticResolver.resolveConstItem(node.identifier, currentScope())
    }

    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        // For an override, manually handle the scope resolution
        val scope = currentScope()
        val functionSymbol = (scope.functionST.resolve(node.identifier) as? SemanticSymbol.Function)
            ?: throw CompileError("Unresolved function: ${node.identifier}")
                .with(node).with(scope).at(node.pointer)
        scopeMaintainer.withNextScope {
            val varDefScope = currentScope()
            resolveFunctionSymbol(functionSymbol, varDefScope)
            super.visitFunctionInternal(node)
        }
    }

    override fun visitStructItem(node: ItemNode.StructItemNode) {
        val scope = currentScope()  // assume that traits inherit the scope from the struct's scope
        val resolvedSymbol = scope.typeST.resolve(node.identifier) as? SemanticSymbol.Struct
            ?: throw CompileError("Unresolved struct type: ${node.identifier}")
                .with(node.identifier).at(node.pointer)
        // Within the symbol system of the struct, process all inner signatures
        selfResolver.withinSymbol(resolvedSymbol) {
            // resolve fields, functions and constants
            resolvedSymbol.fields.forEach { (identifier, field) ->
                if (!field.isReady()) {
                    val typeNode = node.fields.find { it.identifier == identifier }?.typeNode
                        ?: throw CompileError("Missing type annotation for struct field: $identifier")
                            .with(node).at(node.pointer)
                    val fieldType = staticResolver.resolveTypeNode(typeNode, scope)
                    field.set(fieldType)
                }
            }
            resolvedSymbol.functions.forEach { (_, function) ->
                resolveFunctionSymbol(function, scope)
            }
            resolvedSymbol.constants.forEach { (_, const) ->
                if (!const.type.isReady()) {
                    val definedAtNode = const.definedAt!!
                    val typeNode = (definedAtNode as? ItemNode.ConstItemNode)?.typeNode
                        ?: throw CompileError("Constant symbol defined at a non-constant node")
                            .at(definedAtNode.pointer)
                    val constType = staticResolver.resolveTypeNode(typeNode, scope)
                    const.type.set(constType)
                }
            }
            // Finally, visit the struct node to resolve any expressions within
            super.visitStructItem(node)
        }
    }

    override fun visitEnumItem(node: ItemNode.EnumItemNode) {
        // TODO: Enum classes should also be able to be implemented
    }

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        scopeMaintainer.skipScope()
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        // TODO: Trait check (trait self-contain ability)
        scopeMaintainer.skipScope()
    }
}