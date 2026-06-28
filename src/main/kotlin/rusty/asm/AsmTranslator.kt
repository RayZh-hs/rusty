package rusty.asm

import rusty.core.RiscvTargetConfig
import rusty.asm.support.AsmContext
import rusty.asm.support.PlacedStackObject
import rusty.asm.support.StackFrame
import rusty.asm.utils.SavableSlot
import rusty.asm.utils.calleeSavedRegisters
import rusty.asm.utils.callerSavedRegisters
import rusty.asm.utils.Register
import space.norb.llvm.analysis.presets.BlockLivenessAnalysis
import space.norb.llvm.analysis.presets.DominatorTreeAnalysis
import space.norb.llvm.analysis.presets.FunctionDominanceInfo
import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.binary.AShrInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.binary.AndInst
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
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.instructions.terminators.ReturnInst
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.BasicBlockId
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.structure.Argument
import space.norb.llvm.types.ArrayType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.PointerType
import space.norb.llvm.types.StructType
import space.norb.llvm.types.VoidType
import space.norb.llvm.utils.computeLayout
import space.norb.llvm.values.constants.ArrayConstant
import space.norb.llvm.values.constants.IntConstant
import space.norb.llvm.values.constants.NullPointerConstant
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.llvm.values.MDString
import space.norb.riscv.*
import space.norb.riscv.Register as RvRegister

internal class AsmTranslator(private val context: AsmContext) {
    private val argumentRegisters = listOf(a0, a1, a2, a3, a4, a5, a6, a7)
    private val registerBytes = RiscvTargetConfig.REGISTER_BYTES
    private val pointerWidthBits = RiscvTargetConfig.POINTER_WIDTH_BITS
    private val module: Module = context.module
    private lateinit var asm: RiscvAsm
    private lateinit var function: Function
    private lateinit var frame: StackFrame
    private lateinit var allocation: Map<Value, SavableSlot>
    private lateinit var allocaObjects: Map<AllocaInst, PlacedStackObject>
    private lateinit var blockLabels: Map<BasicBlock, String>
    private lateinit var edgePhiMoves: Map<Pair<BasicBlock, BasicBlock>, List<Pair<PhiNode, Value>>>
    private lateinit var cachedFields: Map<GetElementPtrInst, CachedField>
    private lateinit var cachedFieldAvailability: Map<Instruction, Set<FieldAddressKey>>
    private lateinit var noAliasArgumentPairs: Set<Pair<Argument, Argument>>
    private lateinit var callLiveOut: Map<CallInst, Set<Value>>
    private val callerSavedRegisterSet = callerSavedRegisters.toSet()

    private data class CachedField(
        val gep: GetElementPtrInst,
        val key: FieldAddressKey,
        val savedRegister: Register,
        val register: RvRegister,
        val sizeBytes: Int,
        val dirty: Boolean,
        val hasLoad: Boolean,
        val initializeAt: Instruction?,
    )

    fun translate(): String {
        return riscv(target = RiscvTargetConfig.ASM_TARGET) {
            asm = this
            emitGlobals()
            text()
            for (fn in module.functions.filter { it.hasBody() }) {
                lowerFunction(fn)
                blank()
            }
        }.render()
    }

    private fun emitGlobals() {
        if (module.globalVariables.isEmpty()) return
        asm.rodata()
        for (global in module.globalVariables) {
            val name = global.asmName()
            if (global.isConstantValue) {
                asm.global(name)
            }
            asm.label(name)
            emitGlobalInitializer(global)
        }
        asm.blank()
    }

    private fun emitGlobalInitializer(global: GlobalVariable) {
        val initializer = global.initializer
        when (initializer) {
            is ArrayConstant -> asm.emitArrayConstant(initializer)
            is IntConstant -> emitIntegerConstant(initializer)
            is NullPointerConstant -> emitPointerWord(0)
            null -> asm.zero(global.elementType?.sizeBytes(module) ?: 4)
            else -> asm.zero(initializer.type.sizeBytes(module))
        }
    }

    private fun emitIntegerConstant(value: IntConstant) {
        when (value.type.bitWidth) {
            in 1..8 -> asm.byte(value.value.toInt())
            in 9..16 -> asm.half(value.value.toInt())
            in 17..32 -> asm.word(expr(value.value.toString()))
            in 33..64 -> asm.directive(".dword", expr(value.value.toString()))
            else -> throw UnsupportedOperationException("Unsupported integer width ${value.type.bitWidth} in global")
        }
    }

    private fun emitPointerWord(value: Long) {
        if (registerBytes == 8) {
            asm.directive(".dword", expr(value.toString()))
        } else {
            asm.word(expr(value.toString()))
        }
    }

    private fun RiscvAsm.emitArrayConstant(array: ArrayConstant) {
        if (array.type.elementType == IntegerType.I8) {
            byte(*array.elements.map { (it as IntConstant).value.toInt() }.toIntArray())
            return
        }

        for (element in array.elements) {
            when (element) {
                is IntConstant -> when (element.type.bitWidth) {
                    in 1..8 -> byte(element.value.toInt())
                    in 9..16 -> half(element.value.toInt())
                    in 17..32 -> word(expr(element.value.toString()))
                    in 33..64 -> directive(".dword", expr(element.value.toString()))
                    else -> throw UnsupportedOperationException(
                        "Unsupported integer width ${element.type.bitWidth} in array initializer"
                    )
                }
                is ArrayConstant -> emitArrayConstant(element)
                is NullPointerConstant -> emitPointerWord(0)
                else -> zero(element.type.sizeBytes(module))
            }
        }
    }

    private fun lowerFunction(fn: Function) {
        function = fn
        frame = context.stackManager.getStackFrame(fn)
        allocation = context.registerAllocation.getValue(fn)
        blockLabels = fn.basicBlocks.associateWith { block -> "${fn.asmName()}.${block.asmName()}" }
        edgePhiMoves = buildEdgePhiMoves(fn)
        allocaObjects = collectAllocaObjects(fn)
        noAliasArgumentPairs = proveNoAliasArgumentPairs(fn)
        cachedFields = planCachedFields(fn)
        cachedFieldAvailability = analyzeCachedFieldAvailability(fn)
        callLiveOut = computeCallLiveOut(fn)

        asm.global(fn.asmName())
        asm.type(fn.asmName(), "@function")
        asm.label(fn.asmName())
        materializeCachedFieldSaves()
        emitPrologue()
        moveParametersToAllocatedLocations()
        initializeCachedFields()

        for (block in fn.basicBlocks) {
            asm.label(blockLabels.getValue(block))
            for (instruction in block.instructionsIncludingTerminator()) {
                lowerInstruction(instruction)
            }
        }

        asm.size(fn.asmName(), ".-${fn.asmName()}")
    }

    private fun buildEdgePhiMoves(fn: Function): Map<Pair<BasicBlock, BasicBlock>, List<Pair<PhiNode, Value>>> {
        val moves = linkedMapOf<Pair<BasicBlock, BasicBlock>, MutableList<Pair<PhiNode, Value>>>()
        for (block in fn.basicBlocks) {
            for (phi in block.instructions.filterIsInstance<PhiNode>()) {
                for ((incomingValue, incomingBlock) in phi.incomingValues) {
                    moves.getOrPut(incomingBlock to block) { mutableListOf() }.add(phi to incomingValue)
                }
            }
        }
        return moves
    }

