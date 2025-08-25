package rusty.semantic.support

import rusty.core.CompilerPointer

data class Annotation (val pointer: CompilerPointer?, val name: String?) {
    companion object {
        fun from(pointer: CompilerPointer? = null, name: String? = null): Annotation {
            return Annotation(pointer, name)
        }
    }
}