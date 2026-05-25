package space.norb.llvm.instructions.other

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.enums.FcmpPredicate
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.IntegerType

/**
 * Floating-point comparison instruction.
 *
 * This instruction performs a comparison between two floating-point values and returns
 * a boolean result (i1 type). The comparison is performed according to the
 * specified predicate.
 *
 * IR output examples:
 * ```
 * ; Ordered equality comparison
 * %eq = fcmp oeq float %a, %b
 *
 * ; Unordered less than
 * %ult = fcmp ult double %x, %y
 * ```
 *
 * Key characteristics:
 * - Always returns i1 (boolean) type
 * - Supports all LLVM floating-point comparison predicates
 * - Operands must be floating-point types
 */
class FCmpInst private constructor(
    name: String?,
    type: Type,
    val predicate: FcmpPredicate,
    lhs: Value,
    rhs: Value
) : OtherInst(name, type, listOf(lhs, rhs)) {
    val lhs: Value
        get() = getOperand(0)

    val rhs: Value
        get() = getOperand(1)

    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFCmpInst(this)
    
    override fun getOpcodeName(): String = "fcmp"
    
    /**
     * Gets the left operand.
     */
    fun getLeftOperand(): Value = lhs
    
    /**
     * Gets the right operand.
     */
    fun getRightOperand(): Value = rhs
    
    companion object {
        /**
         * Creates a new FCmpInst.
         *
         * @param name The name of the result variable
         * @param predicate The comparison predicate
         * @param lhs The left operand
         * @param rhs The right operand
         * @return A new FCmpInst instance
         */
        fun create(name: String?, predicate: FcmpPredicate, lhs: Value, rhs: Value): FCmpInst {
            if (lhs.type != rhs.type) {
                throw IllegalArgumentException("FCmp operands must have the same type: ${lhs.type} vs ${rhs.type}")
            }
            
            if (!lhs.type.isFloatingPointType()) {
                throw IllegalArgumentException("FCmp operands must be floating-point types, got ${lhs.type}")
            }
            
            // Result type is always i1 (boolean)
            val resultType = IntegerType.I1
            
            return FCmpInst(name, resultType, predicate, lhs, rhs)
        }
    }
}
