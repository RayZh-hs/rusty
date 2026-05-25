package space.norb.llvm.visitors

import space.norb.llvm.structure.Module
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Argument
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.llvm.core.Constant
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
 * Visitor interface for traversing LLVM IR.
 */
interface IRVisitor<T> {
    fun visitModule(module: Module): T
    fun visitFunction(function: Function): T
    fun visitBasicBlock(block: BasicBlock): T
    
    // Value visitors
    fun visitArgument(argument: Argument): T
    fun visitGlobalVariable(globalVariable: GlobalVariable): T
    fun visitConstant(constant: Constant): T
    fun visitMetadata(metadata: Metadata): T
    
    // Instruction visitors
    fun visitReturnInst(inst: ReturnInst): T
    fun visitBranchInst(inst: BranchInst): T
    fun visitSwitchInst(inst: SwitchInst): T
    fun visitUnreachableInst(inst: UnreachableInst): T
    fun visitAddInst(inst: AddInst): T
    fun visitSubInst(inst: SubInst): T
    fun visitMulInst(inst: MulInst): T
    fun visitSDivInst(inst: SDivInst): T
    fun visitUDivInst(inst: UDivInst): T
    fun visitURemInst(inst: URemInst): T
    fun visitSRemInst(inst: SRemInst): T
    fun visitFAddInst(inst: FAddInst): T
    fun visitFSubInst(inst: FSubInst): T
    fun visitFMulInst(inst: FMulInst): T
    fun visitFDivInst(inst: FDivInst): T
    fun visitFRemInst(inst: FRemInst): T
    fun visitAndInst(inst: AndInst): T
    fun visitOrInst(inst: OrInst): T
    fun visitXorInst(inst: XorInst): T
    fun visitLShrInst(inst: LShrInst): T
    fun visitAShrInst(inst: AShrInst): T
    fun visitShlInst(inst: ShlInst): T
    fun visitAllocaInst(inst: AllocaInst): T
    fun visitLoadInst(inst: LoadInst): T
    fun visitStoreInst(inst: StoreInst): T
    fun visitGetElementPtrInst(inst: GetElementPtrInst): T
    fun visitTruncInst(inst: TruncInst): T
    fun visitZExtInst(inst: ZExtInst): T
    fun visitSExtInst(inst: SExtInst): T
    fun visitBitcastInst(inst: BitcastInst): T
    fun visitPtrToIntInst(inst: PtrToIntInst): T
    fun visitCallInst(inst: CallInst): T
    fun visitICmpInst(inst: ICmpInst): T
    fun visitFCmpInst(inst: FCmpInst): T
    fun visitPhiNode(inst: PhiNode): T
    fun visitCommentAttachment(inst: CommentAttachment): T
    
    // Base instruction visitors for generic handling
    fun visitTerminatorInst(inst: TerminatorInst): T
    fun visitBinaryInst(inst: BinaryInst): T
    fun visitMemoryInst(inst: MemoryInst): T
    fun visitCastInst(inst: CastInst): T
    fun visitOtherInst(inst: OtherInst): T
}
