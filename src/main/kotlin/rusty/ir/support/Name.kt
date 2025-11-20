package rusty.ir.support

class Name(val identifier: String) {
    companion object {
        fun ofStruct(identifier: String): Name {
            return Name("user.struct.$identifier")
        }
    }
}