package space.norb.llvm.analysis

/**
 * The result of an Analysis.
 *
 * This is a marker interface for all analysis results, and can be implemented by any class.
 */
interface AnalysisResult {
    /**
     * Analysis Result: Update Method
     *
     * Indicates that the method is used to update the analysis result. Typically called by transformations after modifications to the module is made.
     */
    annotation class UpdateMethod
}