package com.embabel.shepherd.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*

/**
 * A GitHub repository.
 */
data class Repository(
    val owner: String,
    val name: String,
    override val uuid: UUID = UUID.randomUUID(),
) : HasUUID {

    /**
     * The full name of the repository (owner/name).
     */
    val fullName: String
        @JsonIgnore get() = "$owner/$name"

    companion object {
        /**
         * Parse a full repository name (owner/name) into a Repository.
         */
        fun fromFullName(fullName: String): Repository {
            val parts = fullName.split("/")
            require(parts.size == 2) { "Invalid repository full name: $fullName" }
            return Repository(owner = parts[0], name = parts[1])
        }
    }
}
