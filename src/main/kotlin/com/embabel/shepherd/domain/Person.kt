package com.embabel.shepherd.domain

import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueStateReason
import java.time.Instant
import java.util.*


/**
 * Embabel synthetic identifier
 */
interface HasUUID {
    val uuid: UUID
}

data class Employer(
    override val uuid: UUID,
    val name: String,
) : HasUUID


interface Issue : HasUUID {
    /**
     * The unique identifier of the issue from github
     */
    val id: Long
    val state: String?
    val stateReason: GHIssueStateReason?
    val number: Int
    val closedAt: String?

    //    val comments: Int
    val body: String?
    val title: String?
    val htmlUrl: String?
    val locked: Boolean
    val company: String?

    fun withRaisedBy(person: Person): RaisableIssue {
        return RaisableIssueImpl(
            uuid = this.uuid,
            id = this.id,
            state = this.state,
            stateReason = this.stateReason,
            number = this.number,
            closedAt = this.closedAt,
            body = this.body,
            title = this.title,
            htmlUrl = this.htmlUrl,
            locked = this.locked,
            company = this.company,
            raisedBy = person,
        )
    }

    companion object {

        fun fromGHIssue(ghIssue: GHIssue): Issue {
            val issue = IssueImpl(
                uuid = UUID.randomUUID(),
                id = ghIssue.id,
                state = ghIssue.state?.name,
                stateReason = ghIssue.stateReason,
                number = ghIssue.number,
                closedAt = ghIssue.closedAt?.toString(),
//                comments = ghIssue.comments,
                body = ghIssue.body,
                title = ghIssue.title,
                htmlUrl = ghIssue.htmlUrl.toString(),
                locked = ghIssue.isLocked,
                company = ghIssue.user.company,
            )
            return issue
        }
    }
}

data class IssueImpl(
    override val uuid: UUID,
    override val id: Long,
    override val state: String?,
    override val stateReason: GHIssueStateReason?,
    override val number: Int,
    override val closedAt: String?,
//    override val comments: Int,
    override val body: String?,
    override val title: String?,
    override val htmlUrl: String?,
    override val locked: Boolean,
    override val company: String?,
) : Issue


interface RaisableIssue : Issue {

    //    @GraphRelationship(type = "RAISED_BY", direction = Direction.INCOMING, targetLabel = "Person", alias = "p")
    val raisedBy: Person
}

data class RaisableIssueImpl(
    override val uuid: UUID,
    override val id: Long,
    override val state: String?,
    override val stateReason: GHIssueStateReason?,
    override val number: Int,
    override val closedAt: String?,
    override val body: String?,
    override val title: String?,
    override val htmlUrl: String?,
    override val locked: Boolean,
    override val company: String?,
    override val raisedBy: Person,
) : RaisableIssue

interface IssueAssignment : Issue {
    //    @GraphRelationship(type = "ASSIGNED_TO", direction = Direction.OUTGOING, targetLabel = "Person", alias = "p")
    val assignedTo: Collection<Person>
}

data class Profile(
    override val uuid: UUID = UUID.randomUUID(),
    val retrieved: Instant = Instant.now(),
    val bio: String,
    val homepage: String?,
    val location: String?,
    val email: String?,
    val blog: String?,
) : HasUUID


data class Person(
    override val uuid: UUID,
    val name: String,
    val bio: String?,
    val githubId: Long?,
    val employer: Employer?,
    val profile: Profile? = null
) : HasUUID

