package com.embabel.shepherd.community.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.core.CoreToolGroups
import com.embabel.common.core.types.ZeroToOne
import com.embabel.shepherd.community.conf.ShepherdProperties
import com.embabel.shepherd.community.domain.Profile
import com.embabel.shepherd.community.service.DummyGitHubUpdater
import com.embabel.shepherd.community.service.GitHubUpdater
import com.embabel.shepherd.community.service.Store
import com.embabel.shepherd.community.tools.GitHubUserTools
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import org.slf4j.LoggerFactory

interface TriggerToken

internal object NewIssue : TriggerToken
internal object UpdatedIssue : TriggerToken
internal object NewPerson : TriggerToken


data class FirstResponse(
    val comment: String,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the urgency of addressing this issue, where 1 is most urgent")
    val urgency: ZeroToOne,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the sentiment of the person who opened the issue, where 0 is negative and 1 is positive")
    val sentiment: ZeroToOne,
    @field:JsonPropertyDescription("Labels to apply to the issue")
    val labels: Set<String>,
)


@EmbabelComponent
class IssueActions(
    val properties: ShepherdProperties,
    private val store: Store,
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
        val existing = store.findIssueByGithubId(ghIssue.id)
        if (existing == null) {
            val issueStorageResult = store.saveAndExpandIssue(ghIssue)
            context += issueStorageResult
            logger.info("New issue found: #{}, title='{}'", ghIssue.number, ghIssue.title)
            return ghIssue
        }
        logger.info("Issue already processed: #{}, title='{}'", ghIssue.number, ghIssue.title)
        return null
    }

    @Action(
        pre = ["spel:!(ghIssue instanceof T(org.kohsuke.github.GHPullRequest))"]
    )
    fun reactToNewIssue(
        ghIssue: GHIssue,
        issueStorageResult: Store.IssueStorageResult,
        ai: Ai
    ): FirstResponse {
        logger.info(
            "Found new issue to react to: #{}, title='{}'",
            ghIssue.number, ghIssue.title
        )

        val firstResponse = ai
            .withLlm(properties.firstResponderLlm)
            .withId("issue_response")
            .creating(FirstResponse::class.java)
            .fromTemplate(
                "first_issue_response",
                mapOf(
                    "issue" to ghIssue,
                    "properties" to properties,
                ),
            )
        logger.info(
            "Generated first response for issue #{}: comment='{}', urgency={}, sentiment={}",
            ghIssue.number,
            firstResponse.comment,
            firstResponse.urgency,
            firstResponse.sentiment,
        )

        gitHubUpdater.labelIssue(
            ghIssue,
            firstResponse.labels
        )

        return firstResponse
    }

    @Action(
        pre = ["spel:ghIssue instanceof T(org.kohsuke.github.GHPullRequest)"]
    )
    fun reactToNewPr(
        ghIssue: GHPullRequest,
        issueStorageResult: Store.IssueStorageResult,
        ai: Ai,
    ): FirstResponse {
        logger.info(
            "Found new PR to react to: #{}, title='{}'",
            ghIssue.number, ghIssue.title
        )

        val firstResponse = ai
            .withLlm(properties.firstResponderLlm)
            .withId("pr_response")
            .creating(FirstResponse::class.java)
            .fromTemplate(
                "first_pr_response",
                mapOf("pr" to ghIssue),
            )
        logger.info(
            "Generated first response for PR #{}: comment='{}', urgency={}, sentiment={}",
            ghIssue.number,
            firstResponse.comment,
            firstResponse.urgency,
            firstResponse.sentiment,
        )

        return firstResponse
    }

    /**
     * The person raising this issue isn't already known to us.
     */
    @Action(
        pre = ["spel:issueStorageResult.newPerson != null"]
    )
    fun researchRaiser(
        ghIssue: GHIssue,
        issueStorageResult: Store.IssueStorageResult,
        ai: Ai
    ) {
        logger.info(
            "Researching person raising issue #{}: githubId={}",
            ghIssue.number,
            ghIssue.user.id,
        )
        val person = issueStorageResult.newPerson ?: error("Internal error: should have new person")
        val profile = ai
            .withLlm(properties.researcherLlm)
            .withId("person_research")
            .withTools(CoreToolGroups.WEB)
            .withToolObject(GitHubUserTools(ghIssue.user))
            .withoutProperties("uuid", "updated")
            .creating(Profile::class.java)
            .fromTemplate(
                "research_person",
                mapOf(
                    "person" to person,
                    "properties" to properties,
                ),
            )
        logger.info(
            "Researched person raising issue #{}: name='{}', profile='{}'",
            ghIssue.number,
            person.name,
            profile,
        )

        // What about their github repos

        store.save(person.copy(profile = profile))
    }

    // TODO note that naming comes from blackboard, not parameter name
    @Action(
        pre = ["spel:firstResponse.urgency > 0.0"]
    )
    fun heavyHitter(issue: GHIssue, firstResponse: FirstResponse) {
        logger.info("Taking heavy hitter action on issue #{}", issue.number)
    }
}
