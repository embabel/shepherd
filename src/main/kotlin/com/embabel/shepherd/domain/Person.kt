package com.embabel.shepherd.domain

import java.util.*


data class Employer(
    val name: String,
    val aliases: Set<String> = emptySet(),
    override val uuid: UUID = UUID.randomUUID(),
) : HasUUID {

    /**
     * Check if the given company name matches this employer.
     * Matches against the canonical name or any alias, case-insensitively.
     */
    fun matches(companyName: String): Boolean {
        val normalizedInput = canonicalize(companyName)
        return canonicalize(name) == normalizedInput ||
                aliases.any { canonicalize(it) == normalizedInput }
    }

    companion object {
        /**
         * Normalize a company name for comparison.
         * Converts to lowercase and removes common suffixes and punctuation.
         */
        fun canonicalize(companyName: String): String {
            return companyName
                .trim()
                .lowercase()
                .replace(Regex("""[.,]"""), "") // Remove punctuation
                .replace(Regex("""\s+(inc|llc|ltd|corp|corporation|company|co)$"""), "") // Remove suffixes
                .trim()
        }
    }
}

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

