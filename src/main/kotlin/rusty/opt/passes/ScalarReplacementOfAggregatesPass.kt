package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.casts.BitcastInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import space.norb.llvm.types.IntegerType
import space.norb.llvm.types.StructType
import space.norb.llvm.values.constants.IntConstant

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

            if (plan.keepAlloca) {
                insertFieldInitLoads(function, plan)
            }

            for (block in function.basicBlocks) {
                block.instructions.removeAll(plan.gepsToRemove)
                if (!plan.keepAlloca) {
                    block.instructions.remove(plan.alloca)
                }
            }
        }
    }

    private fun insertFieldInitLoads(function: Function, plan: ReplacementPlan) {
        val structType = plan.alloca.allocatedType as StructType
        for (block in function.basicBlocks) {
            for (i in block.instructions.indices) {
                val inst = block.instructions[i]
                if (inst !is CallInst) continue
                val calleeName = (inst.callee as? space.norb.llvm.structure.Function)?.name ?: continue
                if (calleeName != "aux.func.memfill") continue
                if (inst.arguments.size < 4) continue
                val countArg = inst.arguments[3] as? IntConstant ?: continue
                if (countArg.value != 1L) continue
                val dstPtr = resolvePointerBase(inst.arguments[0])
                if (dstPtr != plan.alloca) continue

                val insertIdx = i + 1
                val newInsts = mutableListOf<Instruction>()
                for ((key, fieldAlloca) in plan.replacements) {
                    val fieldIdx = key.indices.firstOrNull()?.toInt() ?: continue
                    val fieldGep = GetElementPtrInst.createStructField(
                        "sroa.reload.f$fieldIdx", structType, plan.alloca, fieldIdx
                    )
                    val fieldType = fieldGep.getFinalElementType()
                    val load = LoadInst("sroa.reload.v$fieldIdx", fieldType, fieldGep)
                    val store = StoreInst("sroa.reload.s$fieldIdx", fieldType, load, fieldAlloca)
                    newInsts.add(fieldGep)
                    newInsts.add(load)
                    newInsts.add(store)
                }
                block.instructions.addAll(insertIdx, newInsts)
                return
            }
        }
    }

    private fun resolvePointerBase(value: Value): Value {
        var current = value
        while (current is BitcastInst) {
            current = current.getOperand(0)
        }
        return current
    }

    private fun isEntryAlloca(function: Function, alloca: AllocaInst): Boolean {
        val entry = function.entryBlock ?: return false
        return entry.instructions.contains(alloca)
    }

    private fun buildPlan(alloca: AllocaInst, function: Function): ReplacementPlan? {
        val directUses = alloca.getUses().filter { isAliveInstruction(it, function) }
        if (directUses.isEmpty()) return null

        val geps = mutableListOf<GetElementPtrInst>()
        var hasNonGepUses = false

        for (user in directUses) {
            when {
                user is GetElementPtrInst && user.pointer == alloca -> geps.add(user)
                user is BitcastInst -> hasNonGepUses = true
                user is StoreInst && user.value == alloca -> hasNonGepUses = true
                else -> return null
            }
        }

        if (geps.isEmpty()) return null

        val replacements = linkedMapOf<FieldKey, AllocaInst>()
        val gepsToRemove = linkedSetOf<GetElementPtrInst>()

        for (gep in geps) {
            val key = fieldKey(gep) ?: return null
            if (!hasOnlyScalarUses(gep)) continue  // skip non-scalar (array) fields
            val fieldType = gep.getFinalElementType()
            replacements.getOrPut(key) {
                AllocaInst("${alloca.name}.${key.nameSuffix()}.sroa", fieldType)
            }
            gepsToRemove.add(gep)
        }

        return if (replacements.isEmpty()) null
        else ReplacementPlan(alloca, replacements, gepsToRemove, keepAlloca = hasNonGepUses)
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

    private fun hasAcceptableUses(value: Value): Boolean {
        return value.getUses().all { user ->
            when (user) {
                is LoadInst -> user.pointer == value
                is StoreInst -> user.pointer == value
                is GetElementPtrInst -> user.pointer == value && hasAcceptableUses(user)
                is BitcastInst -> hasAcceptableUses(user)
                is CallInst -> true
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
