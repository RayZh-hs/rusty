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
            materializeSavedRegisters(function, frame, allocation, registerBytes)
        }
    }

    private fun materializeAllocas(function: Function, frame: StackFrame, registerBytes: Int) {
        for ((index, alloca) in function.instructions().filterIsInstance<AllocaInst>().withIndex()) {
            val name = alloca.stackObjectName(index)
            if (frame.objectForAlloca(alloca) != null) continue

            val layout = alloca.allocatedType.computeLayout(function.module, pointerWidthBits = registerBytes * 8)
            val count = alloca.constantArraySize()
            frame.alloca(
                sizeBytes = (layout.sizeInBytes * count).toIntExact("alloca $name"),
                alignBytes = layout.alignment,
                name = name,
                alloca = alloca,
            )
        }
    }

    private fun materializeSavedRegisters(
        function: Function,
        frame: StackFrame,
        allocation: Map<Value, SavableSlot>,
        registerBytes: Int,
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

        val hasCalls = function.containsCall()
        // Parameters need stack temps only when they cannot be copied straight from their incoming
        // argument register into their allocated register. When every parameter moves directly (the
        // common case for leaf functions), reserving these temps forces a stack frame whose only
        // purpose is an unused prologue/epilogue `sp` adjustment. Mirrors AsmTranslator's
        // canDirectMoveParameters so a function that takes the direct path never has temps reserved
        // and one that takes the spill path always does.
        val parametersNeedTemps = function.parameters.isNotEmpty() &&
            !canMoveParametersDirectly(function, allocation, registerBytes)

        if (hasCalls && frame.objectWithName("ra") == null) {
            frame.save(rusty.asm.utils.Register.RA)
        }

        if (hasCalls) {
            val allocatedCallerSaved = allocation.values
                .asSequence()
                .filterIsInstance<SavableSlot.Register>()
                .map { it.physical }
                .filter { it in callerSavedRegisters }
                .toSet()

            for (register in allocatedCallerSaved) {
                val name = register.callSaveTempName()
                if (frame.objectWithName(name) == null) {
                    frame.temp(sizeBytes = registerBytes, alignBytes = registerBytes, name = name)
                }
            }
        }

        if (hasCalls || parametersNeedTemps) {
            val maxCallArguments = maxOf(
                if (parametersNeedTemps) function.parameters.size else 0,
                if (hasCalls) {
                    function.instructions()
                        .filterIsInstance<CallInst>()
                        .maxOfOrNull { it.arguments.size }
                        ?: 0
                } else {
                    0
                },
            )
            for (index in 0 until maxCallArguments) {
                val name = callArgumentTempName(index)
                if (frame.objectWithName(name) == null) {
                    frame.temp(sizeBytes = registerBytes, alignBytes = registerBytes, name = name)
                }
            }
        }
    }

    // RISC-V passes the first eight integer/pointer arguments in a0-a7.
    private const val ARGUMENT_REGISTER_COUNT = 8

    private fun canMoveParametersDirectly(
        function: Function,
        allocation: Map<Value, SavableSlot>,
        registerBytes: Int,
    ): Boolean {
        return function.parameters.withIndex().all { (index, parameter) ->
            index < ARGUMENT_REGISTER_COUNT &&
                parameter.type.sizeBytes(function.module) <= registerBytes &&
                allocation[parameter] is SavableSlot.Register
        }
    }
}
