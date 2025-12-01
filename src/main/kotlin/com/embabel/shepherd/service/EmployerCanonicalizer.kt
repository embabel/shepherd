package com.embabel.shepherd.service

import com.embabel.shepherd.domain.Employer

/**
 * Strategy interface for canonicalizing employer/company names.
 * Implementations can provide different matching strategies such as:
 * - Simple string normalization
 * - Fuzzy matching
 * - External API lookups (e.g., Clearbit, LinkedIn)
 * - Custom alias databases
 */
interface EmployerCanonicalizer {

    /**
     * Canonicalize a company name for comparison.
     * @param companyName The raw company name
     * @return A normalized/canonical form suitable for matching
     */
    fun canonicalize(companyName: String): String

    /**
     * Check if a company name matches an existing employer.
     * @param companyName The company name to check
     * @param employer The employer to match against
     * @return true if the company name matches the employer
     */
    fun matches(companyName: String, employer: Employer): Boolean {
        val canonicalInput = canonicalize(companyName)
        return canonicalize(employer.name) == canonicalInput ||
                employer.aliases.any { canonicalize(it) == canonicalInput }
    }
}

/**
 * Regex-based implementation that normalizes company names by:
 * - Trimming whitespace
 * - Converting to lowercase
 * - Removing punctuation (periods, commas)
 * - Removing common company suffixes (Inc, LLC, Ltd, Corp, etc.)
 */
class RegexEmployerCanonicalizer : EmployerCanonicalizer {

    override fun canonicalize(companyName: String): String {
        return companyName
            .trim()
            .lowercase()
            .replace(Regex("""[.,]"""), "") // Remove punctuation
            .replace(Regex("""\s+(inc|llc|ltd|corp|corporation|company|co)$"""), "") // Remove suffixes
            .trim()
    }
}