    private fun collectAllocaObjects(fn: Function): Map<AllocaInst, PlacedStackObject> {
        val result = linkedMapOf<AllocaInst, PlacedStackObject>()
        for ((index, alloca) in fn.instructions().filterIsInstance<AllocaInst>().withIndex()) {
            val name = alloca.stackObjectName(index)
            result[alloca] = frame.objectForAlloca(alloca)
                ?: frame.alloca(
                    sizeBytes = (alloca.allocatedType.sizeBytes(module).toLong() * alloca.constantArraySize())
                        .toIntExact("alloca $name"),
                    alignBytes = alloca.allocatedType.computeLayout(module, pointerWidthBits).alignment,
                    name = name,
                    alloca = alloca,
                )
        }
        return result
    }

    private fun planCachedFields(fn: Function): Map<GetElementPtrInst, CachedField> {
        if (fn.containsCall()) return emptyMap()

        val canonical = linkedMapOf<FieldAddressKey, GetElementPtrInst>()
        val useCounts = linkedMapOf<FieldAddressKey, Int>()
        val hasStore = linkedMapOf<FieldAddressKey, Boolean>()
        val hasLoad = linkedMapOf<FieldAddressKey, Boolean>()
        val geps = fn.instructions().filterIsInstance<GetElementPtrInst>().toList()
        if (geps.size > 1_000) return emptyMap()
        val dominanceInfo = context.analysisManager.get(DominatorTreeAnalysis::class).getFunctionInfo(fn)

        for (gep in geps) {
            val key = fieldAddressKey(gep) ?: continue
            if (!isCacheableFieldUse(gep)) continue
            if (key.base is AllocaInst && !hasOnlyCacheableFieldUses(key.base)) continue
            canonical.putIfAbsent(key, gep)
            useCounts[key] = (useCounts[key] ?: 0) + gep.getUses().count { user ->
                (user is LoadInst && user.pointer == gep) || (user is StoreInst && user.pointer == gep)
            }
            hasStore[key] = hasStore[key] == true || gep.getUses().any { user ->
                user is StoreInst && user.pointer == gep
            }
            hasLoad[key] = hasLoad[key] == true || gep.getUses().any { user ->
                user is LoadInst && user.pointer == gep
            }
        }

        val available = calleeSavedRegisters
            .asSequence()
            .filter { register -> frame.objectWithName(register.name.lowercase()) == null }
            .toList()
        if (available.isEmpty()) return emptyMap()

        val selected = useCounts.entries
            .filter { (_, count) -> count >= 2 }
            .filter { (key, _) -> key.base is AllocaInst || findDominatingCachedLoad(key, geps, dominanceInfo) != null }
            .sortedByDescending { it.value }
            .take(available.size)
            .map { it.key to canonical.getValue(it.key) }

        if (selected.isEmpty()) return emptyMap()

        val selectedKeys = selected.map { it.first }.toSet()
        val registerByKey = selected.mapIndexed { index, (key, _) -> key to available[index] }.toMap()
        val result = linkedMapOf<GetElementPtrInst, CachedField>()
        for (gep in geps) {
            val key = fieldAddressKey(gep) ?: continue
            if (key !in selectedKeys) continue
            val register = registerByKey.getValue(key)
            val keyHasLoad = hasLoad[key] == true
            result[gep] = CachedField(
                gep = canonical.getValue(key),
                key = key,
                savedRegister = register,
                register = register.toRv(),
                sizeBytes = gep.getFinalElementType().sizeBytes(module),
                dirty = hasStore[key] == true,
                hasLoad = keyHasLoad,
                initializeAt = if (key.base is AllocaInst) null else findDominatingCachedLoad(key, geps, dominanceInfo),
            )
        }
        return result
    }

    private fun analyzeCachedFieldAvailability(fn: Function): Map<Instruction, Set<FieldAddressKey>> {
        val keys = cachedFields.values.map { it.key }.toSet()
        if (keys.isEmpty()) return emptyMap()

        val entryAvailable = keys.filterTo(linkedSetOf()) { it.base is AllocaInst }
        val predecessors = fn.basicBlocks.associateWith { mutableListOf<BasicBlock>() }
        for (block in fn.basicBlocks) {
            for (successor in block.getSuccessors()) {
                predecessors.getValue(successor).add(block)
            }
        }

        val inSets = fn.basicBlocks.associateWith { linkedSetOf<FieldAddressKey>() }.toMutableMap()
        val outSets = fn.basicBlocks.associateWith { linkedSetOf<FieldAddressKey>() }.toMutableMap()
        inSets[fn.basicBlocks.first()] = entryAvailable

        var changed: Boolean
        do {
            changed = false
            for (block in fn.basicBlocks) {
                val nextIn = if (block === fn.basicBlocks.first()) {
                    entryAvailable
                } else {
                    val preds = predecessors.getValue(block)
                    if (preds.isEmpty()) {
                        linkedSetOf()
                    } else {
                        preds.map { outSets.getValue(it) }
                            .reduce { acc, set -> acc.intersect(set).toCollection(linkedSetOf()) }
                    }
                }
                if (inSets.getValue(block) != nextIn) {
                    inSets[block] = nextIn
                    changed = true
                }

                val nextOut = transferCachedAvailability(block, nextIn)
                if (outSets.getValue(block) != nextOut) {
                    outSets[block] = nextOut
                    changed = true
                }
            }
        } while (changed)

        val result = linkedMapOf<Instruction, Set<FieldAddressKey>>()
        for (block in fn.basicBlocks) {
            var available = inSets.getValue(block)
            for (instruction in block.instructionsIncludingTerminator()) {
                result[instruction] = available
                available = transferCachedAvailability(instruction, available)
            }
        }
        return result
    }

    private fun transferCachedAvailability(
        block: BasicBlock,
        input: Set<FieldAddressKey>,
    ): LinkedHashSet<FieldAddressKey> {
        var available = input.toCollection(linkedSetOf())
        for (instruction in block.instructionsIncludingTerminator()) {
            available = transferCachedAvailability(instruction, available)
        }
        return available
    }

    private fun transferCachedAvailability(
        instruction: Instruction,
        input: Set<FieldAddressKey>,
    ): LinkedHashSet<FieldAddressKey> {
        val available = input.toCollection(linkedSetOf())
        when (instruction) {
            is LoadInst -> cachedFields[instruction.pointer]?.let { available.add(it.key) }
            is StoreInst -> {
                val cached = cachedFields[instruction.pointer]
                for (field in cachedFields.values.distinctBy { it.key }) {
                    if (mayAliasCachedField(field, instruction.pointer)) {
                        available.remove(field.key)
                    }
                }
                if (cached != null) available.add(cached.key)
            }
            is CallInst -> available.clear()
        }
        return available
    }

    private fun findDominatingCachedLoad(
        key: FieldAddressKey,
        geps: List<GetElementPtrInst>,
        dominanceInfo: FunctionDominanceInfo?,
    ): LoadInst? {
        if (dominanceInfo == null) return null
        val loads = cachedMemoryUsers(key, geps).filterIsInstance<LoadInst>()
        val firstLoad = loads.minWithOrNull(compareBy<LoadInst> { instructionOrder(it) })
            ?: return null
        val users = cachedMemoryUsers(key, geps)
        return if (users.all { dominates(firstLoad, it, dominanceInfo) }) firstLoad else null
    }

