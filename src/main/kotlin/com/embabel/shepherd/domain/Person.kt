package com.embabel.shepherd.domain

import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.drivine.utils.ObjectUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

// TODO could we just serialize it all?
data class Person(
    val uuid: UUID,
    val name: String,
    val bio: String?,
    val githubId: String?
)

@Component
class PersonRepository(
    @param:Autowired private val persistenceManager: PersistenceManager,
) {

    fun save(person: Person): Person {
        val spec = QuerySpecification.withStatement("""
            merge (p:Person {uuid: ${'$'}props.uuid}) set p += ${'$'}props 
            return properties(p)
        """.trimIndent())
            .bind(mapOf("props" to ObjectUtils.primitiveProps(person)))
            .transform(Person::class.java)
        val result = persistenceManager.getOne(spec)
        return result;
    }
}

// TODO look at twitter example from crew
