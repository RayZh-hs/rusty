package space.norb.llvm.builder

import space.norb.llvm.structure.Module
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Function
import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.values.Metadata
import space.norb.llvm.types.FunctionType
import space.norb.llvm.types.VoidType
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.FloatingPointType
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.terminators.ReturnInst
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.instructions.terminators.SwitchInst
import space.norb.llvm.instructions.terminators.UnreachableInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.binary.SubInst
import space.norb.llvm.instructions.binary.MulInst
import space.norb.llvm.instructions.binary.AndInst
import space.norb.llvm.instructions.binary.OrInst
import space.norb.llvm.instructions.binary.XorInst
import space.norb.llvm.instructions.binary.LShrInst
import space.norb.llvm.instructions.binary.AShrInst
import space.norb.llvm.instructions.binary.ShlInst
import space.norb.llvm.instructions.binary.SDivInst
import space.norb.llvm.instructions.binary.UDivInst
import space.norb.llvm.instructions.binary.URemInst
import space.norb.llvm.instructions.binary.FAddInst
import space.norb.llvm.instructions.binary.FSubInst
import space.norb.llvm.instructions.binary.FMulInst
import space.norb.llvm.instructions.binary.FDivInst
import space.norb.llvm.instructions.binary.FRemInst
import space.norb.llvm.instructions.binary.SRemInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.FCmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.casts.BitcastInst
import space.norb.llvm.instructions.casts.PtrToIntInst
import space.norb.llvm.instructions.casts.SExtInst
import space.norb.llvm.instructions.casts.ZExtInst
import space.norb.llvm.instructions.casts.TruncInst
import space.norb.llvm.enums.IcmpPredicate
import space.norb.llvm.enums.FcmpPredicate
import space.norb.llvm.utils.Renamer

/**
 * Builder for constructing LLVM IR.
 */
class IRBuilder(val module: Module) {
    private var currentBlock: BasicBlock? = null
    private var insertionPoint: MutableListIterator<Instruction>? = null
    private val defaultMetadata = mutableMapOf<String, Metadata>()
    
    // Positioning methods
    fun positionAtEnd(block: BasicBlock) {
        currentBlock = block
        insertionPoint = block.instructions.listIterator(block.instructions.size)
    }

    fun positionAfter(block: BasicBlock, index: Int) {
        currentBlock = block
        insertionPoint = block.instructions.listIterator(index + 1)
    }

    fun positionBefore(block: BasicBlock, index: Int) {
        currentBlock = block
        insertionPoint = block.instructions.listIterator(index)
    }

    fun clearInsertionPoint() {
        currentBlock = null
        insertionPoint = null
    }

    // Metadata management
    /**
     * Sets a default metadata attachment for all subsequently created instructions.
     * @param kind The kind of metadata (e.g., "dbg", "tbaa")
     * @param metadata The metadata to attach, or null to remove
     */
    fun setDefaultMetadata(kind: String, metadata: Metadata?) {
        if (metadata == null) {
            defaultMetadata.remove(kind)
        } else {
            defaultMetadata[kind] = metadata
        }
    }
    
    /**
     * Sets the current debug location for subsequently created instructions.
     * @param location The metadata node representing the debug location
     */
    fun setCurrentDebugLocation(location: Metadata?) {
        setDefaultMetadata("dbg", location)
    }

    // Comments and annotations
    fun insertComment(text: String, name: String? = null): CommentAttachment {
        val rename = name ?: Renamer.another()
        val comment = CommentAttachment(rename, text)
        return insertInstruction(comment) as CommentAttachment
    }
    
    private fun insertInstruction(instruction: Instruction): Instruction {
        val block = currentBlock ?: throw IllegalStateException("No insertion point set")
        
        // Attach default metadata
        defaultMetadata.forEach { (kind, md) ->
            instruction.setMetadata(kind, md)
        }

        // Check if this is a terminator instruction
        if (instruction is space.norb.llvm.instructions.base.TerminatorInst) {
            // Replace existing terminator if any
            block.terminator?.let { existing ->
                val index = block.instructions.indexOf(existing)
                if (index >= 0) {
                    block.instructions[index] = instruction
                } else {
                    block.instructions.add(instruction)
                }
            } ?: run {
                block.instructions.add(instruction)
            }
            block.terminator = instruction
        } else {
            // Insert at current insertion point or at end
            insertionPoint?.let { iterator ->
                iterator.add(instruction)
            } ?: run {
                block.instructions.add(instruction)
            }
        }

        return instruction
    }
    
