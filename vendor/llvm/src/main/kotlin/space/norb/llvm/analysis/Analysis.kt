package space.norb.llvm.analysis

import kotlin.reflect.KClass
import space.norb.llvm.structure.Module

/**
 * An Analysis is a computation that extracts information from the IR tree.
 *
 * Analyses should be pure functions of the IR tree, and should not modify the IR tree.
 */
abstract class Analysis<Result : AnalysisResult> {
    annotation class Primitive  // Used on analyses that does not depend on any other analyses
    annotation class Requires(vararg val dependencies: KClass<out Analysis<*>>)  // Used on analyses that depends on other analyses

    // Unique key to identify this analysis (using class itself)
    open val key: KClass<out Analysis<Result>> = this::class
    abstract fun compute(module: Module, am: AnalysisManager): Result
}