    private fun cachedMemoryUsers(key: FieldAddressKey, geps: List<GetElementPtrInst>): List<Instruction> {
        return geps
            .filter { fieldAddressKey(it) == key }
            .flatMap { gep ->
                gep.getUses().filterIsInstance<Instruction>().filter { user ->
                    (user is LoadInst && user.pointer == gep) || (user is StoreInst && user.pointer == gep)
                }
            }
    }

    private fun dominates(
        dominator: Instruction,
        dominated: Instruction,
        dominanceInfo: FunctionDominanceInfo,
    ): Boolean {
        val dominatorBlock = dominator.getParent() as? BasicBlock ?: return false
        val dominatedBlock = dominated.getParent() as? BasicBlock ?: return false
        if (dominatorBlock === dominatedBlock) {
            val instructions = dominatorBlock.instructionsIncludingTerminator().toList()
            return instructions.indexOf(dominator) <= instructions.indexOf(dominated)
        }

        var current: BasicBlockId? = dominatedBlock.id
        while (current != null) {
            if (current == dominatorBlock.id) return true
            current = dominanceInfo.immediateDominators[current]
        }
        return false
    }

    private fun instructionOrder(instruction: Instruction): Int {
        var order = 0
        for (block in function.basicBlocks) {
            for (candidate in block.instructionsIncludingTerminator()) {
                if (candidate === instruction) return order
                order += 1
            }
        }
        return Int.MAX_VALUE
    }

    private data class FieldAddressKey(
        val base: Value,
        val indices: List<Long>,
    )

    private fun fieldAddressKey(gep: GetElementPtrInst): FieldAddressKey? {
        if (gep.getFinalElementType().sizeBytes(module) > registerBytes) return null
        val base = canonicalFieldBase(gep.pointer) ?: return null

        val indices = gep.indices
        if (indices.size < 2) return null
        val first = indices.first() as? IntConstant ?: return null
        if (first.value != 0L) return null

        val path = indices.drop(1).map { index ->
            (index as? IntConstant)?.value ?: return null
        }
        if (path.isEmpty()) return null
        return FieldAddressKey(base, path)
    }

    private fun canonicalFieldBase(value: Value): Value? {
        if (value is Argument || value is AllocaInst) return value
        val load = value as? LoadInst ?: return null
        val alloca = load.pointer as? AllocaInst ?: return null
        val stores = alloca.getUses().filterIsInstance<StoreInst>().filter { it.pointer == alloca }
        if (stores.size != 1) return null
        return stores.single().value as? Argument
    }

    private fun isCacheableFieldUse(gep: GetElementPtrInst): Boolean {
        return gep.getUses().isNotEmpty() && gep.getUses().all { user ->
            when (user) {
                is LoadInst -> user.pointer == gep
                is StoreInst -> user.pointer == gep && user.storedType.sizeBytes(module) <= registerBytes
                else -> false
            }
        }
    }

    private fun hasOnlyCacheableFieldUses(alloca: AllocaInst): Boolean {
        return alloca.getUses().isNotEmpty() && alloca.getUses().all { user ->
            user is GetElementPtrInst && user.pointer == alloca && fieldAddressKey(user) != null && isCacheableFieldUse(user)
        }
    }

    private fun materializeCachedFieldSaves() {
        for (field in cachedFields.values.distinctBy { it.savedRegister }) {
            if (frame.objectWithName(field.savedRegister.name.lowercase()) == null) {
                frame.save(field.savedRegister)
            }
        }
    }

    private fun initializeCachedFields() {
        for (field in cachedFields.values.distinctBy { it.gep }) {
            if (field.key.base !is AllocaInst) continue
            if (!field.hasLoad) continue
            loadSized(field.register, emitGepAddress(field.gep, t6), field.sizeBytes)
        }
    }

    private fun flushCachedFields() {
        for (field in cachedFields.values.distinctBy { it.gep }.filter { it.dirty }) {
            storeSized(field.register, emitGepAddress(field.gep, t6), field.sizeBytes)
        }
    }

    private fun reloadCachedFieldsAliasedBy(pointer: Value, except: CachedField? = null) {
        for (field in cachedFields.values.distinctBy { it.gep }) {
            if (!field.hasLoad || field == except) continue
            if (mayAliasCachedField(field, pointer)) {
                loadSized(field.register, emitGepAddress(field.gep, t6), field.sizeBytes)
            }
        }
    }

    private fun mayAliasCachedField(field: CachedField, pointer: Value): Boolean {
        val storeKey = (pointer as? GetElementPtrInst)?.let { fieldAddressKey(it) }
        if (storeKey != null) {
            if (!mayAliasBase(field.key.base, storeKey.base)) return false
            if (field.key.base === storeKey.base && field.key.indices != storeKey.indices) return false
            return true
        }

        val root = pointerRoot(pointer)
        if (root != null) {
            return mayAliasBase(field.key.base, root)
        }

        return field.key.base is Argument
    }

    private fun pointerRoot(value: Value): Value? {
        return when (value) {
            is AllocaInst, is Argument, is GlobalVariable -> value
            is GetElementPtrInst -> pointerRoot(value.pointer)
            is BitcastInst -> pointerRoot(value.value)
            else -> null
        }
    }

    private fun mayAliasBase(lhs: Value, rhs: Value): Boolean {
        if (lhs === rhs) return true
        if (lhs is AllocaInst || rhs is AllocaInst) return false
        if (lhs is Argument && rhs is Argument && argumentsProvenNoAlias(lhs, rhs)) return false
        return true
    }

    private fun argumentsProvenNoAlias(lhs: Argument, rhs: Argument): Boolean {
        return lhs to rhs in noAliasArgumentPairs || rhs to lhs in noAliasArgumentPairs
    }

    private fun proveNoAliasArgumentPairs(fn: Function): Set<Pair<Argument, Argument>> {
        val pointerArgs = fn.parameters
            .mapIndexedNotNull { index, argument -> if (argument.type is PointerType) index to argument else null }
        if (pointerArgs.size < 2) return emptySet()

        val callSites = module.functions
            .asSequence()
            .filter { it.hasBody() }
            .flatMap { it.instructions() }
            .filterIsInstance<CallInst>()
            .filter { it.callee === fn }
            .toList()
        if (callSites.isEmpty()) return emptySet()

        val result = linkedSetOf<Pair<Argument, Argument>>()
        for (i in pointerArgs.indices) {
            for (j in i + 1 until pointerArgs.size) {
                val (lhsIndex, lhsArg) = pointerArgs[i]
                val (rhsIndex, rhsArg) = pointerArgs[j]
                if (callSites.all { call ->
                        val provenance = call.pointerArgumentProvenance()
                        val lhs = provenance[lhsIndex]
                        val rhs = provenance[rhsIndex]
                        lhs != null && rhs != null && provenNoAliasProvenance(lhs, rhs)
                    }
                ) {
                    result.add(lhsArg to rhsArg)
                }
            }
        }
        return result
    }

    private fun provenNoAliasProvenance(lhs: String, rhs: String): Boolean {
        val lhsPath = PointerProvenancePath.parse(lhs) ?: return false
        val rhsPath = PointerProvenancePath.parse(rhs) ?: return false
        if (lhsPath.root != rhsPath.root) return true
        val commonLength = minOf(lhsPath.fields.size, rhsPath.fields.size)
        for (index in 0 until commonLength) {
            if (lhsPath.fields[index] != rhsPath.fields[index]) return true
        }
        return false
    }

