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
) {

    @Transactional(readOnly = true)
    fun findIssueByGithubId(id: Long): Issue? {
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
     * A retrieval result, indicating whether the entity already existed or was newly created.
     */
    data class RetrieveOrCreate<T>(
        val entity: T,
        val existing: Boolean,
    )

    /**
     * Retrieve or create an entity of the given type.
     * Do not save the new entity
     */
    @Transactional
    fun <T> retrieveOrCreate(
        retriever: () -> T?,
        creator: () -> T,
    ): RetrieveOrCreate<T> {
        val existing = retriever()
        if (existing != null) {
            return RetrieveOrCreate(entity = existing, existing = true)
        }
        return RetrieveOrCreate(entity = creator(), existing = false)
    }

    /**
     * Retrieve an existing person by GitHub ID, or create a new one from the GHUser.
     * Do not save the person as we may further change it within this transaction
     */
    @Transactional
    fun retrieveOrCreatePersonFrom(ghUser: GHUser): RetrieveOrCreate<Person> {
        val employer = ghUser.company?.let { company ->
            retrieveOrCreate(
                {
                    mixinTemplate.findAll(Employer::class.java)
                        .find { it.name == company }
                }) {
                Employer(name = company)
            }
        }

        return retrieveOrCreate({
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

        val personRetrieval = retrieveOrCreatePersonFrom(ghIssue.user)
        val raisableIssue = RaisableIssue.from(saved, personRetrieval.entity)
        mixinTemplate.save(raisableIssue)

        return IssueStorageResult(
            issue = raisableIssue,
            newPerson = if (personRetrieval.existing) null else personRetrieval.entity,
        )
    }

    fun <T> save(entity: T): T {
        return mixinTemplate.save(entity)
    }

}