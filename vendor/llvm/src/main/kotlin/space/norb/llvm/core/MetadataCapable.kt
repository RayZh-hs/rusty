package space.norb.llvm.core

import space.norb.llvm.values.Metadata

/**
 * Interface for entities that can have metadata attached to them.
 */
interface MetadataCapable {
    /**
     * Gets all metadata attached to this entity.
     * @return A map where keys are metadata kinds and values are metadata nodes/strings
     */
    fun getAllMetadata(): Map<String, Metadata>

    /**
     * Gets metadata of a specific kind.
     * @param kind The kind of metadata to retrieve (e.g., "dbg", "tbaa")
     * @return The metadata if present, null otherwise
     */
    fun getMetadata(kind: String): Metadata?

    /**
     * Sets metadata for a specific kind.
     * @param kind The kind of metadata
     * @param metadata The metadata to attach
     */
    fun setMetadata(kind: String, metadata: Metadata)

    /**
     * Checks if this entity has any metadata.
     * @return true if there is at least one metadata attachment
     */
    fun hasMetadata(): Boolean = getAllMetadata().isNotEmpty()

    /**
     * Removes metadata of a specific kind.
     * @param kind The kind of metadata to remove
     */
    fun removeMetadata(kind: String)
}
