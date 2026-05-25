package space.norb.llvm.enums

/**
 * Enumeration of LLVM linkage types.
 */
enum class LinkageType {
    EXTERNAL,
    PRIVATE,
    INTERNAL,
    LINK_ONCE,
    WEAK,
    COMMON,
    APPENDING,
    EXTERN_WEAK,
    AVAILABLE_EXTERNALLY,
    DLL_IMPORT,
    DLL_EXPORT,
    EXTERNAL_WEAK,
    GHOST,
    LINKER_PRIVATE,
    LINKER_PRIVATE_WEAK
}