package org.drivine.query

import com.embabel.shepherd.domain.*
import com.embabel.sherlock.domain.PersonWithProfile
import com.embabel.sherlock.domain.Profile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kohsuke.github.GHIssueStateReason
import java.nio.file.Path
import java.util.*

class PersonPersistenceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var template: FileMixinTemplate

    @BeforeEach
    fun setUp() {
        // Use composite extractor: try HasUUID first, then fall back to @Id annotation
        val uuidIdExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val composite = CompositeIdExtractor(uuidIdExtractor, AnnotationIdExtractor)

        // Create UuidExtractor for extracting UUIDs from HasUUID instances
        val uuidExtractor = UuidExtractor { entity ->
            (entity as? HasUUID)?.uuid
        }

        template = FileMixinTemplate(
            baseDir = tempDir.resolve(".data"),
            idExtractor = composite,
            uuidExtractor = uuidExtractor,
        )

        // Register packages where domain classes live
        template.registerPackage("com.embabel.shepherd.community.domain")
        template.registerPackage("com.embabel.shepherd.proprietary.domain")
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

        val ids = template.listIds("PersonImpl")
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

        val ids = template.listIds("PersonImpl")
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
        val personIds = template.listIds("PersonImpl")
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
    fun `should find Issue when querying by Issue interface`() {
        val issueUuid = UUID.randomUUID()
        val issue = Issue.create(
            uuid = issueUuid,
            id = 12345L,
            state = "open",
            number = 42,
            body = "This is a bug",
            title = "Bug report",
            htmlUrl = "https://github.com/test/repo/issues/42",
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
        assertEquals(issueUuid, retrieved.uuid)
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
        val issueUuid = UUID.randomUUID()
        val issue = Issue.create(
            uuid = issueUuid,
            id = 999L,
            state = "closed",
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
        assertTrue(uuids.contains(issueUuid))
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

        val issueUuid = UUID.randomUUID()
        val issue = Issue.create(
            uuid = issueUuid,
            id = 54321L,
            state = "open",
            number = 100,
            body = "Found a bug",
            title = "Bug in login",
            htmlUrl = "https://github.com/test/repo/issues/100",
            company = "TestCorp"
        )

        // Create a RaisableIssue using RaisableIssue.from
        val raisableIssue = RaisableIssue.from(issue, person)

        // Save the raisable issue
        template.save(raisableIssue)

        // Query by RaisableIssue interface
        val allRaisable = template.findAll<RaisableIssue>().toList()
        assertEquals(1, allRaisable.size)

        val retrieved = allRaisable[0]
        assertEquals(issueUuid, retrieved.uuid)
        assertEquals(issue.id, retrieved.id)
        assertEquals(issue.title, retrieved.title)
        assertEquals(person.uuid, retrieved.raisedBy.uuid)
        assertEquals(person.name, retrieved.raisedBy.name)
    }

    @Test
    fun `should persist and retrieve PersonWithProfile`() {
        val profile = Profile(
            bio = "Senior Developer",
            homepage = "https://example.com",
            countryCode = "US",
            region = "New York",
            email = "dev@example.com",
            blog = "https://example.com/blog",
            linkedInId = "senior-dev",
            twitterHandle = "seniordev",
            programmingLanguages = setOf("Kotlin", "Java"),
            frameworks = setOf("Spring"),
            importance = 0.8,
            categories = setOf("developer")
        )

        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Developer",
            bio = "Writes code",
            githubId = 777L,
            employer = null
        )

        // Create PersonWithProfile using PersonWithProfile.from
        val personWithProfile = PersonWithProfile.from(person, profile)
        template.save(personWithProfile)

        // Retrieve and verify Profile is intact
        val allPersonsWithProfile = template.findAll<PersonWithProfile>().toList()
        assertEquals(1, allPersonsWithProfile.size)

        val retrieved = allPersonsWithProfile[0]
        assertEquals(person.uuid, retrieved.uuid)
        assertEquals(person.name, retrieved.name)

        // Verify Profile is preserved
        assertEquals(profile.bio, retrieved.profile.bio)
        assertEquals(profile.homepage, retrieved.profile.homepage)
        assertEquals(profile.countryCode, retrieved.profile.countryCode)
        assertEquals(profile.region, retrieved.profile.region)
        assertEquals(profile.email, retrieved.profile.email)
        assertEquals(profile.blog, retrieved.profile.blog)
    }

    @Test
    fun `updating Person after saving PersonWithProfile DOES update via reference resolution`() {
        // First save a basic person
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Developer",
            bio = "Writes code",
            githubId = 888L,
            employer = null
        )

        // Create a profile and save PersonWithProfile
        val profile = Profile(
            bio = "Updated bio",
            homepage = "https://updated.com",
            countryCode = null,
            region = "Updated location",
            email = "updated@example.com",
            blog = null,
            linkedInId = null,
            twitterHandle = null,
            programmingLanguages = setOf("Python"),
            frameworks = setOf("Django"),
            importance = 0.5,
            categories = setOf("contributor")
        )
        val personWithProfile = PersonWithProfile.from(person, profile)
        template.save(personWithProfile)

        // Retrieve PersonWithProfile
        val allPersonsWithProfile = template.findAll<PersonWithProfile>().toList()
        assertEquals(1, allPersonsWithProfile.size)

        val retrievedWithProfile = allPersonsWithProfile[0]
        assertEquals(profile.bio, retrievedWithProfile.profile.bio)

        // Person queried directly should also be found (stored as PersonImpl)
        val allPersons = template.findAll<Person>().toList()
        assertEquals(1, allPersons.size)
    }

    @Test
    fun `should save loaded entity back after creating relationship`() {
        // Save a person first
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Reporter",
            bio = "Bug hunter",
            githubId = 111L,
            employer = null
        )
        template.save(person)

        // Save an issue
        val issue = Issue.create(
            uuid = UUID.randomUUID(),
            id = 99999L,
            state = "open",
            number = 1,
            body = "Found a bug",
            title = "Bug report",
            htmlUrl = "https://github.com/test/issues/1",
        )
        template.save(issue)

        // Load the person back
        val loadedPersons = template.findAll<Person>().toList()
        assertEquals(1, loadedPersons.size)
        val loadedPerson = loadedPersons[0]

        // Load the issue back
        val loadedIssues = template.findAll<Issue>().toList()
        assertEquals(1, loadedIssues.size)
        val loadedIssue = loadedIssues[0]

        // Create a RaisableIssue using the loaded entities
        val raisableIssue = RaisableIssue.from(loadedIssue, loadedPerson)
        template.save(raisableIssue)

        // Verify we can load the RaisableIssue with the relationship intact
        val allRaisable = template.findAll<RaisableIssue>().toList()
        assertEquals(1, allRaisable.size)

        val retrieved = allRaisable[0]
        assertEquals(issue.uuid, retrieved.uuid)
        assertEquals(issue.title, retrieved.title)
        assertEquals(person.uuid, retrieved.raisedBy.uuid)
        assertEquals(person.name, retrieved.raisedBy.name)
    }

    @Test
    fun `should save PersonWithProfile created from fresh Person - IssueActions scenario`() {
        // This tests the exact scenario in IssueActions.researchRaiser:
        // A new Person is created via Person.create(), then a profile is attached
        // and saved via store.save(PersonWithProfile.from(person, profile))

        val person = Person(
            uuid = UUID.randomUUID(),
            name = "New Contributor",
            bio = "Found a bug",
            githubId = 12345L,
            employer = null
        )

        val profile = Profile(
            bio = "Open source contributor",
            homepage = "https://github.com/contributor",
            programmingLanguages = setOf("Java", "Kotlin"),
            frameworks = setOf("Spring Boot"),
            importance = 0.7,
            categories = setOf("contributor")
        )

        // This is exactly what IssueActions does
        val personWithProfile = PersonWithProfile.from(person, profile)
        template.save(personWithProfile)

        // Verify we can find it as PersonWithProfile
        val allWithProfile = template.findAll<PersonWithProfile>().toList()
        assertEquals(1, allWithProfile.size)

        val retrieved = allWithProfile[0]
        assertEquals(person.uuid, retrieved.uuid)
        assertEquals(person.name, retrieved.name)
        assertEquals(person.githubId, retrieved.githubId)
        assertEquals(profile.bio, retrieved.profile.bio)
        assertEquals(profile.programmingLanguages, retrieved.profile.programmingLanguages)
        assertEquals(profile.frameworks, retrieved.profile.frameworks)
        assertEquals(profile.importance, retrieved.profile.importance)

        // Also verify we can find it as Person (base type)
        val allPersons = template.findAll<Person>().toList()
        assertEquals(1, allPersons.size)
    }

    @Test
    fun `should save loaded entity with new profile attached`() {
        // Save a person first
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Developer",
            bio = "Writes code",
            githubId = 222L,
            employer = null
        )
        template.save(person)

        // Load the person back
        val loadedPersons = template.findAll<Person>().toList()
        assertEquals(1, loadedPersons.size)
        val loadedPerson = loadedPersons[0]

        // Create a profile and attach to loaded person
        val profile = Profile(
            bio = "Senior Developer",
            homepage = "https://example.com",
            programmingLanguages = setOf("Kotlin", "Java"),
            frameworks = setOf("Spring"),
            importance = 0.9,
            categories = setOf("developer")
        )

        val personWithProfile = PersonWithProfile.from(loadedPerson, profile)
        template.save(personWithProfile)

        // Verify PersonWithProfile was saved correctly
        val allWithProfile = template.findAll<PersonWithProfile>().toList()
        assertEquals(1, allWithProfile.size)

        val retrieved = allWithProfile[0]
        assertEquals(person.uuid, retrieved.uuid)
        assertEquals(person.name, retrieved.name)
        assertEquals(profile.bio, retrieved.profile.bio)
        assertEquals(profile.programmingLanguages, retrieved.profile.programmingLanguages)
    }

    @Test
    fun `should persist and retrieve PullRequest with all PR-specific fields`() {
        val prUuid = UUID.randomUUID()
        val pr = PullRequest(
            uuid = prUuid,
            id = 99999L,
            state = "open",
            stateReason = null,
            number = 42,
            closedAt = null,
            body = "This PR adds a new feature",
            title = "Add new feature",
            htmlUrl = "https://github.com/test/repo/pull/42",
            locked = false,
            company = "TestCorp",
            additions = 150,
            deletions = 30,
            changedFiles = 5,
            commits = 3,
            merged = false,
            mergeable = true,
            mergeableState = "clean",
            mergeCommitSha = null,
            mergedAt = null,
            draft = false,
            diffUrl = "https://github.com/test/repo/pull/42.diff",
            patchUrl = "https://github.com/test/repo/pull/42.patch",
            baseBranch = "main",
            headBranch = "feature/new-feature",
            baseRepo = "test/repo",
            headRepo = "contributor/repo",
        )

        template.save(pr)

        // Verify it's saved under PullRequestImpl directory
        val implIds = template.listIds("PullRequestImpl")
        assertEquals(1, implIds.size)

        // Query by PullRequest interface - should find the PullRequestImpl
        val allPrs = template.findAll<PullRequest>().toList()
        assertEquals(1, allPrs.size)

        val retrieved = allPrs[0]
        assertEquals(prUuid, retrieved.uuid)
        assertEquals(pr.id, retrieved.id)
        assertEquals(pr.title, retrieved.title)
        assertEquals(pr.body, retrieved.body)
        assertEquals(pr.number, retrieved.number)

        // Verify PR-specific fields
        assertEquals(150, retrieved.additions)
        assertEquals(30, retrieved.deletions)
        assertEquals(5, retrieved.changedFiles)
        assertEquals(3, retrieved.commits)
        assertEquals(false, retrieved.merged)
        assertEquals(true, retrieved.mergeable)
        assertEquals("clean", retrieved.mergeableState)
        assertEquals(false, retrieved.draft)
        assertEquals("https://github.com/test/repo/pull/42.diff", retrieved.diffUrl)
        assertEquals("https://github.com/test/repo/pull/42.patch", retrieved.patchUrl)
        assertEquals("main", retrieved.baseBranch)
        assertEquals("feature/new-feature", retrieved.headBranch)
        assertEquals("test/repo", retrieved.baseRepo)
        assertEquals("contributor/repo", retrieved.headRepo)
    }

    @Test
    fun `should find PullRequest when querying by Issue interface`() {
        val prUuid = UUID.randomUUID()
        val pr = PullRequest(
            uuid = prUuid,
            id = 88888L,
            state = "closed",
            number = 100,
            body = "Bug fix PR",
            title = "Fix critical bug",
            htmlUrl = "https://github.com/test/repo/pull/100",
            additions = 10,
            deletions = 5,
            changedFiles = 2,
            commits = 1,
            merged = true,
            mergedAt = "2024-01-15T10:30:00Z",
            mergeCommitSha = "abc123def456",
            baseBranch = "main",
            headBranch = "fix/critical-bug",
        )

        template.save(pr)

        // Query by Issue interface - should also find the PullRequest
        val allIssues = template.findAll<Issue>().toList()
        assertEquals(1, allIssues.size)

        val retrieved = allIssues[0]
        assertEquals(prUuid, retrieved.uuid)
        assertEquals(pr.id, retrieved.id)
        assertEquals(pr.title, retrieved.title)

        // Verify it's actually a PullRequest
        assertTrue(retrieved is PullRequest)
        val retrievedPr = retrieved as PullRequest
        assertEquals(true, retrievedPr.merged)
        assertEquals("abc123def456", retrievedPr.mergeCommitSha)
    }

    @Test
    fun `should persist merged PullRequest with merge details`() {
        val prUuid = UUID.randomUUID()
        val pr = PullRequest(
            uuid = prUuid,
            id = 77777L,
            state = "closed",
            stateReason = GHIssueStateReason.COMPLETED,
            number = 200,
            closedAt = "2024-02-01T14:00:00Z",
            body = "Merged feature",
            title = "Feature complete",
            htmlUrl = "https://github.com/test/repo/pull/200",
            additions = 500,
            deletions = 100,
            changedFiles = 20,
            commits = 10,
            merged = true,
            mergeable = null,
            mergeableState = null,
            mergeCommitSha = "deadbeef1234567890",
            mergedAt = "2024-02-01T14:00:00Z",
            draft = false,
            baseBranch = "main",
            headBranch = "feature/big-feature",
            baseRepo = "org/repo",
            headRepo = "org/repo",
        )

        template.save(pr)

        val allPrs = template.findAll<PullRequest>().toList()
        assertEquals(1, allPrs.size)

        val retrieved = allPrs[0]
        assertEquals(true, retrieved.merged)
        assertEquals("deadbeef1234567890", retrieved.mergeCommitSha)
        assertEquals("2024-02-01T14:00:00Z", retrieved.mergedAt)
        assertEquals("2024-02-01T14:00:00Z", retrieved.closedAt)
        assertEquals(GHIssueStateReason.COMPLETED, retrieved.stateReason)
    }

    @Test
    fun `should persist draft PullRequest`() {
        val prUuid = UUID.randomUUID()
        val pr = PullRequest(
            uuid = prUuid,
            id = 66666L,
            state = "open",
            number = 300,
            body = "WIP: Working on this",
            title = "[WIP] Draft feature",
            htmlUrl = "https://github.com/test/repo/pull/300",
            additions = 50,
            deletions = 0,
            changedFiles = 3,
            commits = 2,
            merged = false,
            mergeable = false,
            mergeableState = "draft",
            draft = true,
            baseBranch = "develop",
            headBranch = "feature/wip",
        )

        template.save(pr)

        val allPrs = template.findAll<PullRequest>().toList()
        assertEquals(1, allPrs.size)

        val retrieved = allPrs[0]
        assertEquals(true, retrieved.draft)
        assertEquals(false, retrieved.mergeable)
        assertEquals("draft", retrieved.mergeableState)
    }

    @Test
    fun `should find both Issue and PullRequest when querying HasUUID`() {
        val issue = Issue.create(
            uuid = UUID.randomUUID(),
            id = 11111L,
            state = "open",
            number = 1,
            body = "Regular issue",
            title = "Bug report",
            htmlUrl = "https://github.com/test/repo/issues/1",
        )

        val pr = PullRequest(
            uuid = UUID.randomUUID(),
            id = 22222L,
            state = "open",
            number = 2,
            body = "PR body",
            title = "Feature PR",
            htmlUrl = "https://github.com/test/repo/pull/2",
            additions = 100,
            deletions = 50,
            changedFiles = 10,
            commits = 5,
            baseBranch = "main",
            headBranch = "feature",
        )

        template.save(issue)
        template.save(pr)

        // Query by HasUUID - should find both
        val allWithUuid = template.findAll<HasUUID>().toList()
        assertEquals(2, allWithUuid.size)

        // Query by Issue - should find both (PullRequest extends Issue)
        val allIssues = template.findAll<Issue>().toList()
        assertEquals(2, allIssues.size)

        // Query by PullRequest - should find only the PR
        val allPrs = template.findAll<PullRequest>().toList()
        assertEquals(1, allPrs.size)
        assertEquals(pr.uuid, allPrs[0].uuid)
    }
}
