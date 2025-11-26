package com.embabel.shepherd.domain

import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.drivine.utils.ObjectUtils
import org.kohsuke.github.GHIssue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

data class Organization(
    val name: String,
)

data class Person(
    val uuid: UUID,
    val name: String,
    val bio: String?,
    val githubId: String?,
//    val organization: Organization?,
)

@Component
class PersonRepository(
    @param:Autowired private val persistenceManager: PersistenceManager,
) {

    fun save(ghIssue: GHIssue): GHIssue {
        val props = mapOf("props" to ObjectUtils.primitiveProps(ghIssue))
        val spec = QuerySpecification.withStatement(
            """
            merge (g:GHIssue {id: ${'$'}props.id}) set g += ${'$'}props 
            return properties(g)
        """.trimIndent()
        )
            .bind(props)
//            .transform(GHIssue::class.java)
        persistenceManager.execute(spec)
//        val result = persistenceManager.getOne(spec)
        return ghIssue
    }

    fun save(person: Person): Person {
        val spec = QuerySpecification.withStatement(
            """
            merge (p:Person {uuid: ${'$'}props.uuid}) set p += ${'$'}props 
            return properties(p)
        """.trimIndent()
        )
            .bind(mapOf("props" to ObjectUtils.primitiveProps(person)))
            .transform(Person::class.java)
        val result = persistenceManager.getOne(spec)
        return result
    }
}
