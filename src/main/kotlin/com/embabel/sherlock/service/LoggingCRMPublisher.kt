package com.embabel.sherlock.service

import com.embabel.sherlock.domain.OrganizationWithProfile
import com.embabel.sherlock.domain.PersonWithProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Dummy CRM publisher implementation that logs records instead of sending to a real CRM.
 */
@Service
class LoggingCRMPublisher : CRMPublisher {

    private val logger = LoggerFactory.getLogger(LoggingCRMPublisher::class.java)

    override fun publishPerson(personWithProfile: PersonWithProfile) {
        logger.info(
            "*********** Publishing person to CRM: name='{}', githubLogin={}, importance={}, categories={}",
            personWithProfile.name,
            personWithProfile.githubLogin,
            personWithProfile.profile.importance,
            personWithProfile.profile.categories,
        )
    }

    override fun publishEmployer(employerWithProfile: OrganizationWithProfile) {
        logger.info(
            "*********** Publishing employer to CRM: name='{}', industry='{}', categories={}",
            employerWithProfile.name,
            employerWithProfile.profile.industry,
            employerWithProfile.profile.categories,
        )
    }
}
