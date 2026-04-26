package rusty.asm

import rusty.asm.support.AsmContext
import rusty.asm.support.StackFrame
import rusty.asm.utils.SavableSlot
import rusty.asm.utils.calleeSavedRegisters
import rusty.asm.utils.callerSavedRegisters
import space.norb.llvm.core.Value
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.Function
import space.norb.llvm.utils.computeLayout

internal object StackFrameMaterializer {
    fun materialize(asmContext: AsmContext, registerBytes: Int) {
        for ((function, allocation) in asmContext.registerAllocation) {
            val frame = asmContext.stackManager.getStackFrame(function)
            asmContext.stackManager.materializeSpills(function, allocation, registerBytes)
            materializeAllocas(function, frame, registerBytes)
            materializeSavedRegisters(function, frame, allocation)
        }
    }

    private fun materializeAllocas(function: Function, frame: StackFrame, registerBytes: Int) {
        for ((index, alloca) in function.instructions().filterIsInstance<AllocaInst>().withIndex()) {
            val name = alloca.stackObjectName(index)
            if (frame.objectWithName(name) != null) continue

            val layout = alloca.allocatedType.computeLayout(function.module, pointerWidthBits = registerBytes * 8)
            val count = alloca.constantArraySize()
            frame.alloca(
                sizeBytes = (layout.sizeInBytes * count).toIntExact("alloca $name"),
                alignBytes = layout.alignment,
                name = name,
            )
        }
    }

    private fun materializeSavedRegisters(
        function: Function,
        frame: StackFrame,
        allocation: Map<Value, SavableSlot>,
    ) {
        val allocatedCalleeSaved = allocation.values
            .asSequence()
            .filterIsInstance<SavableSlot.Register>()
            .map { it.physical }
            .filter { it in calleeSavedRegisters }
            .toSet()

        for (register in allocatedCalleeSaved) {
            if (frame.objectWithName(register.name.lowercase()) == null) {
                frame.save(register)
            }
        }

        if (function.containsCall() && frame.objectWithName("ra") == null) {
            frame.save(rusty.asm.utils.Register.RA)
        }

        if (function.containsCall()) {
            val allocatedCallerSaved = allocation.values
                .asSequence()
                .filterIsInstance<SavableSlot.Register>()
                .map { it.physical }
                .filter { it in callerSavedRegisters }
                .toSet()

            for (register in allocatedCallerSaved) {
                val name = register.callSaveTempName()
                if (frame.objectWithName(name) == null) {
                    frame.temp(sizeBytes = 4, alignBytes = 4, name = name)
                }
            }

            val maxCallArguments = function.instructions()
                .filterIsInstance<CallInst>()
                .maxOfOrNull { it.arguments.size }
                ?: 0
            for (index in 0 until maxCallArguments) {
                val name = callArgumentTempName(index)
                if (frame.objectWithName(name) == null) {
                    frame.temp(sizeBytes = 4, alignBytes = 4, name = name)
                }
            }
        }
    }
}
