package com.embabel.shepherd.service

import com.embabel.shepherd.domain.Employer
import com.embabel.shepherd.domain.Issue
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.domain.RaisableIssue
import org.drivine.query.MixinTemplate
import org.kohsuke.github.GHIssue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
     * Save the issue and its company and person if not already present
     */
    @Transactional
    fun saveAndExpandIssue(ghIssue: GHIssue): RaisableIssue {
        val issue = Issue.fromGHIssue(ghIssue)
        val saved = mixinTemplate.save(issue)
        val employer = mixinTemplate.findAll(Employer::class.java)
            .find { it.name == ghIssue.user.company }
        val person = mixinTemplate.findAll(Person::class.java)
            .find { it.githubId == ghIssue.user.id } ?: Person(
            uuid = java.util.UUID.randomUUID(),
            name = ghIssue.user.name ?: ghIssue.user.login,
            bio = ghIssue.user.bio ?: "",
            githubId = ghIssue.user.id,
            employer = employer,
        )
        val raisableIssue = saved.withRaisedBy(person)
        mixinTemplate.save(raisableIssue)
        return raisableIssue
    }

}