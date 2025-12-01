package com.embabel.sherlock.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.core.CoreToolGroups
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.service.CommunityDataManager
import com.embabel.shepherd.service.NewEntity
import com.embabel.sherlock.conf.SherlockProperties
import com.embabel.sherlock.domain.PersonWithProfile
import com.embabel.sherlock.domain.Profile
import org.slf4j.LoggerFactory

@EmbabelComponent
class ResearchActions(
    val properties: SherlockProperties,
    private val communityDataManager: CommunityDataManager,
) {

    private val logger = LoggerFactory.getLogger(ResearchActions::class.java)

    /**
     * The person raising this issue isn't already known to us.
     */
    @Action(
        pre = [
            "spel:newEntity.newEntities.?[#this instanceof T(com.embabel.shepherd.domain.Person)].size() > 0"
        ]
    )
    fun researchPerson(
        newEntity: NewEntity<*>,
        ai: Ai
    ) {
        val person = newEntity.newEntities.filterIsInstance<Person>().first()
        logger.info(
            "Researching person {}",
            person,
        )

        val profile = ai
            .withLlm(properties.researchLLm)
            .withId("person_research")
            .withTools(CoreToolGroups.WEB)
            // TODO restore this
//            .withToolObject(GitHubUserTools(newPerson.person.githubId))
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
            "Researched person name='{}', profile='{}'",
            person.name,
            profile,
        )

        communityDataManager.save(PersonWithProfile.from(person, profile))
    }

}