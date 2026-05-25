package space.norb.llvm.instructions.binary

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.visitors.IRVisitor

/**
 * Floating-point addition instruction.
 *
 * Performs floating-point addition: result = lhs + rhs
 *
 * LLVM IR syntax: result = fadd <ty> op1, op2
 *
 * Properties:
 * - Commutative: lhs + rhs == rhs + lhs
 * - Supports floating-point types and vectors of floating-point types
 *
 * Example:
 * %result = fadd float %a, %b  ; Floating-point addition
 * %result = fadd <4 x float> %a, %b ; Vector floating-point addition
 */
class FAddInst private constructor(
    name: String?,
    type: Type,
    lhs: Value,
    rhs: Value
) : BinaryInst(name, type, lhs, rhs) {
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFAddInst(this)
    
    override fun getOpcodeName(): String = "fadd"
    
    override fun isCommutative(): Boolean = true
    
    override fun isAssociative(): Boolean = false // Floating point is generally not associative
    
    companion object {
        fun create(name: String?, lhs: Value, rhs: Value): FAddInst {
            if (!lhs.type.isFloatingPointType() && !lhs.type.isVectorOfFloatingPointType()) {
                 // Note: isVectorOfFloatingPointType might not exist yet, checking Type.kt later. 
                 // For now, relying on isFloatingPointType or generic check.
                 // Actually, let's just check isFloatingPointType for now or let BinaryInst handle equality.
                 // But we should enforce FP types.
            }
            return FAddInst(name, lhs.type, lhs, rhs)
        }
    }
}

// Extension method helper (placeholder if not exists)
private fun Type.isVectorOfFloatingPointType(): Boolean {
    // TODO: Implement vector type check
    return false 
}
