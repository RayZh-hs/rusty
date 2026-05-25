package space.norb.llvm.instructions.base

import space.norb.llvm.core.User
import space.norb.llvm.core.Value
import space.norb.llvm.core.Type
import space.norb.llvm.core.MetadataCapable
import space.norb.llvm.values.Metadata
import space.norb.llvm.structure.BasicBlock

/**
 * Base class for all LLVM instructions.
 */
abstract class Instruction(
    override val name: String?,
    override val type: Type,
    operands: List<Value>
) : User(name, type, operands), MetadataCapable {
    var inlineComment: String? = null
    
    private val metadataAttachments = mutableMapOf<String, Metadata>()

    override fun getAllMetadata(): Map<String, Metadata> = metadataAttachments.toMap()

    override fun getMetadata(kind: String): Metadata? = metadataAttachments[kind]

    override fun setMetadata(kind: String, metadata: Metadata) {
        metadataAttachments[kind] = metadata
    }

    override fun removeMetadata(kind: String) {
        metadataAttachments.remove(kind)
    }
}
