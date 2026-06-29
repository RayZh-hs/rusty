package rusty.asm.support

import space.norb.llvm.core.Value

/**
 * Stage 1 of register copy coalescing: graph-level, run *inside* the allocator before coloring.
 *
 * The instruction selector emits a `mv` for every value-to-value copy in the program. There are four
 * distinct sources, and they are *not* one problem — each is removed by a different stage:
 *
 * ```
 * Source: Phi resolution            mv phiReg, incomingReg
 *   Where:        emitScalarPhiMoves (AsmTranslator:1218)
 *   Removed by:   today, only if the allocator coincidentally gave phi and incoming the same slot
 *                 (emitScalarPhiMoves' removeAll { destination == source })
 *   Coalescable in allocator?  Yes — THIS pass is the target.
 * ────────────────────────────────────────
 * Source: Parameter setup           mv allocReg, argReg
 *   Where:        moveParametersDirectly (AsmTranslator:735)
 *   Removed by:   today, only coincidentally (dest == argReg)
 *   Coalescable in allocator?  Yes (biasing) — future extension; not handled here yet.
 * ────────────────────────────────────────
 * Source: Load result              mv dst, t5
 *   Where:        lowerLoad loads into fixed scratch t5, then writeValue moves it (AsmTranslator:838)
 *   Removed by:   AsmCoalescing R1
 *   Coalescable in allocator?  No — a codegen artifact, not an IR copy. See [AsmCoalescing].
 * ────────────────────────────────────────
 * Source: GEP result               mv dst, t4
 *   Where:        lowerGep -> emitGepAddress(.., t4) then writeValue (AsmTranslator:869)
 *   Removed by:   AsmCoalescing R1/R2
 *   Coalescable in allocator?  No — a codegen artifact, not an IR copy. See [AsmCoalescing].
 * ```
 *
 * This pass handles row 1 (phi resolution). It merges a phi and a copy-related operand into one node so
 * they receive the same register; the phi-resolution move then has identical source and destination and
 * [rusty.asm.AsmTranslator]'s move sequencer drops it for free. [AsmCoalescing] is the asm-level stage 2
 * that mops up rows 3-4, the scratch moves this pass structurally cannot see.
 *
 * Algorithm — Briggs conservative coalescing.
 * Move-related node pairs (phi, incoming) are merged via union-find iff they do not interfere and the
 * merge passes the Briggs test: the combined node has fewer than `registerCount` neighbors of
 * significant degree (degree >= registerCount). Briggs proved this test can never turn a K-colorable
 * graph into a non-K-colorable one, so coalescing here never trades a move for a spill. We iterate over
 * the move set to a fixpoint because merging two nodes lowers the degree of their common neighbors,
 * which can unblock a move that failed the test on an earlier pass.
 */
object RegallocCoalescing {
    data class Result(
        /** Maps every original interference-graph node to the representative it was merged into. */
        val representativeOf: Map<Value, Value>,
        /** The coalesced graph, keyed by representative; both keys and neighbours are representatives. */
        val mergedGraph: Map<Value, Set<Value>>,
    )

    /**
     * @param graph the interference graph (a node's set lists every node it cannot share a register with)
     * @param moves copy-related pairs to attempt to coalesce; entries referencing non-graph nodes are ignored
     * @param registerCount K, the number of allocatable registers — the Briggs significance threshold
     * @param stableOrder a deterministic index per node, used only to pick which side of a merge survives
     */
    fun run(
        graph: Map<Value, Set<Value>>,
        moves: List<Pair<Value, Value>>,
        registerCount: Int,
        stableOrder: (Value) -> Int,
    ): Result {
        // Adjacency keyed by the *current* representative; every stored neighbour is also a current
        // representative. A LinkedHashMap/LinkedHashSet preserves the input's deterministic order so the
        // surviving representatives reach colorGraph in a stable sequence (its tie-breaking depends on it).
        val adjacency = LinkedHashMap<Value, LinkedHashSet<Value>>()
        for ((node, neighbours) in graph) {
            adjacency.getOrPut(node) { LinkedHashSet() }.addAll(neighbours)
            for (neighbour in neighbours) adjacency.getOrPut(neighbour) { LinkedHashSet() }
        }

        // Union-find over nodes. parent[v] == v marks a representative.
        val parent = HashMap<Value, Value>()
        for (node in adjacency.keys) parent[node] = node

        fun find(value: Value): Value {
            var root = value
            while (parent.getValue(root) != root) root = parent.getValue(root)
            var current = value
            while (parent.getValue(current) != current) {
                val next = parent.getValue(current)
                parent[current] = root
                current = next
            }
            return root
        }

        // Merge `b` into `a` (both current, distinct, non-interfering representatives). The survivor is the
        // lower-stableOrder side so the result is independent of map iteration order. All of the dropped
        // node's edges are redirected onto the survivor, keeping the invariant that adjacency holds only
        // representatives; a neighbour shared by both sides loses one edge, which is what lets the Briggs
        // test improve as coalescing proceeds.
        fun union(a: Value, b: Value) {
            val keep = if (stableOrder(a) <= stableOrder(b)) a else b
            val drop = if (keep == a) b else a
            parent[drop] = keep
            val keepAdjacency = adjacency.getValue(keep)
            val dropAdjacency = adjacency.remove(drop) ?: linkedSetOf()
            for (neighbour in dropAdjacency) {
                if (neighbour == keep) continue
                val neighbourAdjacency = adjacency.getValue(neighbour)
                neighbourAdjacency.remove(drop)
                neighbourAdjacency.add(keep)
                keepAdjacency.add(neighbour)
            }
            keepAdjacency.remove(drop)
        }

        // Briggs test: safe to merge x and y iff the combined neighbourhood has < K significant-degree
        // (>= K) members. Low-degree neighbours are ignored — they will simplify away on their own.
        fun coalesceIsSafe(x: Value, y: Value): Boolean {
            val combined = LinkedHashSet<Value>()
            combined.addAll(adjacency.getValue(x))
            combined.addAll(adjacency.getValue(y))
            combined.remove(x)
            combined.remove(y)
            var significant = 0
            for (neighbour in combined) {
                if (adjacency.getValue(neighbour).size >= registerCount) {
                    significant += 1
                    if (significant >= registerCount) return false
                }
            }
            return true
        }

        var changed = true
        while (changed) {
            changed = false
            for ((left, right) in moves) {
                if (left !in parent || right !in parent) continue
                val x = find(left)
                val y = find(right)
                if (x == y) continue              // already coalesced
                if (y in adjacency.getValue(x)) continue  // interfere — cannot share a register
                if (coalesceIsSafe(x, y)) {
                    union(x, y)
                    changed = true
                }
            }
        }

        val representativeOf = LinkedHashMap<Value, Value>()
        for (node in graph.keys) representativeOf[node] = find(node)
        return Result(representativeOf, adjacency)
    }
}
