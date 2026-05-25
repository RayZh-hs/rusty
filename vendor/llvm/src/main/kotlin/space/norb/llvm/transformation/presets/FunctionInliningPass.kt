package space.norb.llvm.transformation.presets

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.presets.FunctionDependencyAnalysis
import space.norb.llvm.analysis.presets.FunctionDependencyGraph
import space.norb.llvm.core.MetadataCapable
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.binary.AShrInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.binary.AndInst
import space.norb.llvm.instructions.binary.FAddInst
import space.norb.llvm.instructions.binary.FDivInst
import space.norb.llvm.instructions.binary.FMulInst
import space.norb.llvm.instructions.binary.FRemInst
import space.norb.llvm.instructions.binary.FSubInst
import space.norb.llvm.instructions.binary.LShrInst
import space.norb.llvm.instructions.binary.MulInst
import space.norb.llvm.instructions.binary.OrInst
import space.norb.llvm.instructions.binary.SDivInst
import space.norb.llvm.instructions.binary.SRemInst
import space.norb.llvm.instructions.binary.ShlInst
import space.norb.llvm.instructions.binary.SubInst
import space.norb.llvm.instructions.binary.UDivInst
import space.norb.llvm.instructions.binary.URemInst
import space.norb.llvm.instructions.binary.XorInst
import space.norb.llvm.instructions.casts.BitcastInst
import space.norb.llvm.instructions.casts.PtrToIntInst
import space.norb.llvm.instructions.casts.SExtInst
import space.norb.llvm.instructions.casts.TruncInst
import space.norb.llvm.instructions.casts.ZExtInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.instructions.other.FCmpInst
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.instructions.terminators.ReturnInst
import space.norb.llvm.instructions.terminators.SwitchInst
import space.norb.llvm.instructions.terminators.UnreachableInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.VoidType

