package com.embabel.shepherd.service

sealed interface EntityStatus<T : Any> {
    val entity: T
    val created: Boolean

    /**
     * All entities created along with this one
     */
    val newEntities: List<Any>

    companion object {

        /**
         * Retrieve or create an entity of the given type.
         * Do not save the new entity
         */
        fun <T : Any> retrieveOrCreate(
            retriever: () -> T?,
            creator: () -> NewEntity<T>,
        ): EntityStatus<T> {
            val existing = retriever()
            if (existing != null) {
                return ExistingEntity(entity = existing)
            }
            return creator()
        }
    }
}


/**
 * A new entity that may have new related entities to be saved.
 * For example, a new Person with new Employers.
 * @param T the type of the main entity
 * @param newEntities the list of notable related entities to be saved
 */
data class NewEntity<T : Any>(
    override val entity: T,
    private val otherNewEntities: List<Any>,
) : EntityStatus<T> {
    override val created: Boolean = true

    override val newEntities: List<Any>
        get() = listOf(entity) + otherNewEntities
}

/**
 * An existing entity that may have new related entities to be saved.
 * For example, an existing Person with new Employers.
 * @param T the type of the main entity
 */
data class ExistingEntity<T : Any>(
    override val entity: T,
) : EntityStatus<T> {
    override val created: Boolean = false

    override val newEntities: List<Any> = emptyList()
}