    private data class PointerProvenancePath(
        val root: String,
        val fields: List<Int>,
    ) {
        companion object {
            fun parse(value: String): PointerProvenancePath? {
                val parts = value.split('.')
                val root = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
                val fields = parts.drop(1).map { it.toIntOrNull() ?: return null }
                return PointerProvenancePath(root, fields)
            }
        }
    }

    private fun CallInst.pointerArgumentProvenance(): Map<Int, String> {
        val encoded = (getMetadata("rx.ptr.args") as? MDString)?.value ?: return emptyMap()
        return encoded
            .split(';')
            .mapNotNull { entry ->
                val separator = entry.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val index = entry.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
                index to entry.substring(separator + 1)
            }
            .toMap()
    }

    private fun emitPrologue() {
        adjustStack(-frame.frameSizeBytes)
        for (saved in frame.frameObjects.filter { it.stackObject.savedRegister != null }) {
            val register = saved.stackObject.savedRegister!!.toRv()
            storeRegister(register, addressOfStack(saved, t6))
        }
    }

    private fun emitEpilogue() {
        for (saved in frame.frameObjects.filter { it.stackObject.savedRegister != null }.asReversed()) {
            val register = saved.stackObject.savedRegister!!.toRv()
            loadRegister(register, addressOfStack(saved, t6))
        }
        adjustStack(frame.frameSizeBytes)
        asm.ret()
    }

    private fun adjustStack(delta: Int) {
        if (delta == 0) return
        if (delta in -2048..2047) {
            asm.addi(sp, sp, delta)
        } else {
            asm.li(t6, kotlin.math.abs(delta))
            if (delta < 0) asm.sub(sp, sp, t6) else asm.add(sp, sp, t6)
        }
    }

    private fun moveParametersToAllocatedLocations() {
        if (canDirectMoveParameters()) {
            moveParametersDirectly()
            return
        }

        for ((index, _) in function.parameters.withIndex()) {
            val src = if (index < argumentRegisters.size) {
                argumentRegisters[index]
            } else {
                val offset = frame.frameSizeBytes + (index - argumentRegisters.size) * registerBytes
                loadRegister(t4, stackArgumentAddress(offset, t6))
                t4
            }
            val saveTemp = resolveCallArgTemp(frame, index)
            storeRegister(src, addressOfStack(saveTemp, t6))
        }

        for ((index, parameter) in function.parameters.withIndex()) {
            val temp = resolveCallArgTemp(frame, index)
            val loaded = loadRegisterScratch(temp)
            val size = parameter.type.sizeBytes(module)
            if (size <= registerBytes) {
                writeValue(parameter, loaded)
            } else {
                val safeSrc = if (loaded == t5) { asm.mv(t4, loaded); t4 } else loaded
                val destination = addressOf(parameter, t6)
                copyMemory(destination, safeSrc, size)
            }
        }
    }

    private fun canDirectMoveParameters(): Boolean {
        return function.parameters.withIndex().all { (index, parameter) ->
            index < argumentRegisters.size &&
                parameter.type.sizeBytes(module) <= registerBytes &&
                allocation[parameter] is SavableSlot.Register
        }
    }

    private fun moveParametersDirectly() {
        val moves = function.parameters.mapIndexed { index, parameter ->
            ScalarArgumentMove(parameter, argumentRegisters[index])
        }.toMutableList()

        while (moves.isNotEmpty()) {
            moves.removeAll { move ->
                val destination = (allocation[move.parameter] as SavableSlot.Register).physical.toRv()
                destination == move.source
            }
            if (moves.isEmpty()) return

            val nextIndex = moves.indexOfFirst { candidate ->
                val destination = (allocation[candidate.parameter] as SavableSlot.Register).physical.toRv()
                moves.none { other -> other !== candidate && other.source == destination }
            }

            if (nextIndex >= 0) {
                val move = moves.removeAt(nextIndex)
                val destination = (allocation[move.parameter] as SavableSlot.Register).physical.toRv()
                asm.mv(destination, move.source)
                continue
            }

            val cycleSource = moves.first().source
            asm.mv(t3, cycleSource)
            for (move in moves) {
                if (move.source == cycleSource) {
                    move.source = t3
                }
            }
        }
    }

    private data class ScalarArgumentMove(
        val parameter: Argument,
        var source: RvRegister,
    )

    private data class CallArgumentMove(
        val index: Int,
        val destination: RvRegister,
        var source: CallArgumentMoveSource,
    )

    private sealed class CallArgumentMoveSource {
        data class RegisterSource(val register: RvRegister) : CallArgumentMoveSource()
        data class TempSource(val temp: PlacedStackObject) : CallArgumentMoveSource()

        fun registerOrNull(): RvRegister? = (this as? RegisterSource)?.register
    }

    private fun resolveCallArgTemp(frame: StackFrame, index: Int): PlacedStackObject {
        val name = callArgumentTempName(index)
        return frame.objectWithName(name)
            ?: frame.temp(sizeBytes = registerBytes, alignBytes = registerBytes, name = name)
    }

    private fun loadRegisterScratch(temp: PlacedStackObject): RvRegister {
        loadRegister(t5, addressOfStack(temp, t6))
        return t5
    }

    private fun lowerInstruction(instruction: Instruction) {
        when (instruction) {
            is CommentAttachment -> Unit
            is PhiNode -> Unit
            is AllocaInst -> lowerAlloca(instruction)
            is LoadInst -> lowerLoad(instruction)
            is StoreInst -> lowerStore(instruction)
            is GetElementPtrInst -> lowerGep(instruction)
            is BinaryInst -> lowerBinary(instruction)
            is ICmpInst -> lowerIcmp(instruction)
            is BranchInst -> lowerBranch(instruction)
            is ReturnInst -> lowerReturn(instruction)
            is CallInst -> lowerCall(instruction)
            is CastInst -> lowerCast(instruction)
            else -> throw UnsupportedOperationException(
                "Cannot lower ${instruction::class.simpleName} in ${function.name}"
            )
        }
    }

    private fun lowerAlloca(instruction: AllocaInst) {
        if (instruction !in allocation) return
        val obj = allocaObjects.getValue(instruction)
        writeValue(instruction, addressOfStack(obj, t6))
    }

    private fun lowerLoad(instruction: LoadInst) {
        val cached = cachedFields[instruction.pointer]
        if (cached != null && instruction.loadedType.sizeBytes(module) <= registerBytes) {
            val available = cached.key in cachedFieldAvailability[instruction].orEmpty()
            if (!available) {
                loadSized(cached.register, emitGepAddress(cached.gep, t6), cached.sizeBytes)
            }
            writeValue(instruction, cached.register)
            return
        }

        val size = instruction.loadedType.sizeBytes(module)
        if (size <= registerBytes) {
            val source = addressOf(instruction.pointer, t6)
            val loaded = loadSized(t5, source, size)
            writeValue(instruction, loaded)
        } else {
            val destination = addressOf(instruction, t3)
            val source = addressOf(instruction.pointer, t6)
            copyMemory(destination, source, size)
        }
    }

