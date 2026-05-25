package space.norb.llvm.structure

import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.core.MetadataCapable
import space.norb.llvm.values.Metadata
import space.norb.llvm.types.FunctionType
import space.norb.llvm.visitors.IRVisitor
import space.norb.llvm.enums.LinkageType

typealias FunctionId = ULong

/**
 * Function
 *
 * Represents a function in LLVM IR. Each function has a name, type, and is associated with a parent module.
 *
 * @param name The name of the function, which serves as its identifier within the module.
 * @param type The function type, which includes the return type and parameter types.
 * @param module The parent module to which this function belongs.
 * @param linkage The linkage type of the function, which determines its visibility and linkage behavior.
 * @param isDeclaration Indicates whether this function is a declaration (i.e., it has no body) or a definition (i.e., it has a body with basic blocks).
 */
class Function(
    override val name: String?,
    override val type: FunctionType,
    val module: Module,
    val linkage: LinkageType = LinkageType.EXTERNAL,
    val isDeclaration: Boolean = false
) : Value, MetadataCapable {
    companion object {
        private var functionId: FunctionId = 0UL
        private var functionIdHashmap = mutableMapOf<FunctionId, Function>()
        private fun register(function: Function): FunctionId {
            val id = functionId++
            functionIdHashmap[id] = function
            return id
        }

        @Suppress("Unused")
        fun fromId(id: FunctionId) = functionIdHashmap[id]
    }

    val returnType: Type = type.returnType
    val parameters: List<Argument> = type.paramTypes.mapIndexed { index, paramType ->
        Argument(type.getParameterName(index), paramType, this, index)
    }
    val basicBlocks: MutableList<BasicBlock> = mutableListOf()
    var entryBlock: BasicBlock? = null
    val id: FunctionId = register(this)
    val attributes: MutableSet<String> = linkedSetOf()
    
    private val metadataAttachments = mutableMapOf<String, Metadata>()

    override fun getAllMetadata(): Map<String, Metadata> = metadataAttachments.toMap()

    override fun getMetadata(kind: String): Metadata? = metadataAttachments[kind]

    override fun setMetadata(kind: String, metadata: Metadata) {
        metadataAttachments[kind] = metadata
    }

    override fun removeMetadata(kind: String) {
        metadataAttachments.remove(kind)
    }

    override fun <T> accept(visitor: IRVisitor<T>): T = visitor.visitFunction(this)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Function) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Function(name=$name, type=$type, module=${module.name})"
    }
    
    override fun getParent(): Any {
        // Functions belong to modules
        return module
    }

    fun addAttribute(attribute: String): Function {
        require(attribute.isNotBlank()) { "Function attribute cannot be blank" }
        attributes.add(attribute)
        return this
    }

    fun removeAttribute(attribute: String): Function {
        attributes.remove(attribute)
        return this
    }

    fun hasAttribute(attribute: String): Boolean = attribute in attributes

    fun setBasicBlock(block: BasicBlock): Function {
        this.entryBlock = block
        this.basicBlocks.clear()
        this.basicBlocks.add(block)
        return this
    }

    fun setBasicBlocks(blocks: List<BasicBlock>): Function {
        if (blocks.isNotEmpty()) {
            this.entryBlock = blocks[0]
        } else {
            throw IllegalArgumentException("Basic blocks list cannot be empty")
        }
        this.basicBlocks.clear()
        this.basicBlocks.addAll(blocks)
        return this
    }
    
    /**
     * Insert a new basic block into this function.
     *
     * @param name The name of the basic block
     * @param setAsEntrypoint Whether to set this block as the entry point.
     *                        If true, this block becomes the entryBlock.
     *                        If this is the first block being added, it defaults to being the entrypoint.
     * @return The created BasicBlock
     */
    fun insertBasicBlock(name: String?, setAsEntrypoint: Boolean = false): BasicBlock {
        val block = BasicBlock(name, this)
        
        // Check if this is the first block being added
        val isFirstBlock = basicBlocks.isEmpty()
        
        // Add the block to the function's basic blocks list
        basicBlocks.add(block)
        
        // Set as entrypoint if requested or if this is the first block
        if (setAsEntrypoint || isFirstBlock) {
            entryBlock = block
        }
        
        return block
    }
}
