package com.embabel.shepherd.domain

import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueStateReason
import org.kohsuke.github.GitHub
import java.time.Instant
import java.util.*

interface Issue : HasUUID, ForeignObject<GitHub, GHIssue> {
    /**
     * The unique identifier of the issue from github
     */
    val id: Long
    val updatedAt: Instant
    val gitHubRepository: GitHubRepository
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

    /**
     * Get the ful issue details from GitHub API.
     * Parses the htmlUrl to extract repo owner/name and issue number.
     */
    override fun materialize(service: GitHub): GHIssue {
        // htmlUrl format: https://github.com/owner/repo/issues/123
        val url = htmlUrl ?: throw IllegalStateException("Cannot materialize issue without htmlUrl")
        val regex = Regex("github\\.com/([^/]+)/([^/]+)/(?:issues|pull)/(\\d+)")
        val match = regex.find(url)
            ?: throw IllegalStateException("Cannot parse GitHub URL: $url")

        val (owner, repo, _) = match.destructured
        return service.getRepository("$owner/$repo").getIssue(number)
    }

    override fun sync(service: GitHub) {
        val ghIssue = materialize(service)
        // No-op for now. In a real implementation, we would update the local Issue fields.
        TODO("Implement sync logic to update local Issue from ghIssue")
    }

    companion object {

        fun fromGHIssue(ghIssue: GHIssue, gitHubRepository: GitHubRepository): Issue {
            val issue = IssueImpl(
                uuid = UUID.randomUUID(),
                id = ghIssue.id,
                gitHubRepository = gitHubRepository,
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
            gitHubRepository: GitHubRepository,
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
            gitHubRepository = gitHubRepository,
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
    override val gitHubRepository: GitHubRepository,
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
    override val updatedAt: Instant = Instant.now(),
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