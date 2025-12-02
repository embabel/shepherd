package com.embabel.shepherd.service

import com.embabel.shepherd.domain.*
import org.drivine.query.MixinTemplate
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CommunityDataManager(
    val mixinTemplate: MixinTemplate,
    private val organizationCanonicalizer: OrganizationCanonicalizer = RegexOrganizationCanonicalizer(),
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
     * Retrieve an existing repository by owner and name, or create a new one.
     */
    @Transactional
    fun retrieveOrCreateRepository(owner: String, name: String): EntityStatus<GitHubRepository> {
        return EntityStatus.retrieveOrCreate(
            {
                // TODO make efficient
                mixinTemplate.findAll(GitHubRepository::class.java)
                    .find { it.owner == owner && it.name == name }
            }
        ) {
            NewEntity(GitHubRepository(owner = owner, name = name), emptyList())
        }
    }

    /**
     * Retrieve an existing employer by company name, or create a new one.
     * Uses the configured EmployerCanonicalizer for matching.
     */
    @Transactional
    fun retrieveOrCreateEmployer(companyName: String?): EntityStatus<Organization>? {
        if (companyName.isNullOrBlank()) {
            return null
        }

        return EntityStatus.retrieveOrCreate(
            {
                // Find employer using the canonicalizer's matching logic
                mixinTemplate.findAll(Organization::class.java)
                    .find { organizationCanonicalizer.matches(companyName, it) }
            }
        ) {
            // Create new employer with the original company name as canonical
            // Add normalized variant as alias if different from the original
            val canonical = organizationCanonicalizer.canonicalize(companyName)
            val aliases = if (canonical != companyName.lowercase()) {
                setOf(canonical)
            } else {
                emptySet()
            }
            NewEntity(Organization(name = companyName, aliases = aliases), emptyList())
        }
    }

    /**
     * Retrieve an existing person by GitHub ID, or create a new one from the GHUser.
     * Do not save the person as we may further change it within this transaction
     */
    @Transactional
    fun retrieveOrCreatePersonFrom(ghUser: GHUser): EntityStatus<Person> {
        val employerStatus = retrieveOrCreateEmployer(ghUser.company)

        return EntityStatus.retrieveOrCreate({
            // TODO inefficient. Improve when we have proper querying
            mixinTemplate.findAll(Person::class.java)
                .find { it.github?.login == ghUser.login }
        }) {
            NewEntity(
                Person.fromGHUser(ghUser, employerStatus?.entity),
                otherNewEntities = employerStatus?.newEntities ?: emptyList(),
            )
        }
    }

    /**
     * Save the issue and the raiser and company if not already present
     */
    @Transactional
    fun saveAndExpandIssue(ghIssue: GHIssue): EntityStatus<Issue> {
        val ghRepository = ghIssue.repository
        val repositoryStatus = retrieveOrCreateRepository(
            owner = ghRepository.ownerName,
            name = ghRepository.name
        )
        val repository = repositoryStatus.entity
        if (repositoryStatus.created) {
            mixinTemplate.save(repository)
        }

        val issue = if (ghIssue is GHPullRequest) {
            PullRequest.fromGHPullRequest(ghIssue, repository)
        } else {
            Issue.fromGHIssue(ghIssue, repository)
        }
        val personStatus = retrieveOrCreatePersonFrom(ghIssue.user)
        val raisableIssue = RaisableIssue.from(issue, personStatus.entity)
        mixinTemplate.save(raisableIssue)

        return NewEntity(
            entity = raisableIssue,
            otherNewEntities = personStatus.newEntities,
        )
    }

    /**
     * Record that a person starred a repository at the given time.
     */
    @Transactional
    fun recordStar(person: Person, repoId: RepoId, starredAt: Instant) {
        // TODO create a relationship with the property
        println("******** Recording star: person='${person.github?.login}', repo=$repoId")
    }

    @Transactional
    fun <T> save(entity: T): T {
        return mixinTemplate.save(entity)
    }

}