    private fun lowerStore(instruction: StoreInst) {
        val cached = cachedFields[instruction.pointer]
        if (cached != null && instruction.storedType.sizeBytes(module) <= registerBytes) {
            val value = loadValue(instruction.value, t4)
            if (cached.register != value) asm.mv(cached.register, value)
            storeSized(cached.register, emitGepAddress(cached.gep, t6), cached.sizeBytes)
            return
        }

        val size = instruction.storedType.sizeBytes(module)
        if (size <= registerBytes) {
            val value = loadValue(instruction.value, t4)
            storeSized(value, addressOf(instruction.pointer, t6), size)
        } else {
            val destination = addressOf(instruction.pointer, t3)
            val source = addressOf(instruction.value, t6)
            copyMemory(destination, source, size)
        }
    }

    private fun lowerGep(instruction: GetElementPtrInst) {
        if (instruction in cachedFields) return
        writeValue(instruction, emitGepAddress(instruction, t4))
    }

    private fun emitGepAddress(instruction: GetElementPtrInst, result: RvRegister): RvRegister {
        val base = addressOf(instruction.pointer, result)
        if (base != result) asm.mv(result, base)

        var currentType = instruction.elementType
        for ((indexPosition, indexValue) in instruction.indices.withIndex()) {
            val step = gepStep(currentType, indexPosition, indexValue)
            currentType = step.nextType
            if (step.offsetOrStride == 0) continue

            val constantIndex = (indexValue as? IntConstant)?.value
            if (constantIndex != null) {
                val offset = if (step.scaleByIndex) {
                    constantIndex * step.offsetOrStride
                } else {
                    step.offsetOrStride.toLong()
                }
                addImmediate(result, result, offset.toIntExact("gep offset"))
            } else {
                if (!step.scaleByIndex) {
                    throw UnsupportedOperationException("Dynamic struct GEP index in ${function.name}")
                }
                val indexRegister = loadValue(indexValue, t6)
                scaleIndex(t5, indexRegister, step.offsetOrStride)
                asm.add(result, result, t5)
            }
        }

        return result
    }

    private fun scaleIndex(destination: RvRegister, index: RvRegister, strideBytes: Int) {
        when {
            strideBytes == 1 -> {
                if (destination != index) asm.mv(destination, index)
            }
            strideBytes > 0 && strideBytes and (strideBytes - 1) == 0 -> {
                asm.emit("slli", destination, index, expr(Integer.numberOfTrailingZeros(strideBytes).toString()))
            }
            else -> {
                asm.li(t5, strideBytes)
                asm.mul(destination, index, t5)
            }
        }
    }

    private data class GepStep(
        val offsetOrStride: Int,
        val nextType: Type,
        val scaleByIndex: Boolean,
    )

    private fun gepStep(
        currentType: Type,
        indexPosition: Int,
        indexValue: Value,
    ): GepStep {
        if (indexPosition == 0) {
            return GepStep(currentType.sizeBytes(module), currentType, scaleByIndex = true)
        }

        return when (currentType) {
            is ArrayType -> GepStep(currentType.elementType.sizeBytes(module), currentType.elementType, scaleByIndex = true)
            is StructType.AnonymousStructType -> {
                val field = (indexValue as? IntConstant)?.value?.toInt()
                    ?: throw UnsupportedOperationException("Dynamic struct GEP index in ${function.name}")
                GepStep(
                    structFieldOffset(currentType.elementTypes, currentType.isPacked, field),
                    currentType.elementTypes[field],
                    scaleByIndex = false,
                )
            }
            is StructType.NamedStructType -> {
                val field = (indexValue as? IntConstant)?.value?.toInt()
                    ?: throw UnsupportedOperationException("Dynamic struct GEP index in ${function.name}")
                val resolvedType = (if (currentType.isOpaque()) module.getNamedStructType(currentType.name) else currentType)
                    ?: throw UnsupportedOperationException("Opaque struct GEP for ${currentType.name}")
                val fields = resolvedType.elementTypes
                    ?: throw UnsupportedOperationException("Opaque struct GEP for ${currentType.name}")
                GepStep(
                    structFieldOffset(fields, resolvedType.isPacked, field),
                    fields[field],
                    scaleByIndex = false,
                )
            }
            else -> GepStep(currentType.sizeBytes(module), currentType, scaleByIndex = true)
        }
    }

    private fun lowerBinary(instruction: BinaryInst) {
        if (lowerBinaryImmediate(instruction)) return

        val lhs = loadValue(instruction.lhs, t4)
        val rhs = loadValue(instruction.rhs, t5)
        val dst = valueDestinationRegister(instruction, t6)
        val isI32 = instruction.type is IntegerType && (instruction.type as IntegerType).bitWidth == 32

        when (instruction) {
            is AddInst -> if (isI32) asm.emit("addw", dst, lhs, rhs) else asm.add(dst, lhs, rhs)
            is SubInst -> if (isI32) asm.emit("subw", dst, lhs, rhs) else asm.sub(dst, lhs, rhs)
            is MulInst -> if (isI32) asm.emit("mulw", dst, lhs, rhs) else asm.mul(dst, lhs, rhs)
            is SDivInst -> if (isI32) asm.emit("divw", dst, lhs, rhs) else asm.div(dst, lhs, rhs)
            is UDivInst -> if (isI32) asm.emit("divuw", dst, lhs, rhs) else asm.divu(dst, lhs, rhs)
            is SRemInst -> if (isI32) asm.emit("remw", dst, lhs, rhs) else asm.rem(dst, lhs, rhs)
            is URemInst -> if (isI32) asm.emit("remuw", dst, lhs, rhs) else asm.remu(dst, lhs, rhs)
            is AndInst -> asm.and(dst, lhs, rhs)
            is OrInst -> asm.or(dst, lhs, rhs)
            is XorInst -> asm.xor(dst, lhs, rhs)
            is ShlInst -> if (isI32) asm.emit("sllw", dst, lhs, rhs) else asm.sll(dst, lhs, rhs)
            is LShrInst -> if (isI32) asm.emit("srlw", dst, lhs, rhs) else asm.srl(dst, lhs, rhs)
            is AShrInst -> if (isI32) asm.emit("sraw", dst, lhs, rhs) else asm.sra(dst, lhs, rhs)
            else -> throw UnsupportedOperationException(
                "Cannot lower binary ${instruction::class.simpleName} in ${function.name}"
            )
        }

        writeValue(instruction, dst)
    }

    private fun lowerBinaryImmediate(instruction: BinaryInst): Boolean {
        val isI32 = instruction.type is IntegerType && (instruction.type as IntegerType).bitWidth == 32

        fun emitAddImmediate(sourceValue: Value, immediate: Long): Boolean {
            if (immediate !in -2048..2047) return false
            val source = loadValue(sourceValue, t4)
            val dst = valueDestinationRegister(instruction, t6)
            if (isI32) {
                asm.emit("addiw", dst, source, expr(immediate.toString()))
            } else {
                asm.addi(dst, source, immediate.toInt())
            }
            writeValue(instruction, dst)
            return true
        }

        return when (instruction) {
            is AddInst -> {
                val lhsConstant = instruction.lhs as? IntConstant
                val rhsConstant = instruction.rhs as? IntConstant
                when {
                    rhsConstant != null -> emitAddImmediate(instruction.lhs, rhsConstant.value)
                    lhsConstant != null -> emitAddImmediate(instruction.rhs, lhsConstant.value)
                    else -> false
                }
            }
            is SubInst -> {
                val rhsConstant = instruction.rhs as? IntConstant ?: return false
                emitAddImmediate(instruction.lhs, -rhsConstant.value)
            }
            else -> false
        }
    }

