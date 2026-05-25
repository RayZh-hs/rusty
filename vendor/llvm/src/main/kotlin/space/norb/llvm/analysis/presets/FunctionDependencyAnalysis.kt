package space.norb.llvm.analysis.presets

import space.norb.llvm.analysis.Analysis
import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.analysis.AnalysisResult
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module

class FunctionDependencyGraph(
    val graph: Map<Function, Set<Function>>,
    val supernodes: List<Set<Function>>,
    val functionToSupernode: Map<Function, Int>,
    val condensedGraph: Map<Int, Set<Int>>,
    private val selfLoopFunctions: Set<Function>
) : AnalysisResult {
    private val reachabilityCache = mutableMapOf<Pair<Int, Int>, Boolean>()

    fun directDependencies(function: Function): Set<Function> = graph[function].orEmpty()

    fun hasSelfLoop(function: Function): Boolean = function in selfLoopFunctions

    fun topologicalSupernodes(): List<Int> {
        val indegree = supernodes.indices.associateWith { 0 }.toMutableMap()
        for (dependencies in condensedGraph.values) {
            for (dependency in dependencies) {
                indegree[dependency] = indegree.getValue(dependency) + 1
            }
        }

        val ready = ArrayDeque(
            indegree
                .filterValues { it == 0 }
                .keys
                .sorted()
        )
        val ordered = mutableListOf<Int>()

        while (ready.isNotEmpty()) {
            val current = ready.removeFirst()
            ordered.add(current)

            for (dependency in condensedGraph[current].orEmpty().sorted()) {
                indegree[dependency] = indegree.getValue(dependency) - 1
                if (indegree.getValue(dependency) == 0) {
                    insertSorted(ready, dependency)
                }
            }
        }

        check(ordered.size == supernodes.size) { "Condensed function dependency graph must be acyclic" }
        return ordered
    }

    fun bottomUpSupernodes(): List<Int> = topologicalSupernodes().asReversed()

    fun bottomUpFunctions(): List<Function> =
        bottomUpSupernodes().flatMap { supernode -> supernodes[supernode] }

    fun dependsOn(dependent: Function, dependency: Function): Boolean {
        val dependentSupernode = functionToSupernode[dependent] ?: return false
        val dependencySupernode = functionToSupernode[dependency] ?: return false

        if (dependent == dependency) {
            return hasSelfLoop(dependent) || supernodes[dependentSupernode].size > 1
        }

        if (dependentSupernode == dependencySupernode) {
            return true
        }

        return canReach(dependentSupernode, dependencySupernode)
    }

    private fun canReach(from: Int, to: Int): Boolean =
        reachabilityCache.getOrPut(from to to) {
            val worklist = ArrayDeque<Int>()
            val visited = mutableSetOf<Int>()
            worklist.add(from)

            while (worklist.isNotEmpty()) {
                val current = worklist.removeFirst()
                if (!visited.add(current)) continue

                for (next in condensedGraph[current].orEmpty()) {
                    if (next == to) return@getOrPut true
                    worklist.add(next)
                }
            }

            false
        }

    private fun insertSorted(values: ArrayDeque<Int>, value: Int) {
        if (values.isEmpty()) {
            values.add(value)
            return
        }

        val reordered = ArrayDeque<Int>()
        var inserted = false
        while (values.isNotEmpty()) {
            val current = values.removeFirst()
            if (!inserted && value < current) {
                reordered.add(value)
                inserted = true
            }
            reordered.add(current)
        }
        if (!inserted) {
            reordered.add(value)
        }
        while (reordered.isNotEmpty()) {
            values.add(reordered.removeFirst())
        }
    }
}

object FunctionDependencyAnalysis : Analysis<FunctionDependencyGraph>() {
    override fun compute(module: Module, am: AnalysisManager): FunctionDependencyGraph {
        val functions = module.functions.toList()
        val functionSet = functions.toSet()
        val graph = linkedMapOf<Function, MutableSet<Function>>()
        val selfLoopFunctions = linkedSetOf<Function>()

        functions.forEach { graph[it] = linkedSetOf() }

        for (function in functions) {
            if (function.isDeclaration) continue

            for (block in function.basicBlocks) {
                for (instruction in block.instructions) {
                    val call = instruction as? CallInst ?: continue
                    val callee = call.callee as? Function ?: continue
                    if (callee !in functionSet) continue

                    graph.getValue(function).add(callee)
                    if (function == callee) {
                        selfLoopFunctions.add(function)
                    }
                }
            }
        }

        val supernodes = tarjan(functions, graph)
        val functionToSupernode = linkedMapOf<Function, Int>()
        for ((index, supernode) in supernodes.withIndex()) {
            for (function in supernode) {
                functionToSupernode[function] = index
            }
        }

        val condensedGraph = linkedMapOf<Int, MutableSet<Int>>()
        supernodes.indices.forEach { condensedGraph[it] = linkedSetOf() }
        for ((from, dependencies) in graph) {
            val fromSupernode = functionToSupernode.getValue(from)
            for (to in dependencies) {
                val toSupernode = functionToSupernode.getValue(to)
                if (fromSupernode != toSupernode) {
                    condensedGraph.getValue(fromSupernode).add(toSupernode)
                }
            }
        }

        return FunctionDependencyGraph(
            graph = graph.mapValues { it.value.toSet() },
            supernodes = supernodes,
            functionToSupernode = functionToSupernode,
            condensedGraph = condensedGraph.mapValues { it.value.toSet() },
            selfLoopFunctions = selfLoopFunctions
        )
    }

    private fun tarjan(
        functions: List<Function>,
        graph: Map<Function, Set<Function>>
    ): List<Set<Function>> {
        var nextIndex = 0
        val indexByFunction = mutableMapOf<Function, Int>()
        val lowLinkByFunction = mutableMapOf<Function, Int>()
        val stack = ArrayDeque<Function>()
        val onStack = mutableSetOf<Function>()
        val supernodes = mutableListOf<Set<Function>>()

        fun strongConnect(function: Function) {
            indexByFunction[function] = nextIndex
            lowLinkByFunction[function] = nextIndex
            nextIndex++
            stack.addLast(function)
            onStack.add(function)

            for (dependency in graph[function].orEmpty()) {
                if (dependency !in indexByFunction) {
                    strongConnect(dependency)
                    lowLinkByFunction[function] = minOf(
                        lowLinkByFunction.getValue(function),
                        lowLinkByFunction.getValue(dependency)
                    )
                } else if (dependency in onStack) {
                    lowLinkByFunction[function] = minOf(
                        lowLinkByFunction.getValue(function),
                        indexByFunction.getValue(dependency)
                    )
                }
            }

            if (lowLinkByFunction.getValue(function) == indexByFunction.getValue(function)) {
                val supernode = linkedSetOf<Function>()
                while (true) {
                    val member = stack.removeLast()
                    onStack.remove(member)
                    supernode.add(member)
                    if (member == function) break
                }
                supernodes.add(supernode)
            }
        }

        for (function in functions) {
            if (function !in indexByFunction) {
                strongConnect(function)
            }
        }

        return supernodes
    }
}
