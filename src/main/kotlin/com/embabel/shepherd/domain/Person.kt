package com.embabel.shepherd.domain

import com.embabel.common.core.types.ZeroToOne
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import java.time.Instant
import java.util.*


data class Employer(
    override val uuid: UUID,
    val name: String,
) : HasUUID


data class Profile(
    override val uuid: UUID = UUID.randomUUID(),
    val retrieved: Instant = Instant.now(),
    val bio: String,
    val homepage: String?,
    @param:JsonPropertyDescription("programming languages as generally written, eg Java, Python or C#")
    val programmingLanguages: Set<String>,
    @param:JsonPropertyDescription("frameworks as generally written, eg Spring or React")
    val frameworks: Set<String>,
    // TODO Should be enum
    val location: String?,
    val email: String?,
    val blog: String?,
    @param:JsonPropertyDescription("How important this profile is to us, from 0 (not important) to 1 (very important)")
    val importance: ZeroToOne,
) : HasUUID


data class Person(
    override val uuid: UUID,
    val name: String,
    val bio: String?,
    val githubId: Long?,
    val employer: Employer?,
    val profile: Profile? = null
) : HasUUID

