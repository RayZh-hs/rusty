package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.StructType
import space.norb.llvm.values.constants.IntConstant

/**
 * Scalar replacement of aggregates: split a struct alloca into one independent alloca per scalar
 * field, so the following Mem2Reg can promote each field into a register on its own.
 *
 *   s = alloca {i32, i32}        s.field0 = alloca i32
 *   a = gep s, 0, 0         ->   s.field1 = alloca i32
 *   b = gep s, 0, 1             (geps replaced by the matching field alloca; s removed if fully split)
 *
 * Notes: bails out if the aggregate escapes as a whole (any use of the alloca other than a
 * constant-index field gep — e.g. passed to a call or memcpy). Fields that are themselves aggregates
 * (further GEP'd into) stay inside the original alloca, which is then kept. Runs before Mem2Reg.
 */
object ScalarReplacementOfAggregatesPass : IRPass() {
    private data class FieldKey(val indices: List<Long>)
    private data class ReplacementPlan(
        val alloca: AllocaInst,
        val replacements: Map<FieldKey, AllocaInst>,
        val gepsToRemove: Set<GetElementPtrInst>,
        val keepAlloca: Boolean
    )

    override fun run(module: Module, am: AnalysisManager): Module {
        for (function in module.functions) {
            if (function.isDeclaration || function.entryBlock == null) continue
            runOnFunction(function)
        }
        return module
    }

    private fun runOnFunction(function: Function) {
        val plans = mutableListOf<ReplacementPlan>()

        for (block in function.basicBlocks) {
            for (instruction in block.instructions) {
                val alloca = instruction as? AllocaInst ?: continue
                if (alloca.allocatedType !is StructType || !isEntryAlloca(function, alloca)) continue
                plans.add(buildPlan(alloca, function) ?: continue)
            }
        }

        for (plan in plans) {
            val entry = function.entryBlock ?: continue
            val insertionIndex = entry.instructions.indexOf(plan.alloca).takeIf { it >= 0 } ?: 0
            entry.instructions.addAll(insertionIndex + 1, plan.replacements.values)

            for ((key, fieldAlloca) in plan.replacements) {
                val geps = plan.gepsToRemove.filter { fieldKey(it) == key }
                for (gep in geps) {
                    replaceAllUses(function, gep, fieldAlloca)
                }
            }

            for (block in function.basicBlocks) {
                block.instructions.removeAll(plan.gepsToRemove)
                if (!plan.keepAlloca) {
                    block.instructions.remove(plan.alloca)
                }
            }
        }
    }

    private fun isEntryAlloca(function: Function, alloca: AllocaInst): Boolean {
        val entry = function.entryBlock ?: return false
        return entry.instructions.contains(alloca)
    }

    /**
     * Build a scalar-replacement plan for a struct alloca, or return null if it cannot be split.
     *
     * Bails on any non-GEP use of the alloca (the address escapes, so a split would let aliased
     * copies drift out of sync). Of the constant-index field GEPs, only fields used exclusively by
     * load/store are scalarized; aggregate fields stay in the struct and the original alloca is kept.
     */
    private fun buildPlan(alloca: AllocaInst, function: Function): ReplacementPlan? {
        val directUses = alloca.getUses().filter { isAliveInstruction(it, function) }
        if (directUses.isEmpty()) return null

        val geps = mutableListOf<GetElementPtrInst>()

        for (user in directUses) {
            // Any use that is not a GEP off this alloca lets the aggregate escape as a whole.
            if (user is GetElementPtrInst && user.pointer == alloca) {
                geps.add(user)
            } else {
                return null
            }
        }

        if (geps.isEmpty()) return null

        val replacements = linkedMapOf<FieldKey, AllocaInst>()
        val gepsToRemove = linkedSetOf<GetElementPtrInst>()
        var keepAlloca = false

        for (gep in geps) {
            // A non-constant field path means we cannot statically attribute the access to a field.
            val key = fieldKey(gep) ?: return null
            if (!hasOnlyScalarUses(gep)) {
                keepAlloca = true  // non-scalar (nested aggregate) field stays in the struct
                continue
            }
            val fieldType = gep.getFinalElementType()
            replacements.getOrPut(key) {
                AllocaInst("${alloca.name}.${key.nameSuffix()}.sroa", fieldType)
            }
            gepsToRemove.add(gep)
        }

        return if (replacements.isEmpty()) null
        else ReplacementPlan(alloca, replacements, gepsToRemove, keepAlloca = keepAlloca)
    }

    private fun isAliveInstruction(user: User, function: Function): Boolean {
        val inst = user as? Instruction ?: return false
        return function.basicBlocks.any { it.instructions.contains(inst) }
    }

    private fun hasOnlyScalarUses(value: Value): Boolean {
        return value.getUses().all { user ->
            when (user) {
                is LoadInst -> user.pointer == value
                is StoreInst -> user.pointer == value
                else -> false
            }
        }
    }

    private fun fieldKey(gep: GetElementPtrInst): FieldKey? {
        val indices = gep.indices
        if (indices.isEmpty()) return null
        val first = indices.first() as? IntConstant ?: return null
        if (first.value != 0L) return null

        val path = indices.drop(1).map { index ->
            (index as? IntConstant)?.value ?: return null
        }
        if (path.isEmpty()) return null
        return FieldKey(path)
    }

    private fun FieldKey.nameSuffix(): String = indices.joinToString(".") { "field$it" }

    private fun replaceAllUses(function: Function, oldValue: Value, newValue: Value) {
        for (block in function.basicBlocks) {
            for (instruction in block.instructions) {
                if (instruction == oldValue) continue
                replaceUses(instruction, oldValue, newValue)
            }
        }
    }

    private fun replaceUses(user: User, oldValue: Value, newValue: Value) {
        for (index in 0 until user.getNumOperands()) {
            if (user.getOperand(index) == oldValue) {
                user.setOperand(index, newValue)
            }
        }
    }
}