    fun createFunction(name: String?, type: FunctionType): Function {
        return Function(name, type, module)
    }
    
    /**
     * Create a function with custom parameter names.
     * This overload merges the parameter names into the type and delegates to the constructor.
     */
    fun createFunction(name: String?, type: FunctionType, paramNames: List<String>): Function {
        val updatedType = type.copy(paramNames = paramNames)
        return Function(name, updatedType, module)
    }
    
    /**
     * Convenience overload to create a function with return type, parameter types, and optional parameter names.
     */
    fun createFunction(
        name: String?,
        returnType: Type,
        paramTypes: List<Type>,
        paramNames: List<String>? = null,
        isVarArg: Boolean = false
    ): Function {
        val functionType = FunctionType(returnType, paramTypes, isVarArg, paramNames)
        return Function(name, functionType, module)
    }
    
    @Deprecated("Use Function.insertBasicBlock instead", ReplaceWith("function.insertBasicBlock(name)"))
    fun insertBasicBlock(name: String?, function: Function): BasicBlock {
        val block = BasicBlock(name, function)
        // Automatically add the block to the function's basic blocks list
        if (function.basicBlocks.isEmpty()) {
            function.entryBlock = block
        }
        function.basicBlocks.add(block)
        return block
    }
    
    // Terminator methods
    fun insertRet(value: Value?): ReturnInst {
        val ret = ReturnInst("ret", value?.type ?: VoidType, value)
        return insertInstruction(ret) as ReturnInst
    }
    
    fun insertRetVoid(): ReturnInst {
        return insertRet(null)
    }
    
    fun insertBr(target: BasicBlock): BranchInst {
        val br = BranchInst.createUnconditional("br", VoidType, target)
        return insertInstruction(br) as BranchInst
    }
    
    fun insertCondBr(condition: Value, trueTarget: BasicBlock, falseTarget: BasicBlock): BranchInst {
        val br = BranchInst.createConditional("condbr", VoidType, condition, trueTarget, falseTarget)
        return insertInstruction(br) as BranchInst
    }
    
    fun insertSwitch(condition: Value, defaultDest: BasicBlock, cases: List<Pair<Value, BasicBlock>>, name: String? = null): SwitchInst {
        val rename = name ?: Renamer.another()
        val sw = SwitchInst.create(rename, VoidType, condition, defaultDest, cases)
        return insertInstruction(sw) as SwitchInst
    }
    
    fun insertUnreachable(): UnreachableInst {
        val unreachable = UnreachableInst.create("unreachable", VoidType)
        return insertInstruction(unreachable) as UnreachableInst
    }
    
    // Binary operations
    fun insertAdd(lhs: Value, rhs: Value, name: String? = null): AddInst {
        val rename = name ?: Renamer.another()
        val add = AddInst(rename, lhs.type, lhs, rhs)
        return insertInstruction(add) as AddInst
    }
    
    fun insertSub(lhs: Value, rhs: Value, name: String? = null): SubInst {
        val rename = name ?: Renamer.another()
        val sub = SubInst.create(rename, lhs, rhs)
        return insertInstruction(sub) as SubInst
    }
    
    fun insertMul(lhs: Value, rhs: Value, name: String? = null): MulInst {
        val rename = name ?: Renamer.another()
        val mul = MulInst.create(rename, lhs, rhs)
        return insertInstruction(mul) as MulInst
    }
    
    fun insertAnd(lhs: Value, rhs: Value, name: String? = null): AndInst {
        val rename = name ?: Renamer.another()
        val and = AndInst.create(rename, lhs, rhs)
        return insertInstruction(and) as AndInst
    }
    
    fun insertOr(lhs: Value, rhs: Value, name: String? = null): OrInst {
        val rename = name ?: Renamer.another()
        val or = OrInst.create(rename, lhs, rhs)
        return insertInstruction(or) as OrInst
    }

    fun insertXor(lhs: Value, rhs: Value, name: String? = null): XorInst {
        val rename = name ?: Renamer.another()
        val xor = XorInst.create(rename, lhs, rhs)
        return insertInstruction(xor) as XorInst
    }

