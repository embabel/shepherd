package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.core.CoreToolGroups
import com.embabel.common.core.types.ZeroToOne
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.domain.Profile
import com.embabel.shepherd.service.Store
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHPullRequest
import org.slf4j.LoggerFactory

// We need deserialization help for the sealed interface
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "fqn"
)
sealed interface NewItem<I : GHIssue> {
    val ghIssue: I
    val issueStorageResult: Store.IssueStorageResult

    companion object {
        operator fun invoke(
            ghIssue: GHIssue,
            issueStorageResult: Store.IssueStorageResult,
        ): NewItem<out GHIssue> {
            return if (ghIssue is GHPullRequest) {
                LoggerFactory.getLogger(NewItem::class.java).info(
                    "Creating NewPullRequest for PR #{}",
                    ghIssue.number
                )
                NewPullRequest(ghIssue, issueStorageResult)
            } else {
                NewIssue(ghIssue, issueStorageResult)
            }
        }
    }
}

data class NewIssue(
    override val ghIssue: GHIssue,
    override val issueStorageResult: Store.IssueStorageResult,
) : NewItem<GHIssue>

data class NewPullRequest(
    override val ghIssue: GHPullRequest,
    override val issueStorageResult: Store.IssueStorageResult,
) : NewItem<GHPullRequest>

data class UpdatedIssue(
    val ghIssue: GHIssue,
)

data class FirstResponse(
    val comment: String,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the urgency of addressing this issue, where 1 is most urgent")
    val urgency: ZeroToOne,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the sentiment of the person who opened the issue, where 0 is negative and 1 is positive")
    val sentiment: ZeroToOne,
)

data class IssueReaction(
    val newIssue: NewItem<*>,
    val firstResponse: FirstResponse,
)

@EmbabelComponent
class IssueActions(
    val properties: ShepherdProperties,
    private val store: Store,
) {

    private val logger = LoggerFactory.getLogger(IssueActions::class.java)

    /**
     * If issue isn't new, no further actions will fire
     */
    @Action
    fun saveNewIssue(ghIssue: GHIssue): NewItem<*>? {
        val existing = store.findIssueByGithubId(ghIssue.id)
        if (existing == null) {
            val issueExpansion = store.saveAndExpandIssue(ghIssue)
            logger.info("New issue found: #{}, title='{}'", ghIssue.number, ghIssue.title)
            return NewItem(ghIssue, issueExpansion)
        }
        logger.info("Issue already known: #{}, title='{}'", ghIssue.number, ghIssue.title)
        return null
    }

    @Action
    fun reactToNewIssue(newIssue: NewIssue, ai: Ai): IssueReaction {
        logger.info(
            "Found new issue to react to: #{}, title='{}'",
            newIssue.ghIssue.number, newIssue.ghIssue.title
        )

        val firstResponse = ai
            .withLlm(properties.firstResponderLlm)
            .withId("issue_response")
            .creating(FirstResponse::class.java)
            .fromTemplate(
                "first_issue_response",
                mapOf("issue" to newIssue.ghIssue),
            )
        logger.info(
            "Generated first response for issue #{}: comment='{}', urgency={}, sentiment={}",
            newIssue.ghIssue.number,
            firstResponse.comment,
            firstResponse.urgency,
            firstResponse.sentiment,
        )

        return IssueReaction(
            newIssue = newIssue,
            firstResponse = firstResponse,
        )
    }

    @Action
    fun reactToNewPr(newPr: NewPullRequest, ai: Ai): IssueReaction {
        logger.info(
            "Found new PR to react to: #{}, title='{}'",
            newPr.ghIssue.number, newPr.ghIssue.title
        )

        val firstResponse = ai
            .withLlm(properties.firstResponderLlm)
            .withId("pr_response")
            .creating(FirstResponse::class.java)
            .fromTemplate(
                "first_pr_response",
                mapOf("pr" to newPr.ghIssue),
            )
        logger.info(
            "Generated first response for PR #{}: comment='{}', urgency={}, sentiment={}",
            newPr.ghIssue.number,
            firstResponse.comment,
            firstResponse.urgency,
            firstResponse.sentiment,
        )

        return IssueReaction(
            newIssue = newPr,
            firstResponse = firstResponse,
        )
    }

    /**
     * The person raising this issue isn't already known to us.
     */
    @Action(
        pre = ["spel:newIssue.issueStorageResult.newPerson != null"]
    )
    fun researchRaiser(newIssue: NewItem<*>, ai: Ai) {
        logger.info(
            "Researching person raising issue #{}: githubId={}",
            newIssue.ghIssue.number,
            newIssue.ghIssue.user.id,
        )
        val person = newIssue.issueStorageResult.newPerson ?: error("Internal error: should have new person")
        val profile = ai
            .withLlm(properties.researcherLlm)
            .withId("person_research")
            .withTools(CoreToolGroups.WEB)
            .withoutProperties("uuid", "updated")
            .creating(Profile::class.java)
            .fromTemplate(
                "research_person",
                mapOf("person" to person, "properties" to properties)
            )
        logger.info(
            "Researched person raising issue #{}: name='{}', profile='{}'",
            newIssue.ghIssue.number,
            person.name,
            profile,
        )

        // What about their github repos

        store.save(person.copy(profile = profile))
    }

    // TODO note that naming comes from blackboard, not parameter name
    @Action(
        pre = ["spel:issueReaction.firstResponse.urgency > 0.0"]
    )
    fun heavyHitter(issue: GHIssue, issueReaction: IssueReaction) {
        logger.info("Taking heavy hitter action on issue #{}", issue.number)
    }
}
