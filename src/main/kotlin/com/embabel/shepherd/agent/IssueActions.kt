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
import org.kohsuke.github.GHIssue
import org.slf4j.LoggerFactory

data class NewIssue(
    val ghIssue: GHIssue,
    val issueStorageResult: Store.IssueStorageResult,
)

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
    val newIssue: NewIssue,
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
    fun saveNewIssue(ghIssue: GHIssue): NewIssue? {
        val existing = store.findIssueByGithubId(ghIssue.id)
        if (existing == null) {
            val issueExpansion = store.saveAndExpandIssue(ghIssue)
            return NewIssue(ghIssue, issueExpansion)
        }
        logger.info("Issue already known: #${ghIssue.number}, title='${ghIssue.title}'")
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
            .withId("first_response")
            .creating(FirstResponse::class.java)
            .fromTemplate(
                "first_response",
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

    /**
     * The person raising this issue isn't already known to us.
     */
    @Action(
        pre = ["spel:newIssue.issueStorageResult.newPerson != null"]
    )
    fun researchRaiser(newIssue: NewIssue, ai: Ai) {
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
            .withoutProperties("uuid", "retrieved")
            .creating(Profile::class.java)
            // withoutProperties goes here
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
    fun heavyHitter(issue: GHIssue, reaction: IssueReaction) {
        println("Taking heavy hitter action on issue #${issue.number}")
    }
}
