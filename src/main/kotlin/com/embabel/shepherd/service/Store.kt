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
     * A person retrieval result, indicating whether the person already existed or was newly created.
     */
    data class PersonRetrieval(
        val person: Person,
        val existing: Boolean,
    )

    /**
     * Retrieve an existing person by GitHub ID, or create a new one from the GHUser.
     * Do not save the person as we may further change it within this transaction
     */
    @Transactional
    fun retrieveOrCreatePersonFrom(ghUser: GHUser): PersonRetrieval {
        val existingPerson = mixinTemplate.findAll(Person::class.java)
            .find { it.githubId == ghUser.id }

        if (existingPerson != null) {
            return PersonRetrieval(person = existingPerson, existing = true)
        }

        val employer = mixinTemplate.findAll(Employer::class.java)
            .find { it.name == ghUser.company }

        val newPerson = Person(
            uuid = UUID.randomUUID(),
            name = ghUser.name ?: ghUser.login,
            bio = ghUser.bio ?: "",
            githubId = ghUser.id,
            employer = employer,
        )

        return PersonRetrieval(person = newPerson, existing = false)
    }

    /**
     * Save the issue and its company and person if not already present
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
        val raisableIssue = RaisableIssue.from(saved, personRetrieval.person)
        mixinTemplate.save(raisableIssue)

        return IssueStorageResult(
            issue = raisableIssue,
            newPerson = if (personRetrieval.existing) null else personRetrieval.person,
        )
    }

    fun <T> save(entity: T): T {
        return mixinTemplate.save(entity)
    }

}