package rusty.asm.support

import rusty.core.RiscvTargetConfig
import rusty.asm.utils.*
import space.norb.llvm.core.Value
import space.norb.llvm.structure.Function
import space.norb.llvm.utils.computeLayout

enum class StackObjectKind {
    Spill,          // Spilled register
    LoweringTemp,   // Temporary object generated when lowering LLVM IR to Asm
    Alloca,         // Object created by an alloca instruction in LLVM IR
    SavedRegister,  // Stack slot used to save a callee-saved register
}

data class StackObject(
    val kind: StackObjectKind,
    val sizeBytes: Int,
    val alignBytes: Int,
    val name: String? = null,
    val savedRegister: Register? = null,
    val stackSlotId: Int? = null,
)

class PlacedStackObject(
    val stackObject: StackObject,
    val ofStackFrame: StackFrame,
    offsetFromSp: Int,
    val offsetFromFp: Int,
    val index: Int,
) {
    var offsetFromSp: Int = offsetFromSp
        internal set

    val sizeBytes: Int
        get() = stackObject.sizeBytes

    val alignBytes: Int
        get() = stackObject.alignBytes

    val kind: StackObjectKind
        get() = stackObject.kind
}

class StackFrame(
    val alignBytes: Int = 16,
) {
    val frameObjects = mutableListOf<PlacedStackObject>()
    var frameSizeBytes: Int = 0
        private set

    private var usedBytes: Int = 0

    fun place(stackObject: StackObject): PlacedStackObject {
        val end = alignUp(usedBytes + stackObject.sizeBytes, stackObject.alignBytes)
        val placed = PlacedStackObject(
            stackObject = stackObject,
            ofStackFrame = this,
            offsetFromSp = 0,
            offsetFromFp = -end,
            index = frameObjects.size,
        )

        usedBytes = end
        frameObjects.add(placed)
        refreshSize()
        return placed
    }

    fun spill(
        sizeBytes: Int,
        alignBytes: Int = RiscvTargetConfig.REGISTER_BYTES,
        name: String? = null,
        stackSlotId: Int? = null,
    ): PlacedStackObject {
        return place(StackObject(StackObjectKind.Spill, sizeBytes, alignBytes, name, stackSlotId = stackSlotId))
    }

    fun temp(
        sizeBytes: Int,
        alignBytes: Int = RiscvTargetConfig.REGISTER_BYTES,
        name: String? = null,
    ): PlacedStackObject {
        return place(StackObject(StackObjectKind.LoweringTemp, sizeBytes, alignBytes, name))
    }

    fun alloca(sizeBytes: Int, alignBytes: Int, name: String? = null): PlacedStackObject {
        return place(StackObject(StackObjectKind.Alloca, sizeBytes, alignBytes, name))
    }

    fun save(
        register: Register,
        sizeBytes: Int = RiscvTargetConfig.REGISTER_BYTES,
        alignBytes: Int = sizeBytes,
    ): PlacedStackObject {
        return place(
            StackObject(
                kind = StackObjectKind.SavedRegister,
                sizeBytes = sizeBytes,
                alignBytes = alignBytes,
                name = register.name.lowercase(),
                savedRegister = register,
            )
        )
    }

    fun objects(kind: StackObjectKind): List<PlacedStackObject> {
        return frameObjects.filter { it.kind == kind }
    }

    fun objectWithName(name: String): PlacedStackObject? {
        return frameObjects.firstOrNull { it.stackObject.name == name }
    }

    fun objectWithStackSlotId(id: Int): PlacedStackObject? {
        return frameObjects.firstOrNull { it.stackObject.stackSlotId == id }
    }

    private fun refreshSize() {
        frameSizeBytes = alignUp(usedBytes, alignBytes)
        for (placed in frameObjects) {
            placed.offsetFromSp = frameSizeBytes + placed.offsetFromFp
        }
    }
}

class StackManager(
    private val frameAlignBytes: Int = 16,
) {
    private val functionStackFrames = mutableMapOf<Function, StackFrame>()

    fun getStackFrame(function: Function): StackFrame {
        return functionStackFrames.getOrPut(function) { StackFrame(frameAlignBytes) }
    }

    fun hasStackFrame(function: Function): Boolean {
        return function in functionStackFrames
    }

    fun clear(function: Function) {
        functionStackFrames.remove(function)
    }

    fun clear() {
        functionStackFrames.clear()
    }

    fun frames(): Map<Function, StackFrame> {
        return functionStackFrames.toMap()
    }

    fun materializeSpills(
        function: Function,
        allocation: Map<Value, SavableSlot>,
        registerBytes: Int = RiscvTargetConfig.REGISTER_BYTES,
    ): Map<Int, PlacedStackObject> {
        require(registerBytes > 0) { "Register size must be positive" }

        val stackSlots = allocation.filterValues { it is SavableSlot.Stack }
        if (stackSlots.isEmpty()) return emptyMap()

        val frame = getStackFrame(function)
        val slots = linkedMapOf<Int, PlacedStackObject>()

        for ((value, slot) in stackSlots) {
            val stackSlot = slot as SavableSlot.Stack

            val existing = frame.objectWithStackSlotId(stackSlot.stackSlotId)
            if (existing != null) {
                slots[stackSlot.stackSlotId] = existing
                continue
            }

            val layout = value.type.computeLayout(function.module, pointerWidthBits = registerBytes * 8)
            val sizeBytes = layout.sizeInBytes.toIntExact("Stack slot for ${value.getIdentifier()}")
            slots[stackSlot.stackSlotId] = frame.spill(
                sizeBytes = sizeBytes,
                alignBytes = layout.alignment,
                name = value.name,
                stackSlotId = stackSlot.stackSlotId,
            )
        }

        return slots
    }
}

private fun alignUp(value: Int, align: Int): Int {
    val extra = value % align
    if (extra == 0) return value
    return value + align - extra
}

private fun isPowerOfTwo(value: Int): Boolean {
    return value and (value - 1) == 0
}

private fun Long.toIntExact(description: String): Int {
    require(this <= Int.MAX_VALUE) { "$description is too large: $this bytes" }
    return toInt()
}
