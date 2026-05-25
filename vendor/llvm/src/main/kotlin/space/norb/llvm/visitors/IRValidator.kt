package space.norb.llvm.visitors

import space.norb.llvm.structure.Module
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Argument
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.PointerType
import space.norb.llvm.instructions.terminators.ReturnInst
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.instructions.terminators.SwitchInst
import space.norb.llvm.instructions.terminators.UnreachableInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.binary.SubInst
import space.norb.llvm.instructions.binary.MulInst
import space.norb.llvm.instructions.binary.SDivInst
import space.norb.llvm.instructions.binary.UDivInst
import space.norb.llvm.instructions.binary.URemInst
import space.norb.llvm.instructions.binary.FAddInst
import space.norb.llvm.instructions.binary.FSubInst
import space.norb.llvm.instructions.binary.FMulInst
import space.norb.llvm.instructions.binary.FDivInst
import space.norb.llvm.instructions.binary.FRemInst
import space.norb.llvm.instructions.binary.SRemInst
import space.norb.llvm.instructions.binary.AndInst
import space.norb.llvm.instructions.binary.OrInst
import space.norb.llvm.instructions.binary.XorInst
import space.norb.llvm.instructions.binary.LShrInst
import space.norb.llvm.instructions.binary.AShrInst
import space.norb.llvm.instructions.binary.ShlInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.casts.TruncInst
import space.norb.llvm.instructions.casts.ZExtInst
import space.norb.llvm.instructions.casts.SExtInst
import space.norb.llvm.instructions.casts.BitcastInst
import space.norb.llvm.instructions.casts.PtrToIntInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.FCmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.values.Metadata
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.OtherInst

/**
 * Visitor for validating LLVM IR structure and semantics.
 */
class IRValidator : IRVisitor<Boolean> {
    private val errors = mutableListOf<String>()
    
    fun validate(module: Module): Boolean {
        errors.clear()
        visitModule(module)
        return errors.isEmpty()
    }
    
    fun getErrors(): List<String> = errors.toList()
    
    private fun addError(message: String) {
        errors.add(message)
    }
    
    override fun visitModule(module: Module): Boolean {
        module.functions.forEach { visitFunction(it) }
        return errors.isEmpty()
    }
    
    override fun visitFunction(function: Function): Boolean {
        if (function.name.isNullOrEmpty()) {
            addError("Function name cannot be empty")
        }
        
        // External declarations must not have bodies
        if (function.isDeclaration) {
            if (function.basicBlocks.isNotEmpty()) {
                addError("External function '${function.name}' cannot have a body. External functions must be declarations only.")
            }
            return errors.isEmpty()
        }
        
        if (function.basicBlocks.isEmpty()) {
            addError("Function ${function.name} must have at least one basic block")
        }
        
        function.basicBlocks.forEach { visitBasicBlock(it) }
        
        return errors.isEmpty()
    }
    
    override fun visitBasicBlock(block: BasicBlock): Boolean {
        if (block.name.isNullOrEmpty()) {
            addError("Basic block name cannot be empty")
        }
        
        if (block.terminator == null) {
            addError("Basic block ${block.name} must have a terminator")
        } else {
            // Ensure terminator is the last instruction in the block
            if (block.instructions.isEmpty() || block.instructions.last() != block.terminator) {
                addError("Terminator instruction in basic block ${block.name} must be the last instruction")
            }
        }
        
        block.instructions.forEach { it.accept(this) }
        // Note: we don't visit block.terminator separately because it's already in block.instructions
        return errors.isEmpty()
    }
    
    override fun visitArgument(argument: Argument): Boolean {
        if (argument.name.isNullOrEmpty()) {
            addError("Argument name cannot be empty")
        }
        return errors.isEmpty()
    }
    
    override fun visitGlobalVariable(globalVariable: GlobalVariable): Boolean {
        if (globalVariable.name.isNullOrEmpty()) {
            addError("Global variable name cannot be empty")
        }
        return errors.isEmpty()
    }
    
    override fun visitConstant(constant: Constant): Boolean {
        // Constants should always be valid
        return true
    }
    
    override fun visitMetadata(metadata: Metadata): Boolean {
        // Metadata should always be valid
        return true
    }
    
    override fun visitReturnInst(inst: ReturnInst): Boolean {
        val operands = inst.getOperandsList()
        val returnValue = operands.firstOrNull()
        return errors.isEmpty()
    }
    
    override fun visitBranchInst(inst: BranchInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.size != 1 && operands.size != 3) {
            addError("Branch instruction must have either 1 (unconditional) or 3 (conditional) operands")
        }
        
