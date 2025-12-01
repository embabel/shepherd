package com.embabel.shepherd.domain

import java.util.*

interface Employer : HasUUID {
    val name: String
    val aliases: Set<String>

    companion object {

        operator fun invoke(
            name: String,
            aliases: Set<String> = emptySet(),
            uuid: UUID = UUID.randomUUID(),
        ): Employer = EmployerImpl(
            name = name,
            aliases = aliases,
            uuid = uuid,
        )
    }
}

internal data class EmployerImpl(
    override val name: String,
    override val aliases: Set<String>,
    override val uuid: UUID,
) : Employer
