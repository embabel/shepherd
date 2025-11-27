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
            githubId = 123L,
            employer = null
        )

        template.save(person)

        val ids = template.listIds("Person")
        assertEquals(1, ids.size)
        assertEquals(person.uuid.toString(), ids[0])
    }

    @Test
    fun `should persist and retrieve Person with Employer`() {
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Acme Corp"
        )
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Bob",
            bio = "Developer",
            githubId = 456L,
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
    fun `should persist Employer separately`() {
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Acme Corp"
        )

        template.save(org)

        val ids = template.listIds("Employer")
        assertEquals(1, ids.size)
        assertEquals(org.uuid.toString(), ids[0])
    }

    @Test
    fun `two persons with same Employer should result in one Employer file when saved separately`() {
        val sharedOrg = Employer(
            uuid = UUID.randomUUID(),
            name = "Shared Corp"
        )

        val person1 = Person(
            uuid = UUID.randomUUID(),
            name = "Charlie",
            bio = "Engineer",
            githubId = 123L,
            employer = sharedOrg
        )

        val person2 = Person(
            uuid = UUID.randomUUID(),
            name = "Diana",
            bio = "Designer",
            githubId = 456L,
            employer = sharedOrg
        )

        // Save the employer first
        template.save(sharedOrg)

        // Save both persons
        template.save(person1)
        template.save(person2)

        // Verify we have 2 persons
        val personIds = template.listIds("Person")
        assertEquals(2, personIds.size)

        // Verify we have only 1 employer (saved once)
        val orgIds = template.listIds("Employer")
        assertEquals(1, orgIds.size)
        assertEquals(sharedOrg.uuid.toString(), orgIds[0])
    }

    @Test
    fun `saving same Employer twice should not create duplicate files`() {
        val org = Employer(
            uuid = UUID.randomUUID(),
            name = "Unique Corp"
        )

        template.save(org)
        template.save(org)

        val ids = template.listIds("Employer")
        assertEquals(1, ids.size)
    }

    @Test
    fun `should retrieve persisted Person with null Employer`() {
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Eve",
            bio = "Architect",
            githubId = 789L,
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
            locked = false,
            company = "Acme"
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
            githubId = 111L,
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
            locked = true,
            company = null
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

    @Test
    fun `should persist and retrieve RaisableIssue created with withRaisedBy`() {
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Reporter",
            bio = "Bug reporter",
            githubId = 999L,
            employer = null
        )

        val issue = IssueImpl(
            uuid = UUID.randomUUID(),
            id = 54321L,
            state = "open",
            stateReason = null,
            number = 100,
            closedAt = null,
            body = "Found a bug",
            title = "Bug in login",
            htmlUrl = "https://github.com/test/repo/issues/100",
            locked = false,
            company = "TestCorp"
        )

        // Create a RaisableIssue using withRaisedBy
        val raisableIssue = issue.withRaisedBy(person)

        // Save the raisable issue
        template.save(raisableIssue)

        // Query by RaisableIssue interface
        val allRaisable = template.findAll<RaisableIssue>().toList()
        assertEquals(1, allRaisable.size)

        val retrieved = allRaisable[0]
        assertEquals(issue.uuid, retrieved.uuid)
        assertEquals(issue.id, retrieved.id)
        assertEquals(issue.title, retrieved.title)
        assertEquals(person.uuid, retrieved.raisedBy.uuid)
        assertEquals(person.name, retrieved.raisedBy.name)
    }

    @Test
    fun `should persist RaisableIssue with Person that has Profile`() {
        val profile = Profile(
            bio = "Senior Developer",
            homepage = "https://example.com",
            location = "New York",
            email = "dev@example.com",
            blog = "https://example.com/blog"
        )

        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Developer",
            bio = "Writes code",
            githubId = 777L,
            employer = null,
            profile = profile
        )

        val issue = IssueImpl(
            uuid = UUID.randomUUID(),
            id = 11111L,
            state = "open",
            stateReason = null,
            number = 200,
            closedAt = null,
            body = "Feature request",
            title = "Add dark mode",
            htmlUrl = "https://github.com/test/repo/issues/200",
            locked = false,
            company = null
        )

        // Create RaisableIssue with Person that has a Profile
        val raisableIssue = issue.withRaisedBy(person)
        template.save(raisableIssue)

        // Retrieve and verify Profile is intact
        val allRaisable = template.findAll<RaisableIssue>().toList()
        assertEquals(1, allRaisable.size)

        val retrieved = allRaisable[0]
        assertEquals(person.uuid, retrieved.raisedBy.uuid)
        assertEquals(person.name, retrieved.raisedBy.name)

        // Verify Profile is preserved
        assertNotNull(retrieved.raisedBy.profile)
        assertEquals(profile.bio, retrieved.raisedBy.profile!!.bio)
        assertEquals(profile.homepage, retrieved.raisedBy.profile!!.homepage)
        assertEquals(profile.location, retrieved.raisedBy.profile!!.location)
        assertEquals(profile.email, retrieved.raisedBy.profile!!.email)
        assertEquals(profile.blog, retrieved.raisedBy.profile!!.blog)
    }

    @Test
    fun `updating Person after saving RaisableIssue DOES update via reference resolution`() {
        // This test demonstrates that references are resolved at query time
        val personWithoutProfile = Person(
            uuid = UUID.randomUUID(),
            name = "Developer",
            bio = "Writes code",
            githubId = 888L,
            employer = null,
            profile = null
        )

        val issue = IssueImpl(
            uuid = UUID.randomUUID(),
            id = 22222L,
            state = "open",
            stateReason = null,
            number = 300,
            closedAt = null,
            body = "Bug",
            title = "Fix crash",
            htmlUrl = "https://github.com/test/repo/issues/300",
            locked = false,
            company = null
        )

        // Save RaisableIssue with Person WITHOUT profile
        val raisableIssue = issue.withRaisedBy(personWithoutProfile)
        template.save(raisableIssue)

        // Now save the Person WITH a profile (simulating a later update)
        val profile = Profile(
            bio = "Updated bio",
            homepage = "https://updated.com",
            location = "Updated location",
            email = "updated@example.com",
            blog = null
        )
        val personWithProfile = personWithoutProfile.copy(profile = profile)
        template.save(personWithProfile)

        // Retrieve RaisableIssue - Person is resolved by reference, so it SHOULD have the profile
        val allRaisable = template.findAll<RaisableIssue>().toList()
        assertEquals(1, allRaisable.size)

        val retrievedIssue = allRaisable[0]
        assertNotNull(retrievedIssue.raisedBy.profile,
            "Person resolved via reference SHOULD have the updated profile")
        assertEquals(profile.bio, retrievedIssue.raisedBy.profile!!.bio)

        // Person queried directly should also have the profile
        val allPersons = template.findAll<Person>().toList()
        assertEquals(1, allPersons.size)
        assertNotNull(allPersons[0].profile, "Directly saved Person should have profile")
    }
}
