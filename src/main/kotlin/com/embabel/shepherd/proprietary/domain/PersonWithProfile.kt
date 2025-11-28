package com.embabel.shepherd.proprietary.domain

import com.embabel.shepherd.community.domain.Person
import com.embabel.shepherd.community.domain.Profile

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