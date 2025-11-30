package com.embabel.shepherd.agent

import com.embabel.shepherd.domain.Person

/**
 * Added to blackboard when a new person is created.
 * The person will have been persisted already.
 */
data class NewPerson(
    val person: Person,
)
