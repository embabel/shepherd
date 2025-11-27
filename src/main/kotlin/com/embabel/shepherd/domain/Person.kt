package com.embabel.shepherd.domain

import com.embabel.common.core.types.ZeroToOne
import com.fasterxml.jackson.annotation.JsonPropertyDescription
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
        val self = this
        return object : RaisableIssue, Issue by self {
            override val raisedBy: Person = person
        }
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
}


interface IssueAssignment : Issue {
    //    @GraphRelationship(type = "ASSIGNED_TO", direction = Direction.OUTGOING, targetLabel = "Person", alias = "p")
    val assignedTo: Collection<Person>
}

data class Profile(
    override val uuid: UUID = UUID.randomUUID(),
    val retrieved: Instant = Instant.now(),
    val bio: String,
    val homepage: String?,
    @param:JsonPropertyDescription("programming languages as generally written, eg Java, Python or C#")
    val programmingLanguages: Set<String>,
    @param:JsonPropertyDescription("frameworks as generally written, eg Spring or React")
    val frameworks: Set<String>,
    // TODO Should be enum
    val location: String?,
    val email: String?,
    val blog: String?,
    @param:JsonPropertyDescription("How important this profile is to us, from 0 (not important) to 1 (very important)")
    val importance: ZeroToOne,
) : HasUUID


data class Person(
    override val uuid: UUID,
    val name: String,
    val bio: String?,
    val githubId: Long?,
    val employer: Employer?,
    val profile: Profile? = null
) : HasUUID

