package com.embabel.shepherd.service

import com.embabel.shepherd.domain.Organization

/**
 * Strategy interface for canonicalizing employer/company names.
 * Implementations can provide different matching strategies such as:
 * - Simple string normalization
 * - Fuzzy matching
 * - External API lookups (e.g., Clearbit, LinkedIn)
 * - Custom alias databases
 */
interface OrganizationCanonicalizer {

    /**
     * Canonicalize a company name for comparison.
     * @param organizationName The raw company name
     * @return A normalized/canonical form suitable for matching
     */
    fun canonicalize(organizationName: String): String

    /**
     * Check if a company name matches an existing employer.
     * @param companyName The company name to check
     * @param organization The employer to match against
     * @return true if the company name matches the employer
     */
    fun matches(companyName: String, organization: Organization): Boolean {
        val canonicalInput = canonicalize(companyName)
        return canonicalize(organization.name) == canonicalInput ||
                organization.aliases.any { canonicalize(it) == canonicalInput }
    }
}

/**
 * Regex-based implementation that normalizes company names by:
 * - Trimming whitespace
 * - Converting to lowercase
 * - Removing punctuation (periods, commas)
 * - Removing common company suffixes (Inc, LLC, Ltd, Corp, etc.)
 */
class RegexOrganizationCanonicalizer : OrganizationCanonicalizer {

    override fun canonicalize(organizationName: String): String {
        return organizationName
            .trim()
            .lowercase()
            .replace(Regex("""[.,]"""), "") // Remove punctuation
            .replace(Regex("""\s+(inc|llc|ltd|corp|corporation|company|co)$"""), "") // Remove suffixes
            .trim()
    }
}
