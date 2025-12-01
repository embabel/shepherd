package com.embabel.sherlock.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.sherlock.domain.EmployerWithProfile
import com.embabel.sherlock.domain.PersonWithProfile
import com.embabel.sherlock.service.CRMPublisher
import org.slf4j.LoggerFactory

@EmbabelComponent
class CRMActions(
    private val crmPublisher: CRMPublisher,
) {

    private val logger = LoggerFactory.getLogger(CRMActions::class.java)

    @Action
    fun createPersonCRMRecord(personWithProfile: PersonWithProfile): PersonWithProfile {
        logger.info(
            "Creating CRM record for person name='{}'",
            personWithProfile.name,
        )
        crmPublisher.publishPerson(personWithProfile)
        logger.info(
            "Created CRM record for person name='{}'",
            personWithProfile.name,
        )
        return personWithProfile
    }

    @Action
    fun createEmployerCRMRecord(employerWithProfile: EmployerWithProfile): EmployerWithProfile {
        logger.info(
            "Creating CRM record for employer name='{}'",
            employerWithProfile.name,
        )
        crmPublisher.publishEmployer(employerWithProfile)
        logger.info(
            "Created CRM record for employer name='{}'",
            employerWithProfile.name,
        )
        return employerWithProfile
    }
}
