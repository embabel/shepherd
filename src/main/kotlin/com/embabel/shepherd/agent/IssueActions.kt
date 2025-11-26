package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.common.core.types.ZeroToOne
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.domain.PersonRepository
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.kohsuke.github.GHIssue
import org.slf4j.LoggerFactory
import java.util.*

data class FirstResponse(
    val comment: String,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the urgency of addressing this issue, where 1 is most urgent")
    val urgency: ZeroToOne,
    @field:JsonPropertyDescription("A value between 0 and 1 indicating the sentiment of the person who opened the issue, where 0 is negative and 1 is positive")
    val sentiment: ZeroToOne,
)

data class IssueReaction(
    val ghIssue: GHIssue,
    val firstResponse: FirstResponse,
)

@EmbabelComponent
class IssueActions(
    val properties: ShepherdProperties,
    private val personRepository: PersonRepository,
) {

    private val logger = LoggerFactory.getLogger(IssueActions::class.java)

    @Action
    fun reactToNewIssue(issue: GHIssue, ai: Ai): IssueReaction {
        logger.info("Found new issue to react to: #{}, title='{}'", issue.number, issue.title)
        val firstResponse = ai
            .withLlm(properties.firstResponderLlm)
            .withId("first_response")
            .creating(FirstResponse::class.java)
            .fromTemplate(
                "first_response",
                mapOf("issue" to issue),
            )
        logger.info(
            "Generated first response for issue #{}: comment='{}', urgency={}, sentiment={}",
            issue.number,
            firstResponse.comment,
            firstResponse.urgency,
            firstResponse.sentiment,
        )

        personRepository.save(issue)

        return IssueReaction(
            ghIssue = issue,
            firstResponse = firstResponse,
        )
    }

    /**
     * When we see an issue we check whether or not the person raising it is already known to us.
     */
    @Action
    fun researchRaiser(issue: GHIssue): Person {
        val newPerson = Person(
            uuid = UUID.randomUUID(),
            name = issue.user.name,
            bio = issue.user.bio,
            githubId = issue.user.login,
        )
        val savedPerson = personRepository.save(newPerson)
        return savedPerson
    }

    // TODO note that naming comes from blackboard, not parameter name
    @Action(
        pre = ["spel:issueReaction.firstResponse.urgency > 0.0"]
    )
    fun heavyHitter(issue: GHIssue, reaction: IssueReaction) {
        println("Taking heavy hitter action on issue #${issue.number}")
    }
}
