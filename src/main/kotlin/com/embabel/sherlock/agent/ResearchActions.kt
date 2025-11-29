package com.embabel.sherlock.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.core.CoreToolGroups
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.service.Store
import com.embabel.shepherd.tools.GitHubUserTools
import com.embabel.sherlock.domain.PersonWithProfile
import com.embabel.sherlock.domain.Profile
import org.kohsuke.github.GHIssue
import org.slf4j.LoggerFactory

@EmbabelComponent
class ResearchActions(
    val properties: ShepherdProperties,
    private val store: Store,
) {

    private val logger = LoggerFactory.getLogger(ResearchActions::class.java)

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

        store.save(PersonWithProfile.from(person, profile))
    }

}