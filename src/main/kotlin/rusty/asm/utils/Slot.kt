package rusty.asm.utils

sealed class SavableSlot {
    data class Register(val physical: rusty.asm.utils.Register) : SavableSlot()
    data class Stack(val stackSlotId: Int) : SavableSlot()

    override fun toString(): String = when (this) {
        is Register -> physical.toString()
        is Stack -> "stack(ssid=$stackSlotId)"
    }
}