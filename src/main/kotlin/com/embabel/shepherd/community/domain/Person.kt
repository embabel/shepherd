package com.embabel.shepherd.community.domain

import com.embabel.common.core.types.ZeroToOne
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import java.time.Instant
import java.util.*


data class Employer(
    override val uuid: UUID,
    val name: String,
) : HasUUID

/**
 * Information we've retrieved about a person's profile
 * Some of this is subjective, some from general sources
 */
data class Profile(
    override val uuid: UUID = UUID.randomUUID(),
    @param:JsonPropertyDescription("when this profile was updated")
    val updated: Instant = Instant.now(),
    val bio: String,
    val homepage: String?,
    @param:JsonPropertyDescription("programming languages as generally written, eg Java, Python or C#")
    val programmingLanguages: Set<String>,
    @param:JsonPropertyDescription("frameworks as generally written, eg Spring or React")
    val frameworks: Set<String>,
    @param:JsonPropertyDescription("2 digit lowercase country code if known")
    val countryCode: String? = null,
    @param:JsonPropertyDescription("region within country if known")
    val region: String? = null,
    val email: String? = null,
    val blog: String? = null,
    val linkedInId: String? = null,
    val twitterHandle: String? = null,
    @param:JsonPropertyDescription("How important this profile is to us, from 0 (not important) to 1 (very important)")
    val importance: ZeroToOne,
    @param:JsonPropertyDescription("categorization of the person, any of set of values that applies")
    val categories: Set<String>,
) : HasUUID

data class Person(
    override val uuid: UUID,
    val name: String,
    val bio: String?,
    val githubId: Long?,
    val employer: Employer?,
    val profile: Profile? = null
) : HasUUID

