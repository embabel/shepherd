package com.embabel.shepherd.domain

import java.util.*


data class Employer(
    val name: String,
    val aliases: Set<String> = emptySet(),
    override val uuid: UUID = UUID.randomUUID(),
) : HasUUID

interface Person : HasUUID {
    val name: String
    val bio: String?
    val githubId: Long?
    val employer: Employer?

    companion object {

        operator fun invoke(
            uuid: UUID = UUID.randomUUID(),
            name: String,
            bio: String? = null,
            githubId: Long? = null,
            employer: Employer? = null,
        ): Person = PersonImpl(
            uuid = uuid,
            name = name,
            bio = bio,
            githubId = githubId,
            employer = employer,
        )
    }
}

internal data class PersonImpl(
    override val uuid: UUID,
    override val name: String,
    override val bio: String?,
    override val githubId: Long?,
    override val employer: Employer?,
) : Person

