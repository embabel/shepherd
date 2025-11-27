package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.common.core.types.ZeroToOne
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.domain.RaisableIssue
import com.embabel.shepherd.service.Store
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.drivine.query.MixinTemplate
import org.kohsuke.github.GHIssue
import org.slf4j.LoggerFactory

data class NewIssue(
    val ghIssue: GHIssue,
    val issue: RaisableIssue,
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
    private val mixinTemplate: MixinTemplate,
) {

    private val logger = LoggerFactory.getLogger(IssueActions::class.java)

    @Action
    fun saveNewIssue(ghIssue: GHIssue): NewIssue? {
        val existing = store.findIssueByGithubId(ghIssue.id)
        if (existing == null) {
            val issue = store.saveAndExpandIssue(ghIssue)
            return NewIssue(ghIssue, issue)
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
     * When we see an issue we check whether or not the person raising it is already known to us.
     */
    @Action
    fun researchRaiser(newIssue: NewIssue): Person {
        return newIssue.issue.raisedBy
    }

    // TODO note that naming comes from blackboard, not parameter name
    @Action(
        pre = ["spel:issueReaction.firstResponse.urgency > 0.0"]
    )
    fun heavyHitter(issue: GHIssue, reaction: IssueReaction) {
        println("Taking heavy hitter action on issue #${issue.number}")
    }
}
