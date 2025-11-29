package com.embabel.shepherd.domain

import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueStateReason
import java.util.*

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

        /**
         * Create an Issue for testing purposes.
         */
        fun create(
            uuid: UUID = UUID.randomUUID(),
            id: Long,
            state: String? = null,
            stateReason: GHIssueStateReason? = null,
            number: Int,
            closedAt: String? = null,
            body: String? = null,
            title: String? = null,
            htmlUrl: String? = null,
            locked: Boolean = false,
            company: String? = null,
        ): Issue = IssueImpl(
            uuid = uuid,
            id = id,
            state = state,
            stateReason = stateReason,
            number = number,
            closedAt = closedAt,
            body = body,
            title = title,
            htmlUrl = htmlUrl,
            locked = locked,
            company = company,
        )
    }
}

internal data class IssueImpl(
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

    companion object {
        /**
         * Create a RaisableIssue from an Issue and Person.
         */
        fun from(issue: Issue, raisedBy: Person): RaisableIssue {
            return object : RaisableIssue, Issue by issue {
                override val raisedBy: Person = raisedBy
            }
        }
    }
}


interface IssueAssignment : Issue {
    //    @GraphRelationship(type = "ASSIGNED_TO", direction = Direction.OUTGOING, targetLabel = "Person", alias = "p")
    val assignedTo: Collection<Person>
}