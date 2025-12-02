package com.embabel.shepherd.domain

import java.util.*

/**
 * Person who is a member of the community.
 */
interface Person : HasUUID {
    val name: String
    val bio: String?
    val githubLogin: String?
    val employer: Organization?

    companion object {

        operator fun invoke(
            uuid: UUID = UUID.randomUUID(),
            name: String,
            bio: String? = null,
            githubLogin: String? = null,
            employer: Organization? = null,
        ): Person = PersonImpl(
            uuid = uuid,
            name = name,
            bio = bio,
            githubLogin = githubLogin,
            employer = employer,
        )
    }
}

internal data class PersonImpl(
    override val uuid: UUID,
    override val name: String,
    override val bio: String?,
    override val githubLogin: String?,
    override val employer: Organization?,
) : Person

