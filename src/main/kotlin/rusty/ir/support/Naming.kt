package rusty.ir.support

class Naming {
    companion object {
        fun ofStruct(identifier: String): String {
            return "user.struct.$identifier"
        }
    }
}