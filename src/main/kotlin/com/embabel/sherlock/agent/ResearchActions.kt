package com.embabel.sherlock.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.core.CoreToolGroups
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.domain.Organization
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.service.CommunityDataManager
import com.embabel.shepherd.service.NewEntity
import com.embabel.sherlock.conf.SherlockProperties
import com.embabel.sherlock.domain.CompanyProfile
import com.embabel.sherlock.domain.OrganizationWithProfile
import com.embabel.sherlock.domain.PersonWithProfile
import com.embabel.sherlock.domain.Profile
import org.slf4j.LoggerFactory

@EmbabelComponent
class ResearchActions(
    val properties: SherlockProperties,
    private val shepherdProperties: ShepherdProperties,
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
    ): PersonWithProfile {
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
                    "shepherdProperties" to shepherdProperties,
                ),
            )
        logger.info(
            "Researched person name='{}', profile=\n{}",
            person.name,
            profile,
        )
        return communityDataManager.save(PersonWithProfile.from(person, profile))
    }

    @Action(
        pre = [
            "spel:newEntity.newEntities.?[#this instanceof T(com.embabel.shepherd.domain.Employer)].size() > 0"
        ]
    )
    fun researchCompany(
        newEntity: NewEntity<*>,
        ai: Ai,
    ): OrganizationWithProfile {
        val organization = newEntity.newEntities.filterIsInstance<Organization>().first()
        logger.info(
            "Researching employer {}",
            organization,
        )

        val profile = ai
            .withLlm(properties.researchLLm)
            .withId("company_research")
            .withTools(CoreToolGroups.WEB)
            .withoutProperties("uuid", "updated")
            .creating(CompanyProfile::class.java)
            .fromTemplate(
                "research_company",
                mapOf(
                    "company" to organization,
                    "properties" to properties,
                ),
            )
        logger.info(
            "Researched company name='{}', profile=\n{}",
            organization.name,
            profile,
        )

        return communityDataManager.save(OrganizationWithProfile.from(organization, profile))
    }

}