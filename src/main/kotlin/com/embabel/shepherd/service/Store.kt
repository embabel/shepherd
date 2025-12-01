package com.embabel.shepherd.service

import com.embabel.shepherd.domain.*
import org.drivine.query.MixinTemplate
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class Store(
    val mixinTemplate: MixinTemplate,
    private val employerCanonicalizer: EmployerCanonicalizer = RegexEmployerCanonicalizer(),
) {

    @Transactional(readOnly = true)
    fun findIssueByGithubId(id: Long): Issue? {
        // TODO remove once we have an efficient way of doing this
        return mixinTemplate.findAll(Issue::class.java)
            .find {
                it.id == id
            }
    }

    /**
     * The issue and any new subgraph elements we saved
     * when we stored it
     */
    data class IssueStorageResult(
        val issue: RaisableIssue,
        val newPerson: Person?,
    )

    /**
     * Retrieve an existing employer by company name, or create a new one.
     * Uses the configured EmployerCanonicalizer for matching.
     */
    @Transactional
    fun retrieveOrCreateEmployer(companyName: String?): EntityStatus<Employer>? {
        if (companyName.isNullOrBlank()) {
            return null
        }

        return EntityStatus.retrieveOrCreate(
            {
                // Find employer using the canonicalizer's matching logic
                mixinTemplate.findAll(Employer::class.java)
                    .find { employerCanonicalizer.matches(companyName, it) }
            }
        ) {
            // Create new employer with the original company name as canonical
            // Add normalized variant as alias if different from the original
            val canonical = employerCanonicalizer.canonicalize(companyName)
            val aliases = if (canonical != companyName.lowercase()) {
                setOf(canonical)
            } else {
                emptySet()
            }
            Employer(name = companyName, aliases = aliases)
        }
    }

    /**
     * Retrieve an existing person by GitHub ID, or create a new one from the GHUser.
     * Do not save the person as we may further change it within this transaction
     */
    @Transactional
    fun retrieveOrCreatePersonFrom(ghUser: GHUser): EntityStatus<Person> {
        val employer = retrieveOrCreateEmployer(ghUser.company)

        return EntityStatus.retrieveOrCreate({
            // TODO inefficient. Improve when we have proper querying
            mixinTemplate.findAll(Person::class.java)
                .find { it.githubId == ghUser.id }
        }) {
            Person(
                uuid = UUID.randomUUID(),
                name = ghUser.name ?: ghUser.login,
                bio = ghUser.bio ?: "",
                githubId = ghUser.id,
                employer = employer?.entity,
            )
        }
    }

    /**
     * Save the issue and its person and company if not already present
     */
    @Transactional
    fun saveAndExpandIssue(ghIssue: GHIssue): IssueStorageResult {
        val issue = if (ghIssue is GHPullRequest) {
            PullRequest.fromGHPullRequest(ghIssue)
        } else {
            Issue.fromGHIssue(ghIssue)
        }
        val saved = mixinTemplate.save(issue)

        val personStatus = retrieveOrCreatePersonFrom(ghIssue.user)
        val raisableIssue = RaisableIssue.from(saved, personStatus.entity)
        mixinTemplate.save(raisableIssue)

        return IssueStorageResult(
            issue = raisableIssue,
            newPerson = if (personStatus.created) personStatus.entity else null,
        )
    }

    fun <T> save(entity: T): T {
        return mixinTemplate.save(entity)
    }

}