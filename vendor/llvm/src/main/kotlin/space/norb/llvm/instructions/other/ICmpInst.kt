package space.norb.llvm.instructions.other

import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.enums.IcmpPredicate
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.types.IntegerType

/**
 * Integer comparison instruction.
 *
 * This instruction performs a comparison between two integer values and returns
 * a boolean result (i1 type). The comparison is performed according to the
 * specified predicate.
 *
 * The instruction supports both signed and unsigned comparisons, as well as
 * equality checks. All operands must be of integer type or pointers (which are
 * treated as integers for comparison purposes).
 *
 * IR output examples:
 * ```
 * ; Equality comparison
 * %eq = icmp eq i32 %a, %b
 *
 * ; Signed greater than
 * %sgt = icmp sgt i64 %x, %y
 *
 * ; Unsigned less than
 * ; Pointers are compared as unsigned integers
 * %ult = icmp ult ptr %ptr1, %ptr2
 *
 * ; Not equal
 * %ne = icmp ne i32 %count, 0
 * ```
 *
 * Key characteristics:
 * - Always returns i1 (boolean) type
 * - Supports all LLVM integer comparison predicates
 * - Operands must be integer types or pointers
 * - Pointers are treated as unsigned integers for comparison
 * - Pure operation with no side effects
 */
