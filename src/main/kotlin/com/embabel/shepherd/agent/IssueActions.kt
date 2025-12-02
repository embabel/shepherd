package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.api.common.OperationContext
import com.embabel.common.core.types.ZeroToOne
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.service.CommunityDataManager
import com.embabel.shepherd.service.DummyGitHubUpdater
import com.embabel.shepherd.service.GitHubUpdater
import com.embabel.shepherd.service.NewEntity
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import org.slf4j.LoggerFactory

data class IssueAssessment(
    val comment: String,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the urgency of addressing this issue, where 1 is most urgent")
    val urgency: ZeroToOne,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the sentiment of the person who opened the issue, where 0 is negative and 1 is positive")
    val sentiment: ZeroToOne,
    @field:JsonPropertyDescription("Labels to apply to the issue")
    val labels: Set<String>,
)

data class PullRequestAssessment(
    val comment: String,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the urgency of addressing this issue, where 1 is most urgent")
    val urgency: ZeroToOne,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the sentiment of the person who opened the issue, where 0 is negative and 1 is positive")
    val sentiment: ZeroToOne,
    @field:JsonPropertyDescription("Labels to apply to the issue")
    val labels: Set<String>,
    val filesChanged: Int = -1,
)


@EmbabelComponent
class IssueActions(
    val properties: ShepherdProperties,
    private val communityDataManager: CommunityDataManager,
    private val gitHubUpdater: GitHubUpdater = DummyGitHubUpdater,
) {

    private val logger = LoggerFactory.getLogger(IssueActions::class.java)

    /**
     * If issue isn't new, no further actions will fire
     */
    @Action(
        // Be sure that output binding is known
        outputBinding = "ghIssue"
    )
    fun saveNewIssue(ghIssue: GHIssue, context: OperationContext): GHIssue? {
        val existing = communityDataManager.findIssueByGithubId(ghIssue.id)
        if (existing == null) {
            val issueStorageResult = communityDataManager.saveAndExpandIssue(ghIssue)
            context += issueStorageResult
            logger.info("New issue found: #{}, title='{}'", ghIssue.number, ghIssue.title)
            return ghIssue
        }
        logger.info("Issue already processed: #{}, title='{}'", ghIssue.number, ghIssue.title)
        return null
    }

    @Action(
        pre = [
            "spel:newEntity.newEntities.?[#this instanceof T(com.embabel.shepherd.domain.Issue) && !(#this instanceof T(com.embabel.shepherd.domain.PullRequest))].size() > 0"
        ]
    )
    fun reactToNewIssue(
        ghIssue: GHIssue,
        newEntity: NewEntity<*>,
        ai: Ai
    ): IssueAssessment {
        logger.info(
            "Found new issue to react to: #{}, title='{}'",
            ghIssue.number, ghIssue.title
        )

        val issueAssessment = ai
            .withLlm(properties.triageLlm)
            .withId("issue_response")
            .creating(IssueAssessment::class.java)
            .fromTemplate(
                "first_issue_response",
                mapOf(
                    "issue" to ghIssue,
                    "properties" to properties,
                ),
            )
        logger.info(
            "Assessed issue #{}: comment='{}', urgency={}, sentiment={}",
            ghIssue.number,
            issueAssessment.comment,
            issueAssessment.urgency,
            issueAssessment.sentiment,
        )

        gitHubUpdater.labelIssue(
            ghIssue,
            issueAssessment.labels
        )

        return issueAssessment
    }

    @Action(
        pre = [
            "spel:newEntity.newEntities.?[#this instanceof T(com.embabel.shepherd.domain.PullRequest)].size() > 0"
        ]
    )
    fun reactToNewPullRequest(
        ghIssue: GHPullRequest,
        newEntity: NewEntity<*>,
        ai: Ai,
    ): PullRequestAssessment {
        logger.info(
            "Found new PR to react to: #{}, title='{}'",
            ghIssue.number, ghIssue.title
        )

        val pullRequestAssessment = ai
            .withLlm(properties.triageLlm)
            .withId("pr_response")
            .withoutProperties("filesChanged")
            .creating(PullRequestAssessment::class.java)
            .fromTemplate(
                "first_pr_response",
                mapOf("pr" to ghIssue),
            ).copy(
                filesChanged = ghIssue.changedFiles,
            )
        logger.info(
            "Assessed PR #{}: comment='{}', urgency={}, sentiment={}",
            ghIssue.number,
            pullRequestAssessment.comment,
            pullRequestAssessment.urgency,
            pullRequestAssessment.sentiment,
        )

        return pullRequestAssessment
    }

    // TODO note that naming comes from blackboard, not parameter name
    @Action(
        pre = ["spel:firstResponse.urgency > 0.0"]
    )
    fun heavyHitter(issue: GHIssue, issueAssessment: IssueAssessment) {
        logger.info("Taking heavy hitter action on issue #{}", issue.number)
    }

    @Action(
        pre = ["spel:ghIssue instanceof T(org.kohsuke.github.GHPullRequest) && ghIssue.changedFiles > 10"]
    )
    fun bigPullRequest(
        issue: GHPullRequest,
        pullRequestAssessment: PullRequestAssessment,
    ) {
        logger.info("Big PR: #{}", issue.number)
    }
}
