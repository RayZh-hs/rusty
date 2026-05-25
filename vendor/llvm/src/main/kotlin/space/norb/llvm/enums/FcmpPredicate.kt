package space.norb.llvm.enums

/**
 * Predicates for floating-point comparison (fcmp) instructions.
 *
 * These predicates determine how the comparison is performed and how NaNs are handled.
 *
 * Ordered comparisons return false if either operand is NaN.
 * Unordered comparisons return true if either operand is NaN.
 */
enum class FcmpPredicate(val irName: String) {
    /** Always false (always folds to false) */
    FALSE("false"),
    
    /** Ordered and equal */
    OEQ("oeq"),
    
    /** Ordered and greater than */
    OGT("ogt"),
    
    /** Ordered and greater than or equal */
    OGE("oge"),
    
    /** Ordered and less than */
    OLT("olt"),
    
    /** Ordered and less than or equal */
    OLE("ole"),
    
    /** Ordered and not equal */
    ONE("one"),
    
    /** Ordered (no nans) */
    ORD("ord"),
    
    /** Unordered or equal */
    UEQ("ueq"),
    
    /** Unordered or greater than */
    UGT("ugt"),
    
    /** Unordered or greater than or equal */
    UGE("uge"),
    
    /** Unordered or less than */
    ULT("ult"),
    
    /** Unordered or less than or equal */
    ULE("ule"),
    
    /** Unordered or not equal */
    UNE("une"),
    
    /** Unordered (either nans) */
    UNO("uno"),
    
    /** Always true (always folds to true) */
    TRUE("true");
    
    override fun toString(): String = irName
}