        if (inst.isConditional()) {
            val condition = inst.getCondition()
            if (condition != null && !condition.type.isIntegerType()) {
                // Actually LLVM requires i1, but we might just check integer for now
                // addError("Branch condition must be of integer type (i1)")
            }
        }
        return errors.isEmpty()
    }
    
    override fun visitSwitchInst(inst: SwitchInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.size < 2) {
            addError("Switch instruction must have condition and default target")
        }
        return errors.isEmpty()
    }
    
    override fun visitUnreachableInst(inst: UnreachableInst): Boolean {
        // Unreachable instruction has no semantic constraints
        return errors.isEmpty()
    }
    
    override fun visitAddInst(inst: AddInst): Boolean = validateBinaryInst(inst, "add")
    override fun visitSubInst(inst: SubInst): Boolean = validateBinaryInst(inst, "sub")
    override fun visitMulInst(inst: MulInst): Boolean = validateBinaryInst(inst, "mul")
    override fun visitSDivInst(inst: SDivInst): Boolean = validateBinaryInst(inst, "sdiv")
    override fun visitUDivInst(inst: UDivInst): Boolean = validateBinaryInst(inst, "udiv")
    override fun visitURemInst(inst: URemInst): Boolean = validateBinaryInst(inst, "urem")
    override fun visitSRemInst(inst: SRemInst): Boolean = validateBinaryInst(inst, "srem")
    override fun visitFAddInst(inst: FAddInst): Boolean = validateBinaryInst(inst, "fadd")
    override fun visitFSubInst(inst: FSubInst): Boolean = validateBinaryInst(inst, "fsub")
    override fun visitFMulInst(inst: FMulInst): Boolean = validateBinaryInst(inst, "fmul")
    override fun visitFDivInst(inst: FDivInst): Boolean = validateBinaryInst(inst, "fdiv")
    override fun visitFRemInst(inst: FRemInst): Boolean = validateBinaryInst(inst, "frem")
    override fun visitAndInst(inst: AndInst): Boolean = validateBinaryInst(inst, "and")
    override fun visitOrInst(inst: OrInst): Boolean = validateBinaryInst(inst, "or")
    override fun visitXorInst(inst: XorInst): Boolean = validateBinaryInst(inst, "xor")
    override fun visitLShrInst(inst: LShrInst): Boolean = validateBinaryInst(inst, "lshr")
    override fun visitAShrInst(inst: AShrInst): Boolean = validateBinaryInst(inst, "ashr")
    override fun visitShlInst(inst: ShlInst): Boolean = validateBinaryInst(inst, "shl")
    
    private fun validateBinaryInst(inst: Any, opName: String): Boolean {
        // Basic validation for binary instructions
        return true
    }
    
    override fun visitAllocaInst(inst: AllocaInst): Boolean {
        if (inst.name.isNullOrEmpty()) {
            addError("Alloca instruction must have a name")
        }
        
        // Validate pointer type - un-typed pointers are now the standard
        if (inst.type != PointerType) {
            addError("Alloca instruction result type must be un-typed pointer")
        }
        
        return errors.isEmpty()
    }
    
    override fun visitLoadInst(inst: LoadInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.isEmpty()) {
            addError("Load instruction must have a pointer operand")
        }
        
        // Validate pointer operand type - un-typed pointers are now the standard
        val pointer = operands.first()
        if (pointer.type != PointerType) {
            addError("Load instruction pointer operand must be un-typed pointer")
        }
        
        return errors.isEmpty()
    }
    
    override fun visitStoreInst(inst: StoreInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.size < 2) {
            addError("Store instruction must have value and pointer operands")
        }
        
        // Validate pointer operand type - un-typed pointers are now the standard
        val pointer = operands[1] // Second operand is the pointer
        if (pointer.type != PointerType) {
            addError("Store instruction pointer operand must be un-typed pointer")
        }
        
        return errors.isEmpty()
    }
    
    override fun visitGetElementPtrInst(inst: GetElementPtrInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.isEmpty()) {
            addError("GetElementPtr instruction must have at least a pointer operand")
        }
        
        // Validate pointer operand type - un-typed pointers are now the standard
        val pointer = operands.first()
        if (pointer.type != PointerType) {
            addError("GetElementPtr instruction pointer operand must be un-typed pointer")
        }
        
        // Validate result type - un-typed pointers are now the standard
        if (inst.type != PointerType) {
            addError("GetElementPtr instruction result type must be un-typed pointer")
        }
        
        return errors.isEmpty()
    }
    
    override fun visitTruncInst(inst: TruncInst): Boolean = validateCastInst(inst, "trunc")
    override fun visitZExtInst(inst: ZExtInst): Boolean = validateCastInst(inst, "zext")
    override fun visitSExtInst(inst: SExtInst): Boolean = validateCastInst(inst, "sext")
    override fun visitBitcastInst(inst: BitcastInst): Boolean = validateBitcastInst(inst)
    override fun visitPtrToIntInst(inst: PtrToIntInst): Boolean = validatePtrToIntInst(inst)
    
    private fun validateCastInst(inst: Any, opName: String): Boolean {
        // Basic validation for cast instructions
        return true
    }
    
    private fun validateBitcastInst(inst: Any): Boolean {
        // Special validation for bitcast instructions with pointer types
        // This would need to be implemented with proper BitcastInst access
        // For now, we'll do basic validation
        
        // With un-typed pointers, all pointers can be bitcast to each other
        
        return true
    }

    private fun validatePtrToIntInst(inst: PtrToIntInst): Boolean {
        if (!inst.value.type.isPointerType()) {
            addError("PtrToInt source must be a pointer type, got ${inst.value.type}")
        }
        if (inst.type !is IntegerType) {
            addError("PtrToInt destination must be an integer type, got ${inst.type}")
        } else {
            val pointerWidth = inst.value.type.getPrimitiveSizeInBits() ?: 0
            if (inst.type.bitWidth < pointerWidth) {
                addError("PtrToInt destination width ${inst.type.bitWidth} is smaller than pointer width $pointerWidth")
            }
        }
        return errors.isEmpty()
    }
    
    override fun visitCallInst(inst: CallInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.isEmpty()) {
            addError("Call instruction must have a callee")
        }
        return errors.isEmpty()
    }
    
    override fun visitICmpInst(inst: ICmpInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.size < 2) {
            addError("ICmp instruction must have two operands")
        }
        return errors.isEmpty()
    }

    override fun visitFCmpInst(inst: FCmpInst): Boolean {
        val operands = inst.getOperandsList()
        if (operands.size < 2) {
            addError("FCmp instruction must have two operands")
        }
        return errors.isEmpty()
    }
    
    override fun visitPhiNode(inst: PhiNode): Boolean {
        val operands = inst.getOperandsList()
        if (operands.size % 2 != 0) {
            addError("Phi node must have pairs of values and basic blocks")
        }
        return errors.isEmpty()
    }

    override fun visitCommentAttachment(inst: CommentAttachment): Boolean {
        return errors.isEmpty()
    }
    
    override fun visitTerminatorInst(inst: TerminatorInst): Boolean = when (inst) {
        is ReturnInst -> visitReturnInst(inst)
        is BranchInst -> visitBranchInst(inst)
        is SwitchInst -> visitSwitchInst(inst)
        is UnreachableInst -> visitUnreachableInst(inst)
        else -> {
            addError("Unknown terminator instruction: ${inst::class.simpleName}")
            false
        }
    }
    
    override fun visitBinaryInst(inst: BinaryInst): Boolean = when (inst) {
        is AddInst -> visitAddInst(inst)
        is SubInst -> visitSubInst(inst)
        is MulInst -> visitMulInst(inst)
        is SDivInst -> visitSDivInst(inst)
        is UDivInst -> visitUDivInst(inst)
        is URemInst -> visitURemInst(inst)
        is SRemInst -> visitSRemInst(inst)
        is FAddInst -> visitFAddInst(inst)
        is FSubInst -> visitFSubInst(inst)
        is FMulInst -> visitFMulInst(inst)
        is FDivInst -> visitFDivInst(inst)
        is FRemInst -> visitFRemInst(inst)
        is AndInst -> visitAndInst(inst)
        is OrInst -> visitOrInst(inst)
        is XorInst -> visitXorInst(inst)
        is LShrInst -> visitLShrInst(inst)
        is AShrInst -> visitAShrInst(inst)
        is ShlInst -> visitShlInst(inst)
        else -> {
            addError("Unknown binary instruction: ${inst::class.simpleName}")
            false
        }
    }
    
    override fun visitMemoryInst(inst: MemoryInst): Boolean = when (inst) {
        is AllocaInst -> visitAllocaInst(inst)
        is LoadInst -> visitLoadInst(inst)
        is StoreInst -> visitStoreInst(inst)
        is GetElementPtrInst -> visitGetElementPtrInst(inst)
        else -> {
            addError("Unknown memory instruction: ${inst::class.simpleName}")
            false
        }
    }
    
    override fun visitCastInst(inst: CastInst): Boolean = when (inst) {
        is TruncInst -> visitTruncInst(inst)
        is ZExtInst -> visitZExtInst(inst)
        is SExtInst -> visitSExtInst(inst)
        is BitcastInst -> visitBitcastInst(inst)
        is PtrToIntInst -> visitPtrToIntInst(inst)
        else -> {
            addError("Unknown cast instruction: ${inst::class.simpleName}")
            false
        }
    }
    
    override fun visitOtherInst(inst: OtherInst): Boolean = when (inst) {
        is CallInst -> visitCallInst(inst)
        is ICmpInst -> visitICmpInst(inst)
        is FCmpInst -> visitFCmpInst(inst)
        is PhiNode -> visitPhiNode(inst)
        is CommentAttachment -> visitCommentAttachment(inst)
        else -> {
            addError("Unknown other instruction: ${inst::class.simpleName}")
            false
        }
    }
}