class FunctionInliningPass(
    private val isSimpleEnough: (Function) -> Boolean = ::defaultIsSimpleEnough
) : IRPass() {
    private data class ReturnSite(val block: BasicBlock, val value: Value?)

    override fun run(module: Module, am: AnalysisManager): Module {
        am.register(FunctionDependencyAnalysis)
        val dependencies = am.get(FunctionDependencyAnalysis::class)

        for (function in dependencies.bottomUpFunctions()) {
            if (function.isDeclaration) continue
            inlineCallsInFunction(function, dependencies)
        }

        return module
    }

    private fun inlineCallsInFunction(
        function: Function,
        dependencies: FunctionDependencyGraph
    ) {
        var blockIndex = 0
        while (blockIndex < function.basicBlocks.size) {
            val block = function.basicBlocks[blockIndex]
            var instructionIndex = 0
            var splitCurrentBlock = false

            while (instructionIndex < block.instructions.size) {
                val call = block.instructions[instructionIndex] as? CallInst
                if (call != null && canInline(call, dependencies)) {
                    inlineCall(function, block, instructionIndex, call)
                    splitCurrentBlock = true
                    break
                }
                instructionIndex++
            }

            blockIndex++
            if (splitCurrentBlock) {
                continue
            }
        }
    }

    private fun canInline(
        call: CallInst,
        dependencies: FunctionDependencyGraph
    ): Boolean {
        if (!call.isDirectCall()) return false

        val callee = call.callee as? Function ?: return false
        if (callee.isDeclaration || callee.entryBlock == null) return false
        if (callee.hasAttribute(NO_INLINE_ATTRIBUTE)) return false
        if (dependencies.dependsOn(callee, callee)) return false

        return isSimpleEnough(callee)
    }

    private fun inlineCall(
        caller: Function,
        callerBlock: BasicBlock,
        callIndex: Int,
        call: CallInst
    ) {
        val callee = call.callee as Function
        val usedValueNames = collectValueNames(caller)
        val usedBlockNames = collectBlockNames(caller)
        val context = nextName("${call.name ?: callee.name ?: "call"}.inline", usedValueNames)

        val valueMap = linkedMapOf<Value, Value>()
        val blockMap = linkedMapOf<BasicBlock, BasicBlock>()
        for ((parameter, argument) in callee.parameters.zip(call.arguments)) {
            valueMap[parameter] = argument
        }

        for (calleeBlock in callee.basicBlocks) {
            blockMap[calleeBlock] = BasicBlock(
                nextName("${callee.name ?: "callee"}.$context.${calleeBlock.name ?: "block"}", usedBlockNames),
                caller
            )
        }

        val continuationBlock = BasicBlock(
            nextName("${callerBlock.name ?: "block"}.$context.cont", usedBlockNames),
            caller
        )
        val returnSites = mutableListOf<ReturnSite>()

        createPhiPlaceholders(callee, blockMap, valueMap, usedValueNames, context)
        cloneCalleeBody(
            callee = callee,
            continuationBlock = continuationBlock,
            blockMap = blockMap,
            valueMap = valueMap,
            returnSites = returnSites,
            usedValueNames = usedValueNames,
            context = context
        )
        fillClonedPhis(callee, blockMap, valueMap)

        val beforeCall = callerBlock.instructions.take(callIndex)
        val afterCall = callerBlock.instructions.drop(callIndex + 1)
        continuationBlock.instructions.addAll(afterCall)
        continuationBlock.terminator = afterCall.lastOrNull() as? TerminatorInst

        val inlinedEntry = blockMap.getValue(callee.entryBlock!!)
        val entryBranch = BranchInst.createUnconditional("br", VoidType, inlinedEntry)
        callerBlock.instructions.clear()
        callerBlock.instructions.addAll(beforeCall)
        callerBlock.instructions.add(entryBranch)
        callerBlock.terminator = entryBranch

        val callerBlockIndex = caller.basicBlocks.indexOf(callerBlock)
        caller.basicBlocks.addAll(
            callerBlockIndex + 1,
            callee.basicBlocks.map { blockMap.getValue(it) } + continuationBlock
        )

        val replacement = createReturnReplacement(call, continuationBlock, returnSites, usedValueNames, context)
        if (replacement != null) {
            replaceAllUses(caller, call, replacement)
        }
    }

    private fun createPhiPlaceholders(
        callee: Function,
        blockMap: Map<BasicBlock, BasicBlock>,
        valueMap: MutableMap<Value, Value>,
        usedValueNames: MutableSet<String>,
        context: String
    ) {
        for (calleeBlock in callee.basicBlocks) {
            val clonedBlock = blockMap.getValue(calleeBlock)
            for (instruction in calleeBlock.instructions) {
                val phi = instruction as? PhiNode ?: continue
                val clonedPhi = PhiNode.createPlaceholder(
                    cloneValueName(phi, usedValueNames, context),
                    phi.type
                )
                copyInstructionState(phi, clonedPhi)
                clonedBlock.instructions.add(clonedPhi)
                valueMap[phi] = clonedPhi
            }
        }
    }

    private fun cloneCalleeBody(
        callee: Function,
        continuationBlock: BasicBlock,
        blockMap: Map<BasicBlock, BasicBlock>,
        valueMap: MutableMap<Value, Value>,
        returnSites: MutableList<ReturnSite>,
        usedValueNames: MutableSet<String>,
        context: String
    ) {
        for (calleeBlock in callee.basicBlocks) {
            val clonedBlock = blockMap.getValue(calleeBlock)

            for (instruction in calleeBlock.instructions) {
                when (instruction) {
                    is PhiNode -> continue
                    is ReturnInst -> {
                        val returnValue = instruction.getReturnValue()?.let { remapValue(it, valueMap, blockMap) }
                        val branch = BranchInst.createUnconditional("br", VoidType, continuationBlock)
                        copyInstructionState(instruction, branch)
                        clonedBlock.instructions.add(branch)
                        clonedBlock.terminator = branch
                        returnSites.add(ReturnSite(clonedBlock, returnValue))
                    }
                    else -> {
                        val clonedInstruction = cloneInstruction(
                            instruction = instruction,
                            valueMap = valueMap,
                            blockMap = blockMap,
                            usedValueNames = usedValueNames,
                            context = context
                        )
                        clonedBlock.instructions.add(clonedInstruction)
                        valueMap[instruction] = clonedInstruction
                        if (clonedInstruction is TerminatorInst) {
                            clonedBlock.terminator = clonedInstruction
                        }
                    }
                }
            }
        }
    }

    private fun fillClonedPhis(
        callee: Function,
        blockMap: Map<BasicBlock, BasicBlock>,
        valueMap: Map<Value, Value>
    ) {
        for (calleeBlock in callee.basicBlocks) {
            for (instruction in calleeBlock.instructions) {
                val phi = instruction as? PhiNode ?: continue
                val clonedPhi = valueMap.getValue(phi) as PhiNode
                for ((value, block) in phi.incomingValues) {
                    clonedPhi.addIncomingMutable(
                        remapValue(value, valueMap, blockMap),
                        blockMap.getValue(block)
                    )
                }
            }
        }
    }

    private fun createReturnReplacement(
        call: CallInst,
        continuationBlock: BasicBlock,
        returnSites: List<ReturnSite>,
        usedValueNames: MutableSet<String>,
        context: String
    ): Value? {
        if (!call.producesValue()) return null
        if (returnSites.isEmpty()) return null

        if (returnSites.size == 1) {
            return returnSites.single().value
        }

        val incomingValues = returnSites.map { returnSite ->
            val value = requireNotNull(returnSite.value) {
                "Non-void inline candidate ${call.callee.name} has a return without a value"
            }
            value to returnSite.block
        }
        val phi = PhiNode.create(
            call.name ?: nextName("$context.result", usedValueNames),
            call.type,
            incomingValues
        )
        continuationBlock.instructions.add(0, phi)
        return phi
    }

    private fun cloneInstruction(
        instruction: Instruction,
        valueMap: Map<Value, Value>,
        blockMap: Map<BasicBlock, BasicBlock>,
        usedValueNames: MutableSet<String>,
        context: String
    ): Instruction {
        fun remap(value: Value): Value = remapValue(value, valueMap, blockMap)
        val name = cloneValueName(instruction, usedValueNames, context)

        val cloned = when (instruction) {
            is AddInst -> AddInst(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is SubInst -> SubInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is MulInst -> MulInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is SDivInst -> SDivInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is UDivInst -> UDivInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is URemInst -> URemInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is SRemInst -> SRemInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is FAddInst -> FAddInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is FSubInst -> FSubInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is FMulInst -> FMulInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is FDivInst -> FDivInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is FRemInst -> FRemInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is AndInst -> AndInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is OrInst -> OrInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is XorInst -> XorInst.create(name, instruction.type, remap(instruction.lhs), remap(instruction.rhs))
            is LShrInst -> LShrInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is AShrInst -> AShrInst.create(name, remap(instruction.lhs), remap(instruction.rhs))
            is ShlInst -> ShlInst.create(name, remap(instruction.lhs), remap(instruction.rhs))

            is AllocaInst -> AllocaInst(
                name,
                instruction.allocatedType,
                instruction.getOperandsList().firstOrNull()?.let(::remap)
            )
            is LoadInst -> LoadInst(name, instruction.loadedType, remap(instruction.pointer))
            is StoreInst -> StoreInst(instruction.name, instruction.storedType, remap(instruction.value), remap(instruction.pointer))
            is GetElementPtrInst -> GetElementPtrInst(
                name,
                instruction.elementType,
                remap(instruction.pointer),
                instruction.indices.map(::remap),
                instruction.isInBounds
            )

            is TruncInst -> TruncInst.create(name, remap(instruction.value), instruction.type as IntegerType)
            is ZExtInst -> ZExtInst.create(name, remap(instruction.value), instruction.type as IntegerType)
            is SExtInst -> SExtInst.create(name, remap(instruction.value), instruction.type as IntegerType)
            is BitcastInst -> BitcastInst.create(name, remap(instruction.value), instruction.type)
            is PtrToIntInst -> PtrToIntInst.create(name, remap(instruction.value), instruction.type as IntegerType)

            is CallInst -> if (instruction.isDirectCall()) {
                CallInst.createDirectCall(name, remap(instruction.callee), instruction.arguments.map(::remap))
            } else {
                CallInst.createIndirectCall(name, instruction.type, remap(instruction.callee), instruction.arguments.map(::remap))
            }
            is ICmpInst -> ICmpInst.create(name, instruction.predicate, remap(instruction.lhs), remap(instruction.rhs))
            is FCmpInst -> FCmpInst.create(name, instruction.predicate, remap(instruction.lhs), remap(instruction.rhs))
            is CommentAttachment -> CommentAttachment(instruction.name, instruction.comment)

            is BranchInst -> if (instruction.isUnconditional()) {
                BranchInst.createUnconditional(instruction.name, instruction.type, remap(instruction.getDestination()))
            } else {
                BranchInst.createConditional(
                    instruction.name,
                    instruction.type,
                    remap(requireNotNull(instruction.getCondition())),
                    remap(instruction.getTrueDestination()),
                    remap(instruction.getFalseDestination())
                )
            }
            is SwitchInst -> SwitchInst.create(
                instruction.name,
                instruction.type,
                remap(instruction.getCondition()),
                remap(instruction.getDefaultDestination()),
                instruction.getCases().map { (value, destination) -> remap(value) to remap(destination) }
            )
            is UnreachableInst -> UnreachableInst.create(instruction.name, instruction.type)
            is ReturnInst -> error("Return instructions are handled by cloneCalleeBody")
            is PhiNode -> error("Phi nodes are cloned as placeholders before the body")
            else -> throw IllegalArgumentException("Unsupported inline instruction: ${instruction::class.simpleName}")
        }

        copyInstructionState(instruction, cloned)
        return cloned
    }

    private fun remapValue(
        value: Value,
        valueMap: Map<Value, Value>,
        blockMap: Map<BasicBlock, BasicBlock>
    ): Value {
        return valueMap[value]
            ?: (value as? BasicBlock)?.let { blockMap[it] }
            ?: value
    }

    private fun replaceAllUses(function: Function, oldValue: Value, newValue: Value) {
        for (block in function.basicBlocks) {
            for (instruction in block.instructions) {
                if (instruction == oldValue) continue
                for (index in 0 until instruction.getNumOperands()) {
                    if (instruction.getOperand(index) == oldValue) {
                        instruction.setOperand(index, newValue)
                    }
                }
            }
        }
    }

    private fun cloneValueName(
        instruction: Instruction,
        usedValueNames: MutableSet<String>,
        context: String
    ): String? {
        if (!instruction.producesLocalValue()) return instruction.name

        val baseName = instruction.name ?: "value"
        return nextName("$context.$baseName", usedValueNames)
    }

    private fun Instruction.producesLocalValue(): Boolean =
        type != VoidType && this !is StoreInst && this !is TerminatorInst

    private fun collectValueNames(function: Function): MutableSet<String> {
        val names = linkedSetOf<String>()
        function.parameters.mapNotNullTo(names) { it.name }
        for (block in function.basicBlocks) {
            block.instructions.mapNotNullTo(names) { it.name }
        }
        return names
    }

    private fun collectBlockNames(function: Function): MutableSet<String> =
        function.basicBlocks.mapNotNullTo(linkedSetOf()) { it.name }

    private fun nextName(baseName: String, usedNames: MutableSet<String>): String {
        var candidate = sanitizeName(baseName)
        var suffix = 0
        while (!usedNames.add(candidate)) {
            suffix++
            candidate = "${sanitizeName(baseName)}.$suffix"
        }
        return candidate
    }

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_.-]"), ".")

    private fun copyInstructionState(from: Instruction, to: Instruction) {
        to.inlineComment = from.inlineComment
        copyMetadata(from, to)
    }

    private fun copyMetadata(from: MetadataCapable, to: MetadataCapable) {
        for ((kind, metadata) in from.getAllMetadata()) {
            to.setMetadata(kind, metadata)
        }
    }

    companion object {
        const val NO_INLINE_ATTRIBUTE = "__no_inline"

        fun defaultIsSimpleEnough(function: Function): Boolean =
            function.basicBlocks.sumOf { it.instructions.size } < 10
    }
}
