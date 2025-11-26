package com.embabel.shepherd.domain

import org.drivine.manager.PersistenceManager

// TODO could we just serialize it all?
data class Person(
    val name: String,
    val bio: String?,
    val githubId: String?
)

class PersonRepository(
    private val persistenceManager: PersistenceManager,
) {

    fun save(person: Person): Person {
        persistenceManager.query(
        )
        return person
    }
}

// TODO look at twitter example from crew