    private fun lowerIcmp(instruction: ICmpInst) {
        val lhs = loadValue(instruction.lhs, t4)
        val rhs = loadValue(instruction.rhs, t5)
        val dst = valueDestinationRegister(instruction, t6)

        when (instruction.predicate.name) {
            "EQ" -> {
                asm.xor(dst, lhs, rhs)
                asm.seqz(dst, dst)
            }
            "NE" -> {
                asm.xor(dst, lhs, rhs)
                asm.snez(dst, dst)
            }
            "SLT" -> asm.slt(dst, lhs, rhs)
            "SGT" -> asm.slt(dst, rhs, lhs)
            "SLE" -> {
                asm.slt(dst, rhs, lhs)
                asm.xori(dst, dst, 1)
            }
            "SGE" -> {
                asm.slt(dst, lhs, rhs)
                asm.xori(dst, dst, 1)
            }
            "ULT" -> asm.sltu(dst, lhs, rhs)
            "UGT" -> asm.sltu(dst, rhs, lhs)
            "ULE" -> {
                asm.sltu(dst, rhs, lhs)
                asm.xori(dst, dst, 1)
            }
            "UGE" -> {
                asm.sltu(dst, lhs, rhs)
                asm.xori(dst, dst, 1)
            }
            else -> throw UnsupportedOperationException("Unknown icmp predicate ${instruction.predicate}")
        }

        writeValue(instruction, dst)
    }

    private fun lowerBranch(instruction: BranchInst) {
        val currentBlock = instruction.getParent() as? BasicBlock
            ?: function.basicBlocks.first { it.terminator == instruction }

        if (instruction.isUnconditional()) {
            val destination = instruction.getDestination() as BasicBlock
            emitPhiMoves(currentBlock, destination)
            asm.j(blockLabels.getValue(destination))
            return
        }

        val trueDestination = instruction.getTrueDestination() as BasicBlock
        val falseDestination = instruction.getFalseDestination() as BasicBlock
        val falseEdgeLabel = "${blockLabels.getValue(currentBlock)}.__false_edge"
        val cond = loadValue(instruction.getCondition()!!, t5)

        asm.beqz(cond, falseEdgeLabel)
        emitPhiMoves(currentBlock, trueDestination)
        asm.j(blockLabels.getValue(trueDestination))
        asm.label(falseEdgeLabel)
        emitPhiMoves(currentBlock, falseDestination)
        asm.j(blockLabels.getValue(falseDestination))
    }

    private fun emitPhiMoves(from: BasicBlock, to: BasicBlock) {
        val moves = edgePhiMoves[from to to].orEmpty()
        val scalarMoves = moves
            .filter { (phi, _) -> phi.type.sizeBytes(module) <= registerBytes }
            .map { (phi, incoming) -> ScalarPhiMove(phi, PhiMoveSource.ValueSource(incoming)) }
            .toMutableList()

        emitScalarPhiMoves(scalarMoves)

        for ((phi, incoming) in moves) {
            if (phi.type.sizeBytes(module) <= registerBytes) continue
            val phiDest = addressOf(phi, t3)
            val incomingSrc = addressOf(incoming, t6)
            copyMemory(phiDest, incomingSrc, phi.type.sizeBytes(module))
        }
    }

    private sealed class PhiMoveSource {
        data class ValueSource(val value: Value) : PhiMoveSource()
        data object ScratchSource : PhiMoveSource()
    }

    private data class ScalarPhiMove(
        val phi: PhiNode,
        var source: PhiMoveSource,
    )

    private fun emitScalarPhiMoves(pending: MutableList<ScalarPhiMove>) {
        while (pending.isNotEmpty()) {
            pending.removeAll { move ->
                val destination = allocation[move.phi]
                val source = move.source.slot()
                destination != null && destination == source
            }
            if (pending.isEmpty()) return

            val nextIndex = pending.indexOfFirst { candidate ->
                val destination = allocation[candidate.phi]
                destination == null || pending.none { other ->
                    other !== candidate && other.source.slot() == destination
                }
            }

            if (nextIndex >= 0) {
                val move = pending.removeAt(nextIndex)
                emitScalarPhiMove(move)
                continue
            }

            val cycleSlot = allocation.getValue(pending.first().phi)
            readSlotToRegister(cycleSlot, t3, pending.first().phi.type.sizeBytes(module))
            for (move in pending) {
                if (move.source.slot() == cycleSlot) {
                    move.source = PhiMoveSource.ScratchSource
                }
            }
        }
    }

    private fun emitScalarPhiMove(move: ScalarPhiMove) {
        val loaded = when (val source = move.source) {
            is PhiMoveSource.ValueSource -> loadValue(source.value, t5)
            PhiMoveSource.ScratchSource -> t3
        }
        writeValue(move.phi, loaded)
    }

    private fun PhiMoveSource.slot(): SavableSlot? {
        return when (this) {
            is PhiMoveSource.ValueSource -> allocation[value]
            PhiMoveSource.ScratchSource -> null
        }
    }

    private fun lowerReturn(instruction: ReturnInst) {
        val returnValue = instruction.getReturnValue()
        if (returnValue != null && returnValue.type != VoidType) {
            val size = returnValue.type.sizeBytes(module)
            if (size <= registerBytes) {
                val reg = loadValue(returnValue, a0)
                if (reg != a0) asm.mv(a0, reg)
            } else {
                throw UnsupportedOperationException("Direct aggregate returns are not lowered yet in ${function.name}")
            }
        }
        emitEpilogue()
    }

    private fun lowerCall(instruction: CallInst) {
        val callerSavedTemps = callerSavedTemps(instruction)
        for ((register, temp) in callerSavedTemps) {
            storeRegister(register.toRv(), addressOfStack(temp, t6))
        }

        val directArgumentMoves = mutableListOf<CallArgumentMove>()
        for ((index, argument) in instruction.arguments.withIndex()) {
            val argumentSlot = allocation[argument]
            if (index < argumentRegisters.size &&
                argument.type.sizeBytes(module) <= registerBytes &&
                argumentSlot is SavableSlot.Register
            ) {
                directArgumentMoves.add(
                    CallArgumentMove(
                        index,
                        argumentRegisters[index],
                        CallArgumentMoveSource.RegisterSource(argumentSlot.physical.toRv()),
                    )
                )
                continue
            }

            val temp = frame.objectWithName(callArgumentTempName(index))
                ?: throw IllegalStateException("Missing call argument temp $index in ${function.name}")
            val value = if (argument.type.sizeBytes(module) <= registerBytes) {
                loadValue(argument, t5)
            } else {
                addressOf(argument, t5)
            }
            asm.mv(t3, value)
            storeRegister(t3, addressOfStack(temp, t6))
        }

        val directArgumentIndexes = directArgumentMoves.mapTo(mutableSetOf()) { it.index }
        emitCallArgumentMoves(directArgumentMoves)

        for (index in instruction.arguments.indices.take(argumentRegisters.size)) {
            if (index in directArgumentIndexes) continue
            val temp = frame.objectWithName(callArgumentTempName(index))
                ?: throw IllegalStateException("Missing call argument temp $index in ${function.name}")
            loadRegister(argumentRegisters[index], addressOfStack(temp, t6))
        }

        val stackArgumentCount = (instruction.arguments.size - argumentRegisters.size).coerceAtLeast(0)
        val stackArgumentBytes = alignUp(stackArgumentCount * registerBytes, 16)
        for (stackIndex in 0 until stackArgumentCount) {
            val argumentIndex = argumentRegisters.size + stackIndex
            val temp = frame.objectWithName(callArgumentTempName(argumentIndex))
                ?: throw IllegalStateException("Missing call argument temp $argumentIndex in ${function.name}")
            loadRegister(t4, addressOfStack(temp, t6))
            storeRegister(t4, stackArgumentAddress(stackIndex * registerBytes - stackArgumentBytes, t6))
        }

        adjustStack(-stackArgumentBytes)
        val callee = instruction.callee
        when (callee) {
            is Function -> asm.call(callee.asmName())
            else -> {
                val target = loadValue(callee, t5)
                asm.jalr(ra, target, 0)
            }
        }
        adjustStack(stackArgumentBytes)

        val returnScratch = if (instruction.producesValue()) t4 else null
        if (instruction.producesValue() && instruction in allocation) {
            asm.mv(returnScratch!!, a0)
        }

        for ((register, temp) in callerSavedTemps.asReversed()) {
            loadRegister(register.toRv(), addressOfStack(temp, t6))
        }

        if (instruction.producesValue() && instruction in allocation) {
            writeValue(instruction, returnScratch!!)
        }
    }

