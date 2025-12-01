package com.embabel.sherlock.service

import com.embabel.sherlock.domain.EmployerWithProfile
import com.embabel.sherlock.domain.PersonWithProfile

/**
 * Service for publishing records to a CRM system.
 */
interface CRMPublisher {

    /**
     * Publish a person with their profile to the CRM.
     * @param personWithProfile the person and profile to publish
     */
    fun publishPerson(personWithProfile: PersonWithProfile)

    /**
     * Publish an employer with their profile to the CRM.
     * @param employerWithProfile the employer and profile to publish
     */
    fun publishEmployer(employerWithProfile: EmployerWithProfile)
}