    fun insertLShr(lhs: Value, rhs: Value, name: String? = null): LShrInst {
        val rename = name ?: Renamer.another()
        val lshr = LShrInst.create(rename, lhs, rhs)
        return insertInstruction(lshr) as LShrInst
    }

    fun insertAShr(lhs: Value, rhs: Value, name: String? = null): AShrInst {
        val rename = name ?: Renamer.another()
        val ashr = AShrInst.create(rename, lhs, rhs)
        return insertInstruction(ashr) as AShrInst
    }

    fun insertShl(lhs: Value, rhs: Value, name: String? = null): ShlInst {
        val rename = name ?: Renamer.another()
        val shl = ShlInst.create(rename, lhs, rhs)
        return insertInstruction(shl) as ShlInst
    }
    
    fun insertSDiv(lhs: Value, rhs: Value, name: String? = null): SDivInst {
        val rename = name ?: Renamer.another()
        val sdiv = SDivInst.create(rename, lhs, rhs)
        return insertInstruction(sdiv) as SDivInst
    }

    fun insertUDiv(lhs: Value, rhs: Value, name: String? = null): UDivInst {
        val rename = name ?: Renamer.another()
        val udiv = UDivInst.create(rename, lhs, rhs)
        return insertInstruction(udiv) as UDivInst
    }
    
    fun insertURem(lhs: Value, rhs: Value, name: String? = null): URemInst {
        val rename = name ?: Renamer.another()
        val urem = URemInst.create(rename, lhs, rhs)
        return insertInstruction(urem) as URemInst
    }
    
    fun insertSRem(lhs: Value, rhs: Value, name: String? = null): SRemInst {
        val rename = name ?: Renamer.another()
        val srem = SRemInst.create(rename, lhs, rhs)
        return insertInstruction(srem) as SRemInst
    }

    // Floating-point operations
    fun insertFAdd(lhs: Value, rhs: Value, name: String? = null): FAddInst {
        val rename = name ?: Renamer.another()
        val fadd = FAddInst.create(rename, lhs, rhs)
        return insertInstruction(fadd) as FAddInst
    }

    fun insertFSub(lhs: Value, rhs: Value, name: String? = null): FSubInst {
        val rename = name ?: Renamer.another()
        val fsub = FSubInst.create(rename, lhs, rhs)
        return insertInstruction(fsub) as FSubInst
    }

    fun insertFMul(lhs: Value, rhs: Value, name: String? = null): FMulInst {
        val rename = name ?: Renamer.another()
        val fmul = FMulInst.create(rename, lhs, rhs)
        return insertInstruction(fmul) as FMulInst
    }

    fun insertFDiv(lhs: Value, rhs: Value, name: String? = null): FDivInst {
        val rename = name ?: Renamer.another()
        val fdiv = FDivInst.create(rename, lhs, rhs)
        return insertInstruction(fdiv) as FDivInst
    }

    fun insertFRem(lhs: Value, rhs: Value, name: String? = null): FRemInst {
        val rename = name ?: Renamer.another()
        val frem = FRemInst.create(rename, lhs, rhs)
        return insertInstruction(frem) as FRemInst
    }
    
    // Cast operations
    fun insertBitcast(value: Value, destType: Type, name: String? = null): BitcastInst {
        val rename = name ?: Renamer.another()
        val bitcast = BitcastInst.create(rename, value, destType)
        return insertInstruction(bitcast) as BitcastInst
    }
    
    fun insertSExt(value: Value, destType: IntegerType, name: String? = null): SExtInst {
        val rename = name ?: Renamer.another()
        val sext = SExtInst.create(rename, value, destType)
        return insertInstruction(sext) as SExtInst
    }
    
    fun insertZExt(value: Value, destType: IntegerType, name: String? = null): ZExtInst {
        val rename = name ?: Renamer.another()
        val zext = ZExtInst.create(rename, value, destType)
        return insertInstruction(zext) as ZExtInst
    }
    
    fun insertTrunc(value: Value, destType: IntegerType, name: String? = null): TruncInst {
        val rename = name ?: Renamer.another()
        val trunc = TruncInst.create(rename, value, destType)
        return insertInstruction(trunc) as TruncInst
    }