    private fun emitCallArgumentMoves(pending: MutableList<CallArgumentMove>) {
        while (pending.isNotEmpty()) {
            pending.removeAll { move -> move.destination == move.source.registerOrNull() }
            if (pending.isEmpty()) return

            val nextIndex = pending.indexOfFirst { candidate ->
                pending.none { other -> other !== candidate && other.source.registerOrNull() == candidate.destination }
            }

            if (nextIndex >= 0) {
                val move = pending.removeAt(nextIndex)
                val source = when (val moveSource = move.source) {
                    is CallArgumentMoveSource.RegisterSource -> moveSource.register
                    is CallArgumentMoveSource.TempSource -> loadRegisterScratch(moveSource.temp)
                }
                if (move.destination != source) {
                    asm.mv(move.destination, source)
                }
                continue
            }

            val cycleSource = pending.firstNotNullOf { it.source.registerOrNull() }
            val temp = resolveCallArgTemp(frame, pending.first().index)
            storeRegister(cycleSource, addressOfStack(temp, t6))
            for (move in pending) {
                if (move.source.registerOrNull() == cycleSource) {
                    move.source = CallArgumentMoveSource.TempSource(temp)
                }
            }
        }
    }

    private fun callerSavedTemps(call: CallInst): List<Pair<rusty.asm.utils.Register, PlacedStackObject>> {
        val liveOut = callLiveOut[call].orEmpty()
        return allocation
            .asSequence()
            .filter { (value, slot) ->
                value in liveOut && slot is SavableSlot.Register && slot.physical in callerSavedRegisterSet
            }
            .map { (_, slot) -> (slot as SavableSlot.Register).physical }
            .distinct()
            .sortedBy { it.id }
            .mapNotNull { register ->
                frame.objectWithName(register.callSaveTempName())?.let { register to it }
            }
            .toList()
    }

    private fun computeCallLiveOut(fn: Function): Map<CallInst, Set<Value>> {
        val blockLiveness = context.analysisManager.get(BlockLivenessAnalysis::class)
        // Only values allocated to caller-saved registers can need a save/restore around a call (the
        // sole consumer, callerSavedTemps, discards everything else), so track exactly those. Tracking
        // every live value made this O(calls * liveValues) — quadratic when many values are live across
        // many calls — even though at most a handful of caller-saved registers matter at any point.
        val trackedValues = linkedSetOf<Value>().apply {
            addAll(fn.parameters)
            for (block in fn.basicBlocks) {
                addAll(block.instructionsIncludingTerminator())
            }
        }.filterTo(linkedSetOf()) { value ->
            val slot = allocation[value]
            slot is SavableSlot.Register && slot.physical in callerSavedRegisterSet
        }
        val result = linkedMapOf<CallInst, Set<Value>>()

        fun addTrackedOperands(instruction: Instruction, destination: MutableSet<Value>) {
            if (instruction is PhiNode) return
            for (operand in instruction.getOperandsList()) {
                if (operand in trackedValues) {
                    destination.add(operand)
                }
            }
        }

        for (block in fn.basicBlocks) {
            val currentLive = LinkedHashSet<Value>(blockLiveness.ofBlock(block).second)
            for (instruction in block.instructionsIncludingTerminator().toList().asReversed()) {
                if (instruction is CallInst) {
                    val liveAcross = if (instruction in currentLive) {
                        LinkedHashSet(currentLive).also { it.remove(instruction) }
                    } else {
                        currentLive
                    }
                    result[instruction] = if (liveAcross.isEmpty()) emptySet() else LinkedHashSet(liveAcross)
                }

                if (instruction in trackedValues) {
                    currentLive.remove(instruction)
                }
                addTrackedOperands(instruction, currentLive)
            }
        }

        return result
    }

    private fun lowerCast(instruction: CastInst) {
        val source = loadValue(instruction.value, t5)
        val dst = valueDestinationRegister(instruction, t5)

        when (instruction) {
            is BitcastInst, is PtrToIntInst -> {
                if (dst != source) asm.mv(dst, source)
            }
            is TruncInst -> {
                val bits = instruction.getDestinationBitWidth()
                if (bits >= registerBytes * 8) {
                    if (dst != source) asm.mv(dst, source)
                } else {
                    val shift = registerBytes * 8 - bits
                    asm.slli(dst, source, shift)
                    asm.srli(dst, dst, shift)
                }
            }
            is ZExtInst -> {
                val bits = instruction.getSourceBitWidth()
                when {
                    bits >= registerBytes * 8 -> if (dst != source) asm.mv(dst, source)
                    bits <= 0 -> asm.li(dst, 0)
                    else -> {
                        val shift = registerBytes * 8 - bits
                        asm.slli(dst, source, shift)
                        asm.srli(dst, dst, shift)
                    }
                }
            }
            is SExtInst -> {
                val bits = instruction.getSourceBitWidth()
                if (bits >= registerBytes * 8) {
                    if (dst != source) asm.mv(dst, source)
                } else {
                    val shift = registerBytes * 8 - bits
                    asm.slli(dst, source, shift)
                    asm.srai(dst, dst, shift)
                }
            }
            else -> throw UnsupportedOperationException(
                "Cannot lower cast ${instruction::class.simpleName} in ${function.name}"
            )
        }

        writeValue(instruction, dst)
    }

    private fun valueDestinationRegister(value: Value, fallback: RvRegister): RvRegister {
        return (allocation[value] as? SavableSlot.Register)?.physical?.toRv() ?: fallback
    }

    private fun loadValue(value: Value, scratch: RvRegister): RvRegister {
        when (value) {
            is IntConstant -> {
                loadImmediate(scratch, value.signExtendedI32Value())
                return scratch
            }
            is NullPointerConstant -> {
                loadImmediate(scratch, 0)
                return scratch
            }
            is GlobalVariable -> {
                asm.la(scratch, value.asmName())
                return scratch
            }
            is Function -> {
                asm.la(scratch, value.asmName())
                return scratch
            }
        }

        return when (val slot = allocation[value]) {
            is SavableSlot.Register -> slot.physical.toRv()
            is SavableSlot.Stack -> {
                val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                    ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                loadSized(scratch, addressOfStack(obj, scratch), value.type.sizeBytes(module))
            }
            null -> throw IllegalStateException("No register allocation for ${value.getIdentifier()} in ${function.name}")
        }
    }

