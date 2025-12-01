package com.embabel.shepherd.domain

import java.util.*

/**
 * Not a github organization, but an actual organization (company, non-profit, etc.)
 */
interface Organization : HasUUID {
    val name: String
    val aliases: Set<String>

    companion object {

        operator fun invoke(
            name: String,
            aliases: Set<String> = emptySet(),
            uuid: UUID = UUID.randomUUID(),
        ): Organization = OrganizationImpl(
            name = name,
            aliases = aliases,
            uuid = uuid,
        )
    }
}

internal data class OrganizationImpl(
    override val name: String,
    override val aliases: Set<String>,
    override val uuid: UUID,
) : Organization
