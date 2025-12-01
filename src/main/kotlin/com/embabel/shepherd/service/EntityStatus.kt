package com.embabel.shepherd.service

/**
 * Result of retrieving or creating an entity.
 */
data class EntityStatus<T>(
    val entity: T,
    val created: Boolean,
) {

    companion object {

        /**
         * Retrieve or create an entity of the given type.
         * Do not save the new entity
         */
        fun <T> retrieveOrCreate(
            retriever: () -> T?,
            creator: () -> T,
        ): EntityStatus<T> {
            val existing = retriever()
            if (existing != null) {
                return EntityStatus(entity = existing, created = false)
            }
            return EntityStatus(entity = creator(), created = true)
        }
    }
}