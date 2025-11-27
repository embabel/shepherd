package org.drivine.query

import com.embabel.shepherd.domain.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*

class PersonPersistenceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var template: FileMixinTemplate

    @BeforeEach
    fun setUp() {
        // Use composite extractor: try HasUUID first, then fall back to @Id annotation
        val uuidExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val composite = CompositeIdExtractor(uuidExtractor, AnnotationIdExtractor)
        template = FileMixinTemplate(
            baseDir = tempDir.resolve(".data"),
            idExtractor = composite
        )
    }

    @AfterEach
    fun tearDown() {
        template.clear()
    }

    @Test
    fun `should persist Person with uuid`() {
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Alice",
            bio = "Software engineer",
            githubId = "alice123",
            employer = null
        )

        template.save(person)

        val ids = template.listIds("Person")
        assertEquals(1, ids.size)
        assertEquals(person.uuid.toString(), ids[0])
    }

    @Test
    fun `should persist and retrieve Person with Organization`() {
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Acme Corp"
        )
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Bob",
            bio = "Developer",
            githubId = "bob456",
            employer = org
        )

        template.save(person)

        val ids = template.listIds("Person")
        assertEquals(1, ids.size)

        // Retrieve using findAll and verify Organization is intact
        val allPersons = template.findAll<Person>().toList()
        assertEquals(1, allPersons.size)

        val retrieved = allPersons[0]
        assertEquals(person.uuid, retrieved.uuid)
        assertEquals(person.name, retrieved.name)
        assertEquals(person.bio, retrieved.bio)
        assertEquals(person.githubId, retrieved.githubId)

        // Verify Organization is correctly persisted and retrieved
        assertNotNull(retrieved.employer)
        assertEquals(org.uuid, retrieved.employer!!.uuid)
        assertEquals(org.name, retrieved.employer!!.name)
    }

    @Test
    fun `should persist Organization separately`() {
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Acme Corp"
        )

        template.save(org)

        val ids = template.listIds("Organization")
        assertEquals(1, ids.size)
        assertEquals(org.uuid.toString(), ids[0])
    }

    @Test
    fun `two persons with same Organization should result in one Organization file when saved separately`() {
        val sharedOrg = Employer(
            uuid = UUID.randomUUID(),
            name = "Shared Corp"
        )

        val person1 = Person(
            uuid = UUID.randomUUID(),
            name = "Charlie",
            bio = "Engineer",
            githubId = "charlie",
            employer = sharedOrg
        )

        val person2 = Person(
            uuid = UUID.randomUUID(),
            name = "Diana",
            bio = "Designer",
            githubId = "diana",
            employer = sharedOrg
        )

        // Save the organization first
        template.save(sharedOrg)

        // Save both persons
        template.save(person1)
        template.save(person2)

        // Verify we have 2 persons
        val personIds = template.listIds("Person")
        assertEquals(2, personIds.size)

        // Verify we have only 1 organization (saved once)
        val orgIds = template.listIds("Organization")
        assertEquals(1, orgIds.size)
        assertEquals(sharedOrg.uuid.toString(), orgIds[0])
    }

    @Test
    fun `saving same Organization twice should not create duplicate files`() {
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Unique Corp"
        )

        template.save(org)
        template.save(org)

        val ids = template.listIds("Organization")
        assertEquals(1, ids.size)
    }

    @Test
    fun `should retrieve persisted Person with null Organization`() {
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Eve",
            bio = "Architect",
            githubId = "eve789",
            employer = null
        )

        template.save(person)

        val allPersons = template.findAll<Person>().toList()
        assertEquals(1, allPersons.size)

        val retrieved = allPersons[0]
        assertEquals(person.uuid, retrieved.uuid)
        assertEquals(person.name, retrieved.name)
        assertEquals(person.bio, retrieved.bio)
        assertEquals(person.githubId, retrieved.githubId)
        assertNull(retrieved.employer)
    }

    @Test
    fun `should find IssueImpl when querying by Issue interface`() {
        val issue = IssueImpl(
            uuid = UUID.randomUUID(),
            id = 12345L,
            state = "open",
            stateReason = null,
            number = 42,
            closedAt = null,
            body = "This is a bug",
            title = "Bug report",
            htmlUrl = "https://github.com/test/repo/issues/42",
            locked = false
        )

        template.save(issue)

        // Verify it's saved under IssueImpl directory
        val implIds = template.listIds("IssueImpl")
        assertEquals(1, implIds.size)

        // Query by Issue interface - should find the IssueImpl
        val allIssues = template.findAll<Issue>().toList()
        assertEquals(1, allIssues.size)

        val retrieved = allIssues[0]
        assertEquals(issue.uuid, retrieved.uuid)
        assertEquals(issue.id, retrieved.id)
        assertEquals(issue.title, retrieved.title)
        assertEquals(issue.body, retrieved.body)
        assertEquals(issue.number, retrieved.number)
    }

    @Test
    fun `should find all HasUUID implementations`() {
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Alice",
            bio = null,
            githubId = "alice",
            employer = null
        )
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Acme"
        )
        val issue = IssueImpl(
            uuid = UUID.randomUUID(),
            id = 999L,
            state = "closed",
            stateReason = null,
            number = 1,
            closedAt = "2024-01-01",
            body = "Fixed",
            title = "Done",
            htmlUrl = "https://example.com",
            locked = true
        )

        template.save(person)
        template.save(org)
        template.save(issue)

        // Query by HasUUID interface - should find all three
        val allWithUuid = template.findAll<HasUUID>().toList()
        assertEquals(3, allWithUuid.size)

        val uuids = allWithUuid.map { it.uuid }.toSet()
        assertTrue(uuids.contains(person.uuid))
        assertTrue(uuids.contains(org.uuid))
        assertTrue(uuids.contains(issue.uuid))
    }
}
