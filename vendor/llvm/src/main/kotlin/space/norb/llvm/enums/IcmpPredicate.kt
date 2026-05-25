package space.norb.llvm.enums

/**
 * Enumeration of integer comparison predicates.
 */
enum class IcmpPredicate {
    EQ,      // Equal
    NE,      // Not Equal
    UGT,     // Unsigned Greater Than
    UGE,     // Unsigned Greater or Equal
    ULT,     // Unsigned Less Than
    ULE,     // Unsigned Less or Equal
    SGT,     // Signed Greater Than
    SGE,     // Signed Greater or Equal
    SLT,     // Signed Less Than
    SLE      // Signed Less or Equal
}