    fun insertPtrToInt(value: Value, destType: IntegerType, name: String? = null): PtrToIntInst {
        val rename = name ?: Renamer.another()
        val ptrToInt = PtrToIntInst.create(rename, value, destType)
        return insertInstruction(ptrToInt) as PtrToIntInst
    }
    
    // Memory operations
    fun insertAlloca(allocatedType: Type, name: String? = null): AllocaInst {
        val rename = name ?: Renamer.another()
        val alloca = AllocaInst(rename, allocatedType)
        return insertInstruction(alloca) as AllocaInst
    }

    fun insertLoad(loadedType: Type, address: Value, name: String? = null): LoadInst {
        val rename = name ?: Renamer.another()
        val load = LoadInst(rename, loadedType, address)
        return insertInstruction(load) as LoadInst
    }
    
    fun insertStore(value: Value, address: Value): StoreInst {
        val store = StoreInst("store", value.type, value, address)
        return insertInstruction(store) as StoreInst
    }
    
    fun insertGep(elementType: Type, address: Value, indices: List<Value>, name: String? = null): GetElementPtrInst {
        val rename = name ?: Renamer.another()
        val gep = GetElementPtrInst(rename, elementType, address, indices)
        return insertInstruction(gep) as GetElementPtrInst
    }

    // Other operations
    fun insertCall(function: Function, args: List<Value>, name: String? = null): CallInst {
        val call = if (function.returnType == VoidType) {
            require(name.isNullOrEmpty()) { "Calls to void-returning functions cannot have result names" }
            CallInst.createDirectCall(null, function, args)
        } else {
            val rename = name ?: Renamer.another()
            CallInst.createDirectCall(rename, function, args)
        }
        return insertInstruction(call) as CallInst
    }

    fun insertVoidCall(function: Function, args: List<Value>): CallInst {
        require(function.returnType == VoidType) { "Function must have void return type for insertVoidCall" }
        return insertCall(function, args)
    }
    
    fun insertIndirectCall(funcPtr: Value, args: List<Value>, returnType: Type, name: String? = null): CallInst {
        val call = if (returnType == VoidType) {
            require(name.isNullOrEmpty()) { "Calls to void-returning functions cannot have result names" }
            CallInst.createIndirectCall(null, returnType, funcPtr, args)
        } else {
            val rename = name ?: Renamer.another()
            CallInst.createIndirectCall(rename, returnType, funcPtr, args)
        }
        return insertInstruction(call) as CallInst
    }

    fun insertIndirectVoidCall(funcPtr: Value, args: List<Value>): CallInst {
        return insertIndirectCall(funcPtr, args, VoidType)
    }
    
    fun insertICmp(pred: IcmpPredicate, lhs: Value, rhs: Value, name: String? = null): ICmpInst {
        val rename = name ?: Renamer.another()
        val icmp = ICmpInst.create(rename, pred, lhs, rhs)
        return insertInstruction(icmp) as ICmpInst
    }

    fun insertFCmp(pred: FcmpPredicate, lhs: Value, rhs: Value, name: String? = null): FCmpInst {
        val rename = name ?: Renamer.another()
        val fcmp = FCmpInst.create(rename, pred, lhs, rhs)
        return insertInstruction(fcmp) as FCmpInst
    }
    
    fun insertPhi(type: Type, incomingValues: List<Pair<Value, BasicBlock>>, name: String? = null): PhiNode {
        val rename = name ?: Renamer.another()
        val phi = PhiNode.create(rename, type, incomingValues.map { Pair(it.first, it.second) })
        return insertInstruction(phi) as PhiNode
    }
    
    // Convenience methods for common operations
    fun insertNot(value: Value, name: String? = null): XorInst {
        val negOne = when {
            value.type.isIntegerType() -> {
                BuilderUtils.getIntConstant(-1, value.type as IntegerType)
            }
            else -> throw IllegalArgumentException("Not operation only supported for integer types")
        }
        return insertXor(value, negOne, name)
    }
    
    fun insertNeg(value: Value, name: String? = null): SubInst {
        val zero = when {
            value.type.isIntegerType() -> BuilderUtils.getIntConstant(0, value.type as IntegerType)
            value.type.isFloatingPointType() -> BuilderUtils.getFloatConstant(0.0, value.type as FloatingPointType)
            else -> throw IllegalArgumentException("Negation not supported for type: ${value.type}")
        }
        return insertSub(zero, value, name)
    }
}
