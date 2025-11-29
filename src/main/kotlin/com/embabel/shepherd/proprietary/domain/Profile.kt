package com.embabel.shepherd.proprietary.domain

import com.embabel.common.core.types.ZeroToOne
import com.embabel.shepherd.community.domain.HasUUID
import com.embabel.shepherd.community.domain.Person
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import java.time.Instant
import java.util.*

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

/**
 * A Person with profile information attached.
 * This is a mixin interface that adds profile to Person using delegation.
 * The more specific type knows about the less specific, not the reverse.
 */
interface PersonWithProfile : Person {
    val profile: Profile

    companion object {
        /**
         * Create a PersonWithProfile from a Person and Profile.
         */
        fun from(person: Person, profile: Profile): PersonWithProfile {
            return object : PersonWithProfile, Person by person {
                override val profile: Profile = profile
            }
        }
    }
}