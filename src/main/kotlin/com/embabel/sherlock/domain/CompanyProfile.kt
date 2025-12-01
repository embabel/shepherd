package com.embabel.sherlock.domain

import com.embabel.shepherd.domain.Employer
import com.embabel.shepherd.domain.HasUUID
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import java.time.Instant
import java.util.*

data class CompanyProfile(
    override val uuid: UUID = UUID.randomUUID(),
    @param:JsonPropertyDescription("when this profile was updated")
    val updated: Instant = Instant.now(),
    val description: String,
    val homepage: String,
    val industry: String,
    val categories: Set<String>,

    // TODO is hobbyist or real
) : HasUUID

/**
 * An Employer with profile information attached.
 * This is a mixin interface that adds profile to Employer using delegation.
 * The more specific type knows about the less specific, not the reverse.
 */
interface EmployerWithProfile : Employer {
    val profile: CompanyProfile

    companion object {
        /**
         * Create an EmployerWithProfile from an Employer and CompanyProfile.
         */
        fun from(employer: Employer, profile: CompanyProfile): EmployerWithProfile {
            return object : EmployerWithProfile, Employer by employer {
                override val profile: CompanyProfile = profile
            }
        }
    }
}
