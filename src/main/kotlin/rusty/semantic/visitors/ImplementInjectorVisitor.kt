package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.utils.getIdentifierFromType
import rusty.semantic.visitors.utils.injectAssociatedItems
import rusty.semantic.visitors.utils.sequentialLookup

class ImplementInjectorVisitor(override val ctx: Context) : ScopeAwareVisitorBase(ctx) {

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        scopeMaintainer.withNextScope { scope ->
            val identifier = getIdentifierFromType(ctx, node.typeNode)
            val semanticStruct = sequentialLookup(identifier, scope, { it.typeST })
                ?: throw CompileError("Cannot find struct or enum with id $identifier to implement").with(node)
            when (semanticStruct.symbol) {
                is SemanticSymbol.AssociativeItem -> {
                    val symbols = scope.functionST.symbols.values + scope.variableST.symbols.values
                    semanticStruct.symbol.injectAssociatedItems(symbols)
                }

                else -> throw CompileError("Expected struct or enum symbol, found ${semanticStruct.symbol}").with(node)
            }
        }
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        scopeMaintainer.withNextScope { scope ->
            val traitName = node.identifier
            val targetIdentifier = getIdentifierFromType(ctx, node.typeNode)

            // We assume that the trait is upper in the hierarchy
            val traitLookup = sequentialLookup(traitName, scope, { it.typeST })
                ?: throw CompileError("Cannot find trait with id $traitName to implement").with(node)
            if (traitLookup.symbol !is SemanticSymbol.Trait) {
                throw CompileError("Expected trait symbol, found ${traitLookup.symbol}").with(node)
            }

            val semanticStruct = sequentialLookup(targetIdentifier, currentScope(), { it.typeST })
                ?: throw CompileError("Cannot find struct or enum with id $targetIdentifier to implement").with(node)
            when (semanticStruct.symbol) {
                is SemanticSymbol.AssociativeItem -> {
                    val trait = traitLookup.symbol

                    // Collect impl symbols inside this impl scope
                    val implFunctions = scope.functionST.symbols.mapValues { (_, sym) ->
                        sym as? SemanticSymbol.Function
                            ?: throw CompileError("Expected function symbol, found $sym").with(node)
                    }
                    val implConstants = scope.variableST.symbols.mapValues { (_, sym) ->
                        sym as? SemanticSymbol.Const
                            ?: throw CompileError("Expected constant symbol, found $sym").with(node)
                    }

                    // Disallow extras not present in the trait
                    implFunctions.keys.forEach { name ->
                        if (!trait.functions.containsKey(name))
                            throw CompileError("Function '$name' is not declared in trait '$traitName'").with(node)
                    }
                    implConstants.keys.forEach { name ->
                        if (!trait.constants.containsKey(name))
                            throw CompileError("Constant '$name' is not declared in trait '$traitName'").with(node)
                    }

                    // Ensure all trait items are implemented, and signatures are compatible at the header level
                    trait.functions.forEach { (name, traitFn) ->
                        val implFn = implFunctions[name]
                            ?: throw CompileError("Missing implementation for trait method '$name' in impl for '$targetIdentifier'").with(node)

                        val tSelf = traitFn.selfParam.get()
                        val iSelf = implFn.selfParam.get()
                        if ((tSelf == null) != (iSelf == null)) {
                            throw CompileError("Method '$name' self receiver kind mismatch with trait").with(node)
                        }
                        if (tSelf != null && iSelf != null) {
                            if (tSelf.isRef != iSelf.isRef || tSelf.isMut != iSelf.isMut) {
                                throw CompileError("Method '$name' self receiver flags mismatch with trait").with(node)
                            }
                        }

                        val tArity = traitFn.funcParams.get().size
                        val iArity = implFn.funcParams.get().size
                        if (tArity != iArity) {
                            throw CompileError("Method '$name' parameter count mismatch with trait: expected $tArity, found $iArity").with(node)
                        }
                    }
                    trait.constants.forEach { (name, _) ->
                        if (!implConstants.containsKey(name))
                            throw CompileError("Missing implementation for trait constant '$name' in impl for '$targetIdentifier'").with(node)
                    }

                    // Inject items when checks pass using the non-deprecated overload
                    val symbols = implFunctions.values + implConstants.values
                    semanticStruct.symbol.injectAssociatedItems(symbols.toList())
                }

                else -> throw CompileError("Expected struct or enum symbol, found ${semanticStruct.symbol}").with(node)
            }
        }
    }
}