    private fun writeValue(value: Value, source: RvRegister) {
        when (val slot = allocation[value]) {
            is SavableSlot.Register -> {
                val destination = slot.physical.toRv()
                if (destination != source) asm.mv(destination, source)
            }
            is SavableSlot.Stack -> {
                val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                    ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                val addressScratch = if (source == t4) t6 else t4
                val safeSource = if (source == t6) { asm.mv(t3, source); t3 } else source
                storeSized(safeSource, addressOfStack(obj, addressScratch), value.type.sizeBytes(module))
            }
            null -> Unit
        }
    }

    private fun readSlotToRegister(slot: SavableSlot, destination: RvRegister, sizeBytes: Int): RvRegister {
        when (slot) {
            is SavableSlot.Register -> {
                val source = slot.physical.toRv()
                if (destination != source) asm.mv(destination, source)
            }
            is SavableSlot.Stack -> {
                val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                    ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                loadSized(destination, addressOfStack(obj, t6), sizeBytes)
            }
        }
        return destination
    }

    private fun addressOf(value: Value, scratch: RvRegister): RvRegister {
        return when (value) {
            is AllocaInst -> addressOfStack(allocaObjects.getValue(value), scratch)
            is GlobalVariable -> {
                asm.la(scratch, value.asmName())
                scratch
            }
            else -> {
                val slot = allocation[value]
                if (slot is SavableSlot.Stack && value.type.sizeBytes(module) > registerBytes) {
                    val obj = frame.objectWithStackSlotId(slot.stackSlotId)
                        ?: throw IllegalStateException("Missing stack slot ${slot.stackSlotId} in ${function.name}")
                    addressOfStack(obj, scratch)
                } else {
                    loadValue(value, scratch)
                }
            }
        }
    }

    private fun addressOfStack(obj: PlacedStackObject, destination: RvRegister): RvRegister {
        addImmediate(destination, sp, obj.offsetFromSp)
        return destination
    }

    private fun stackArgumentAddress(offsetFromSp: Int, destination: RvRegister): RvRegister {
        addImmediate(destination, sp, offsetFromSp)
        return destination
    }

    private fun loadSized(destination: RvRegister, address: RvRegister, sizeBytes: Int): RvRegister {
        when (sizeBytes) {
            1 -> asm.lbu(destination, mem(0, address))
            2 -> asm.lhu(destination, mem(0, address))
            3, 4 -> asm.lw(destination, mem(0, address))
            5, 6, 7, 8 -> asm.emit("ld", destination, mem(0, address))
            else -> throw UnsupportedOperationException("Cannot scalar-load $sizeBytes bytes in ${function.name}")
        }
        return destination
    }

    private fun storeSized(source: RvRegister, address: RvRegister, sizeBytes: Int) {
        when (sizeBytes) {
            1 -> asm.sb(source, mem(0, address))
            2 -> asm.sh(source, mem(0, address))
            3, 4 -> asm.sw(source, mem(0, address))
            5, 6, 7, 8 -> asm.emit("sd", source, mem(0, address))
            else -> throw UnsupportedOperationException("Cannot scalar-store $sizeBytes bytes in ${function.name}")
        }
    }

    private fun loadRegister(destination: RvRegister, address: RvRegister) {
        if (registerBytes == 8) {
            asm.emit("ld", destination, mem(0, address))
        } else {
            asm.lw(destination, mem(0, address))
        }
    }

    private fun storeRegister(source: RvRegister, address: RvRegister) {
        if (registerBytes == 8) {
            asm.emit("sd", source, mem(0, address))
        } else {
            asm.sw(source, mem(0, address))
        }
    }

    private fun loadImmediate(destination: RvRegister, value: Long) {
        if (value in Int.MIN_VALUE..Int.MAX_VALUE) {
            asm.li(destination, value.toInt())
        } else {
            asm.emit("li", destination, expr(value.toString()))
        }
    }

    private fun IntConstant.signExtendedI32Value(): Long {
        if (type.bitWidth != 32 || registerBytes <= 4) return value
        return value.toInt().toLong()
    }

    private fun copyMemory(destination: RvRegister, source: RvRegister, sizeBytes: Int) {
        if (sizeBytes == 0 || destination == source) return

        moveCopyPointers(destination, source)

        var remaining = sizeBytes
        val chunkBytes = 2040 / registerBytes * registerBytes
        while (remaining > 0) {
            val chunkSize = kotlin.math.min(remaining, chunkBytes)
            var chunk = 0
            while (chunk + registerBytes <= chunkSize) {
                if (registerBytes == 8) {
                    asm.emit("ld", t4, mem(chunk, t6))
                    asm.emit("sd", t4, mem(chunk, t3))
                } else {
                    asm.lw(t4, mem(chunk, t6))
                    asm.sw(t4, mem(chunk, t3))
                }
                chunk += registerBytes
            }
            while (chunk < chunkSize) {
                asm.lbu(t4, mem(chunk, t6))
                asm.sb(t4, mem(chunk, t3))
                chunk += 1
            }

            remaining -= chunkSize
            if (remaining > 0) {
                addImmediate(t3, t3, chunkSize)
                addImmediate(t6, t6, chunkSize)
            }
        }
    }

    private fun moveCopyPointers(destination: RvRegister, source: RvRegister) {
        when {
            destination == t3 && source == t6 -> Unit
            destination == t6 && source == t3 -> {
                asm.mv(t5, source)
                asm.mv(t3, destination)
                asm.mv(t6, t5)
            }
            source == t3 -> {
                asm.mv(t6, source)
                if (destination != t3) asm.mv(t3, destination)
            }
            destination == t6 -> {
                asm.mv(t3, destination)
                if (source != t6) asm.mv(t6, source)
            }
            else -> {
                if (destination != t3) asm.mv(t3, destination)
                if (source != t6) asm.mv(t6, source)
            }
        }
    }

    private fun addImmediate(destination: RvRegister, base: RvRegister, immediate: Int) {
        if (immediate == 0) {
            if (destination != base) asm.mv(destination, base)
        } else if (immediate in -2048..2047) {
            asm.addi(destination, base, immediate)
        } else {
            val scratch = when {
                t6 != destination && t6 != base -> t6
                t5 != destination && t5 != base -> t5
                else -> t4
            }
            asm.li(scratch, immediate)
            asm.add(destination, base, scratch)
        }
    }

    private fun structFieldOffset(fields: List<Type>, packed: Boolean, field: Int): Int {
        require(field in fields.indices) { "Struct field $field out of bounds" }
        var offset = 0
        for (index in 0 until field) {
            val layout = fields[index].computeLayout(module, pointerWidthBits)
            val align = if (packed) 1 else layout.alignment
            offset = alignUp(offset, align)
            offset += layout.sizeInBytes.toIntExact("struct field")
        }
        val fieldAlign = if (packed) 1 else fields[field].computeLayout(module, pointerWidthBits).alignment
        return alignUp(offset, fieldAlign)
    }
}
