package com.embabel.shepherd.domain

import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.drivine.utils.ObjectUtils
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueStateReason
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

data class Organization(
    val name: String,
)

/**
 * Embabel synthetic identifier
 */
interface HasUUID {
    val uuid: UUID
}

enum class Direction {
    OUTGOING,
    INCOMING,
    UNDIRECTED
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphRelationship(
    val type: String,                 // Neo4j rel type, e.g. "HAS_HOLIDAY"
    val direction: Direction = Direction.OUTGOING,
    val targetLabel: String,          // Neo4j label, e.g. "Holiday"
    val alias: String = ""            // Cypher variable, e.g. "h"
)

interface Issue : HasUUID {
    /**
     * The unique identifier of the issue from github
     */
    val id: Long
    val state: String?
    val stateReason: GHIssueStateReason?
    val number: Int
    val closedAt: String?

    //    val comments: Int
    val body: String?
    val title: String?
    val htmlUrl: String?
    val locked: Boolean

    fun withRaisedBy(person: Person): RaisableIssue {
        val self = this
        return object : RaisableIssue, Issue by self {
            override val raisedBy: Person = person
        }
    }

    companion object {

        fun fromGHIssue(ghIssue: GHIssue): Issue {
            return IssueImpl(
                uuid = UUID.randomUUID(),
                id = ghIssue.id,
                state = ghIssue.state?.name,
                stateReason = ghIssue.stateReason,
                number = ghIssue.number,
                closedAt = ghIssue.closedAt?.toString(),
//                comments = ghIssue.comments,
                body = ghIssue.body,
                title = ghIssue.title,
                htmlUrl = ghIssue.htmlUrl.toString(),
                locked = ghIssue.isLocked,
            )
        }
    }
}

data class IssueImpl(
    override val uuid: UUID,
    override val id: Long,
    override val state: String?,
    override val stateReason: GHIssueStateReason?,
    override val number: Int,
    override val closedAt: String?,
//    override val comments: Int,
    override val body: String?,
    override val title: String?,
    override val htmlUrl: String?,
    override val locked: Boolean,
) : Issue


interface RaisableIssue : Issue {

    @GraphRelationship(type = "RAISED_BY", direction = Direction.INCOMING, targetLabel = "Person", alias = "p")
    val raisedBy: Person
}

interface IssueAssignment : Issue {
    @GraphRelationship(type = "ASSIGNED_TO", direction = Direction.OUTGOING, targetLabel = "Person", alias = "p")
    val assignedTo: Collection<Person>
}


interface IssueRepository {

    fun findById(id: Long, mixins: List<Class<out Issue>>): Issue?

    fun findById(id: Long): Issue? = findById(id, emptyList())

    fun <T : Issue> findById(id: Long, mixin: Class<T>): T?

    fun <T : Issue> eraseSpecialization(id: Long, mixin: Class<T>): Issue?

    fun findRaisedIssuesByRaiserName(name: String): Iterable<RaisableIssue>

}

interface DrivineTemplate {

    /**
     * Find entities matching the given where clause, applying the given mixins.
     * The whereClause merely finds the id. The convention is that n
     * will be the node being queried.
     */
    fun <T> findWhere(whereClause: String, mixins: List<Class<out T>>): Iterable<T>

    fun <T> findById(id: Long, mixins: List<Class<out T>>): T?

    fun <T> findById(id: Long): T? = findById(id, emptyList())

    fun <T, U : T> findById(id: Long, mixin: Class<U>): U?

    fun <T> save(entity: T): T

    fun <T, U : T> eraseSpecialization(entity: U, mixin: Class<U>): T?

}

inline fun <reified T : Issue> IssueRepository.findById(id: Long): T? = findById(id, T::class.java)


fun tests() {
    val repo: IssueRepository = TODO()
    val issueCore = repo.findById(1234L)

    val ghIssue = GHIssue()

    val newIssue = Issue.fromGHIssue(ghIssue).withRaisedBy(Person(UUID.randomUUID(), "b", null, null))

//    val issueWithRaiser = repo.findById(1234L, listOf(IssueRaising::class.java)) as? IssueRaiser

    val raisedIssue = repo.findById<RaisableIssue>(1234L)

    val drivineTemplate: DrivineTemplate = TODO()

    val fredsIssues: Iterable<RaisableIssue> = drivineTemplate.findWhere(
        whereClause = "(n)-[:RAISED_BY]->(p:Person {name: 'Fred'})->(org:Organization { name: 'Embabel'})",
        mixins = listOf(RaisableIssue::class.java)
    )

}

val raisableIssue = """
MATCH (n:Issue)
OPTIONAL MATCH (n)-[:RAISED_BY]->(_p1:Person)
OPTIONAL MATCH (n)-[:ASSIGNED_TO]->(_a1:Person)
// WHERE CLAUSE
    WHERE (n)-[:RAISED_BY]->(p:Person {name: 'Fred'})
// WHERE CLAUSE END
WITH n, p, collect(DISTINCT assignee) AS assignees
RETURN {
  // spread the Issue node properties into the result
  .*: n,

  // single raisedBy person
  raisedBy: p IS NULL ? null : properties(p),

  // list of assignees
  assignedTo: [a IN assignees | properties(a)]
} AS result;
""".trimIndent()


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
