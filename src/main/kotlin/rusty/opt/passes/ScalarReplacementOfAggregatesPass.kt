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

object ScalarReplacementOfAggregatesPass : IRPass() {
    private data class FieldKey(val indices: List<Long>)
    private data class ReplacementPlan(
        val alloca: AllocaInst,
        val replacements: Map<FieldKey, AllocaInst>,
        val gepsToRemove: Set<GetElementPtrInst>
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
                plans.add(buildPlan(alloca) ?: continue)
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
                block.instructions.remove(plan.alloca)
            }
        }
    }

    private fun isEntryAlloca(function: Function, alloca: AllocaInst): Boolean {
        val entry = function.entryBlock ?: return false
        return entry.instructions.contains(alloca)
    }

    private fun buildPlan(alloca: AllocaInst): ReplacementPlan? {
        val directUses = alloca.getUses()
        if (directUses.isEmpty()) return null
        if (directUses.any { it !is GetElementPtrInst || it.pointer != alloca }) return null

        val replacements = linkedMapOf<FieldKey, AllocaInst>()
        val gepsToRemove = linkedSetOf<GetElementPtrInst>()

        for (user in directUses) {
            val gep = user as GetElementPtrInst
            val key = fieldKey(gep) ?: return null
            if (!hasOnlyMemoryUses(gep)) return null

            val fieldType = gep.getFinalElementType()
            replacements.getOrPut(key) {
                AllocaInst("${alloca.name}.${key.nameSuffix()}.sroa", fieldType)
            }
            gepsToRemove.add(gep)
        }

        return if (replacements.isEmpty()) null else ReplacementPlan(alloca, replacements, gepsToRemove)
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

    private fun hasOnlyMemoryUses(value: Value): Boolean {
        return value.getUses().all { user ->
            when (user) {
                is LoadInst -> user.pointer == value
                is StoreInst -> user.pointer == value
                else -> false
            }
        }
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
