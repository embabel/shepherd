package com.embabel.shepherd.domain

import org.kohsuke.github.GHIssueStateReason
import org.kohsuke.github.GHPullRequest
import java.util.*

interface PullRequest : Issue {
    val additions: Int
    val deletions: Int
    val changedFiles: Int
    val commits: Int
    val merged: Boolean
    val mergeable: Boolean?
    val mergeableState: String?
    val mergeCommitSha: String?
    val mergedAt: String?
    val draft: Boolean
    val diffUrl: String?
    val patchUrl: String?
    val baseBranch: String?
    val headBranch: String?
    val baseRepo: String?
    val headRepo: String?

    companion object {
        fun fromGHPullRequest(ghPr: GHPullRequest): PullRequest {
            return PullRequestImpl(
                uuid = UUID.randomUUID(),
                id = ghPr.id,
                state = ghPr.state?.name,
                stateReason = ghPr.stateReason,
                number = ghPr.number,
                closedAt = ghPr.closedAt?.toString(),
                body = ghPr.body,
                title = ghPr.title,
                htmlUrl = ghPr.htmlUrl.toString(),
                locked = ghPr.isLocked,
                company = ghPr.user.company,
                additions = ghPr.additions,
                deletions = ghPr.deletions,
                changedFiles = ghPr.changedFiles,
                commits = ghPr.commits,
                merged = ghPr.isMerged,
                mergeable = ghPr.mergeable,
                mergeableState = ghPr.mergeableState,
                mergeCommitSha = ghPr.mergeCommitSha,
                mergedAt = ghPr.mergedAt?.toString(),
                draft = ghPr.isDraft,
                diffUrl = ghPr.diffUrl?.toString(),
                patchUrl = ghPr.patchUrl?.toString(),
                baseBranch = ghPr.base?.ref,
                headBranch = ghPr.head?.ref,
                baseRepo = ghPr.base?.repository?.fullName,
                headRepo = ghPr.head?.repository?.fullName,
            )
        }

        /**
         * Create a PullRequest for testing purposes.
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
            additions: Int = 0,
            deletions: Int = 0,
            changedFiles: Int = 0,
            commits: Int = 0,
            merged: Boolean = false,
            mergeable: Boolean? = null,
            mergeableState: String? = null,
            mergeCommitSha: String? = null,
            mergedAt: String? = null,
            draft: Boolean = false,
            diffUrl: String? = null,
            patchUrl: String? = null,
            baseBranch: String? = null,
            headBranch: String? = null,
            baseRepo: String? = null,
            headRepo: String? = null,
        ): PullRequest = PullRequestImpl(
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
            additions = additions,
            deletions = deletions,
            changedFiles = changedFiles,
            commits = commits,
            merged = merged,
            mergeable = mergeable,
            mergeableState = mergeableState,
            mergeCommitSha = mergeCommitSha,
            mergedAt = mergedAt,
            draft = draft,
            diffUrl = diffUrl,
            patchUrl = patchUrl,
            baseBranch = baseBranch,
            headBranch = headBranch,
            baseRepo = baseRepo,
            headRepo = headRepo,
        )
    }
}

internal data class PullRequestImpl(
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
    override val additions: Int,
    override val deletions: Int,
    override val changedFiles: Int,
    override val commits: Int,
    override val merged: Boolean,
    override val mergeable: Boolean?,
    override val mergeableState: String?,
    override val mergeCommitSha: String?,
    override val mergedAt: String?,
    override val draft: Boolean,
    override val diffUrl: String?,
    override val patchUrl: String?,
    override val baseBranch: String?,
    override val headBranch: String?,
    override val baseRepo: String?,
    override val headRepo: String?,
) : PullRequest