class ICmpInst private constructor(
    name: String?,
    type: Type,
    val predicate: IcmpPredicate,
    lhs: Value,
    rhs: Value
) : OtherInst(name, type, listOf(lhs, rhs)) {
    
    /**
     * The left-hand side operand of the comparison.
     */
    val lhs: Value
        get() = getOperand(0)
    
    /**
     * The right-hand side operand of the comparison.
     */
    val rhs: Value
        get() = getOperand(1)
    
    init {
        // Validate that result type is i1 (boolean)
        if (!type.isIntegerType() || (type as IntegerType).bitWidth != 1) {
            throw IllegalArgumentException("ICmpInst result type must be i1 (boolean), got $type")
        }
        
        // Validate that both operands are of compatible types
        validateOperandTypes(lhs.type, rhs.type)
    }
    
    /**
     * Validates that operand types are compatible for integer comparison.
     *
     * @param lhsType The left operand type
     * @param rhsType The right operand type
     * @throws IllegalArgumentException if types are not compatible
     */
    private fun validateOperandTypes(lhsType: Type, rhsType: Type) {
        val lhsValid = lhsType.isIntegerType() || lhsType.isPointerType()
        val rhsValid = rhsType.isIntegerType() || rhsType.isPointerType()
        
        if (!lhsValid) {
            throw IllegalArgumentException("ICmpInst left operand must be integer or pointer type, got $lhsType")
        }
        
        if (!rhsValid) {
            throw IllegalArgumentException("ICmpInst right operand must be integer or pointer type, got $rhsType")
        }
        
        // In untyped pointer mode, pointers are compatible with integers for comparison
        // and all pointers are compatible with each other
        if (!areTypesCompatibleForComparison(lhsType, rhsType)) {
            throw IllegalArgumentException(
                "ICmpInst operand types must be compatible for comparison: $lhsType vs $rhsType"
            )
        }
    }
    
    /**
     * Checks if two types are compatible for integer comparison.
     *
     * @param type1 The first type
     * @param type2 The second type
     * @return true if types are compatible for comparison
     */
    private fun areTypesCompatibleForComparison(type1: Type, type2: Type): Boolean {
        return when {
            // Same integer types are compatible
            type1.isIntegerType() && type2.isIntegerType() -> type1 == type2
            
            // All pointers are compatible with each other in untyped mode
            type1.isPointerType() && type2.isPointerType() -> true
            
            // Pointers are compatible with integers for comparison purposes
            type1.isPointerType() && type2.isIntegerType() -> true
            type1.isIntegerType() && type2.isPointerType() -> true
            
            else -> false
        }
    }
    
    /**
     * Checks if this is an equality comparison (eq or ne).
     *
     * @return true if this is an equality comparison
     */
    fun isEquality(): Boolean = predicate == IcmpPredicate.EQ || predicate == IcmpPredicate.NE
    
    /**
     * Checks if this is a signed comparison.
     *
     * @return true if this is a signed comparison
     */
    fun isSigned(): Boolean = predicate in setOf(
        IcmpPredicate.SGT, IcmpPredicate.SGE, IcmpPredicate.SLT, IcmpPredicate.SLE
    )
    
    /**
     * Checks if this is an unsigned comparison.
     *
     * @return true if this is an unsigned comparison
     */
    fun isUnsigned(): Boolean = predicate in setOf(
        IcmpPredicate.UGT, IcmpPredicate.UGE, IcmpPredicate.ULT, IcmpPredicate.ULE
    )
    
    /**
     * Gets the predicate as a string for IR printing.
     *
     * @return The predicate string
     */
    fun getPredicateString(): String = predicate.name.lowercase()
    
    /**
     * Checks if this comparison is always true given the operands.
     * This is a simplified analysis - in a real implementation, this would
     * require constant folding and more sophisticated analysis.
     *
     * @return true if the comparison is known to be always true
     */
    fun isKnownTrue(): Boolean {
        // Simple constant folding for equal constants
        if (lhs.isConstant() && rhs.isConstant() && lhs == rhs) {
            return when (predicate) {
                IcmpPredicate.EQ, IcmpPredicate.UGE, IcmpPredicate.SGE -> true
                IcmpPredicate.NE, IcmpPredicate.ULT, IcmpPredicate.SLT -> false
                else -> false // Can't determine without value analysis
            }
        }
        return false
    }
    
    /**
     * Checks if this comparison is always false given the operands.
     * This is a simplified analysis - in a real implementation, this would
     * require constant folding and more sophisticated analysis.
     *
     * @return true if the comparison is known to be always false
     */
    fun isKnownFalse(): Boolean {
        // Simple constant folding for equal constants
        if (lhs.isConstant() && rhs.isConstant() && lhs == rhs) {
            return when (predicate) {
                IcmpPredicate.NE, IcmpPredicate.ULT, IcmpPredicate.SLT -> true
                IcmpPredicate.EQ, IcmpPredicate.UGE, IcmpPredicate.SGE -> false
                else -> false // Can't determine without value analysis
            }
        }
        return false
    }
    
    override fun getOpcodeName(): String = "icmp"
    
    override fun mayHaveSideEffects(): Boolean = false
    
    override fun mayReadFromMemory(): Boolean = false
    
    override fun mayWriteToMemory(): Boolean = false
    
    override fun isPure(): Boolean = true
    
    override fun isSameOperationAs(other: OtherInst): Boolean {
        if (!super.isSameOperationAs(other)) return false
        if (other !is ICmpInst) return false
        return predicate == other.predicate
    }
    
    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitICmpInst(this)
    
    companion object {
        /**
         * Creates an integer comparison instruction.
         *
         * @param name The name of the instruction result
         * @param predicate The comparison predicate
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst
         * @throws IllegalArgumentException if operand validation fails
         */
        fun create(name: String?, predicate: IcmpPredicate, lhs: Value, rhs: Value): ICmpInst {
            // ICmp always returns i1 (boolean) type
            val resultType = Type.getIntegerType(1)
            return ICmpInst(name, resultType, predicate, lhs, rhs)
        }
        
        /**
         * Creates an equality comparison (==).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for equality comparison
         */
        fun createEqual(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.EQ, lhs, rhs)
        }
        
        /**
         * Creates a not-equal comparison (!=).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for not-equal comparison
         */
        fun createNotEqual(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.NE, lhs, rhs)
        }
        
        /**
         * Creates an unsigned greater-than comparison (>).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for unsigned greater-than comparison
         */
        fun createUnsignedGreater(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.UGT, lhs, rhs)
        }
        
        /**
         * Creates an unsigned greater-or-equal comparison (>=).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for unsigned greater-or-equal comparison
         */
        fun createUnsignedGreaterEqual(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.UGE, lhs, rhs)
        }
        
        /**
         * Creates an unsigned less-than comparison (<).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for unsigned less-than comparison
         */
        fun createUnsignedLess(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.ULT, lhs, rhs)
        }
        
        /**
         * Creates an unsigned less-or-equal comparison (<=).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for unsigned less-or-equal comparison
         */
        fun createUnsignedLessEqual(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.ULE, lhs, rhs)
        }
        
        /**
         * Creates a signed greater-than comparison (>).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for signed greater-than comparison
         */
        fun createSignedGreater(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.SGT, lhs, rhs)
        }
        
        /**
         * Creates a signed greater-or-equal comparison (>=).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for signed greater-or-equal comparison
         */
        fun createSignedGreaterEqual(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.SGE, lhs, rhs)
        }
        
        /**
         * Creates a signed less-than comparison (<).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for signed less-than comparison
         */
        fun createSignedLess(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.SLT, lhs, rhs)
        }
        
        /**
         * Creates a signed less-or-equal comparison (<=).
         *
         * @param name The name of the instruction result
         * @param lhs The left-hand operand
         * @param rhs The right-hand operand
         * @return A new ICmpInst for signed less-or-equal comparison
         */
        fun createSignedLessEqual(name: String?, lhs: Value, rhs: Value): ICmpInst {
            return create(name, IcmpPredicate.SLE, lhs, rhs)
        }
    }
}
