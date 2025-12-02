package com.embabel.shepherd.service

import com.embabel.shepherd.domain.GitHubProfile
import com.embabel.shepherd.domain.Organization
import com.embabel.shepherd.domain.Person
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.drivine.query.MixinTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kohsuke.github.GHUser
import java.util.*

class CommunityDataManagerTest {

    private lateinit var mixinTemplate: MixinTemplate
    private lateinit var communityDataManager: CommunityDataManager

    // In-memory storage for the mock
    private val organizations = mutableListOf<Organization>()
    private val persons = mutableListOf<Person>()

    @BeforeEach
    fun setUp() {
        organizations.clear()
        persons.clear()

        mixinTemplate = mockk {
            every { findAll(Organization::class.java) } answers { organizations.toList() }
            every { findAll(Person::class.java) } answers { persons.toList() }

            val entitySlot = slot<Any>()
            every { save(capture(entitySlot)) } answers {
                val entity = entitySlot.captured
                when (entity) {
                    is Organization -> {
                        organizations.removeIf { it.uuid == entity.uuid }
                        organizations.add(entity)
                    }

                    is Person -> {
                        persons.removeIf { it.uuid == entity.uuid }
                        persons.add(entity)
                    }
                }
                entity
            }
        }

        communityDataManager = CommunityDataManager(mixinTemplate)
    }

    @Nested
    inner class RetrieveOrCreateOrganizationTests {

        @Test
        fun `should return null for null company name`() {
            val result = communityDataManager.retrieveOrCreateEmployer(null)
            assertNull(result)
        }

        @Test
        fun `should return null for blank company name`() {
            val result = communityDataManager.retrieveOrCreateEmployer("")
            assertNull(result)

            val resultSpaces = communityDataManager.retrieveOrCreateEmployer("   ")
            assertNull(resultSpaces)
        }

        @Test
        fun `should create new employer when none exists`() {
            val result = communityDataManager.retrieveOrCreateEmployer("Google")

            assertNotNull(result)
            assertTrue(result!!.created)
            assertEquals("Google", result.entity.name)
        }

        @Test
        fun `should find existing employer with exact match`() {
            val existingOrganization = Organization(name = "Google")
            organizations.add(existingOrganization)

            val result = communityDataManager.retrieveOrCreateEmployer("Google")

            assertNotNull(result)
            assertFalse(result!!.created)
            assertEquals(existingOrganization.uuid, result.entity.uuid)
        }

        @Test
        fun `should find existing employer case-insensitively`() {
            val existingOrganization = Organization(name = "Google")
            organizations.add(existingOrganization)

            val result = communityDataManager.retrieveOrCreateEmployer("google")

            assertNotNull(result)
            assertFalse(result!!.created)
            assertEquals(existingOrganization.uuid, result.entity.uuid)
        }

        @Test
        fun `should find existing employer with UPPERCASE input`() {
            val existingOrganization = Organization(name = "Google")
            organizations.add(existingOrganization)

            val result = communityDataManager.retrieveOrCreateEmployer("GOOGLE")

            assertNotNull(result)
            assertFalse(result!!.created)
            assertEquals(existingOrganization.uuid, result.entity.uuid)
        }

        @Test
        fun `should find existing employer when input has suffix`() {
            val existingOrganization = Organization(name = "Google")
            organizations.add(existingOrganization)

            val resultInc = communityDataManager.retrieveOrCreateEmployer("Google Inc")
            assertFalse(resultInc!!.created)
            assertEquals(existingOrganization.uuid, resultInc.entity.uuid)

            val resultLlc = communityDataManager.retrieveOrCreateEmployer("Google LLC")
            assertFalse(resultLlc!!.created)
            assertEquals(existingOrganization.uuid, resultLlc.entity.uuid)

            val resultCorp = communityDataManager.retrieveOrCreateEmployer("Google Corporation")
            assertFalse(resultCorp!!.created)
            assertEquals(existingOrganization.uuid, resultCorp.entity.uuid)
        }

        @Test
        fun `should find existing employer via alias`() {
            val existingOrganization = Organization(
                name = "Alphabet Inc",
                aliases = setOf("google", "youtube")
            )
            organizations.add(existingOrganization)

            val result = communityDataManager.retrieveOrCreateEmployer("Google")

            assertNotNull(result)
            assertFalse(result!!.created)
            assertEquals(existingOrganization.uuid, result.entity.uuid)
        }

        @Test
        fun `should find existing employer via alias with suffix`() {
            val existingOrganization = Organization(
                name = "Meta",
                aliases = setOf("facebook")
            )
            organizations.add(existingOrganization)

            val result = communityDataManager.retrieveOrCreateEmployer("Facebook Inc.")

            assertNotNull(result)
            assertFalse(result!!.created)
            assertEquals(existingOrganization.uuid, result.entity.uuid)
        }

        @Test
        fun `should create employer with normalized alias when different from name`() {
            val result = communityDataManager.retrieveOrCreateEmployer("Acme Inc.")

            assertNotNull(result)
            assertTrue(result!!.created)
            assertEquals("Acme Inc.", result.entity.name)
            assertTrue(result.entity.aliases.contains("acme"))
        }

        @Test
        fun `should create employer without alias when normalized equals lowercase name`() {
            val result = communityDataManager.retrieveOrCreateEmployer("google")

            assertNotNull(result)
            assertTrue(result!!.created)
            assertEquals("google", result.entity.name)
            assertTrue(result.entity.aliases.isEmpty())
        }

        @Test
        fun `should not create duplicate employers`() {
            val existingOrganization = Organization(name = "Red Hat")
            organizations.add(existingOrganization)

            // All these should find the existing employer
            val inputs = listOf(
                "Red Hat",
                "red hat",
                "RED HAT",
                "Red Hat Inc",
                "Red Hat LLC",
                "Red Hat, Inc."
            )

            for (input in inputs) {
                val result = communityDataManager.retrieveOrCreateEmployer(input)
                assertFalse(result!!.created, "Expected existing for input: $input")
                assertEquals(existingOrganization.uuid, result.entity.uuid, "UUID mismatch for input: $input")
            }
        }
    }

    @Nested
    inner class RetrieveOrCreatePersonFromTests {

        private fun mockGHUser(
            id: Long = 12345L,
            login: String = "testuser",
            name: String? = "Test User",
            bio: String? = "A test bio",
            blog: String? = null,
            location: String? = null,
            type: String = "User",
            publicRepoCount: Int = 10,
            avatarUrl: String? = "https://avatars.githubusercontent.com/u/12345",
            company: String? = null
        ): GHUser = mockk {
            every { getId() } returns id
            every { getLogin() } returns login
            every { getName() } returns name
            every { getBio() } returns bio
            every { getBlog() } returns blog
            every { getLocation() } returns location
            every { getType() } returns type
            every { getPublicRepoCount() } returns publicRepoCount
            every { getAvatarUrl() } returns avatarUrl
            every { getCompany() } returns company
        }

        @Test
        fun `should create new person when none exists`() {
            val ghUser = mockGHUser()

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertEquals("Test User", result.entity.name)
            assertNotNull(result.entity.github)
            assertEquals("testuser", result.entity.github?.login)
            assertEquals("A test bio", result.entity.github?.bio)
            assertEquals("User", result.entity.github?.type)
            assertNull(result.entity.employer)
        }

        @Test
        fun `should use login as name when name is null`() {
            val ghUser = mockGHUser(name = null, login = "cooldev")

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertEquals("cooldev", result.entity.name)
        }

        @Test
        fun `should find existing person by GitHub login`() {
            val existingPerson = Person(
                uuid = UUID.randomUUID(),
                name = "Existing User",
                github = GitHubProfile(
                    login = "testuser",
                    name = "Existing User",
                    bio = "Old bio",
                    blog = null,
                    location = null,
                    type = "User",
                    publicRepoCount = 5,
                    avatarUrl = null,
                ),
            )
            persons.add(existingPerson)

            val ghUser = mockGHUser(id = 12345L, name = "Updated Name")

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertFalse(result.created)
            assertEquals(existingPerson.uuid, result.entity.uuid)
            assertEquals("Existing User", result.entity.name) // Original name preserved
        }

        @Test
        fun `should create person with new employer`() {
            val ghUser = mockGHUser(company = "Google")

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertNotNull(result.entity.employer)
            assertEquals("Google", result.entity.employer?.name)
        }

        @Test
        fun `should create person with existing employer`() {
            val existingOrganization = Organization(name = "Google")
            organizations.add(existingOrganization)

            val ghUser = mockGHUser(company = "Google Inc")

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertNotNull(result.entity.employer)
            assertEquals(existingOrganization.uuid, result.entity.employer?.uuid)
        }

        @Test
        fun `should create person with employer found via alias`() {
            val existingOrganization = Organization(
                name = "Alphabet",
                aliases = setOf("google")
            )
            organizations.add(existingOrganization)

            val ghUser = mockGHUser(company = "Google")

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertNotNull(result.entity.employer)
            assertEquals(existingOrganization.uuid, result.entity.employer?.uuid)
        }

        @Test
        fun `should handle null company`() {
            val ghUser = mockGHUser(company = null)

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertNull(result.entity.employer)
        }

        @Test
        fun `should handle blank company`() {
            val ghUser = mockGHUser(company = "   ")

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertTrue(result.created)
            assertNull(result.entity.employer)
        }

        @Test
        fun `should handle null bio`() {
            val ghUser = mockGHUser(bio = null)

            val result = communityDataManager.retrieveOrCreatePersonFrom(ghUser)

            assertNotNull(result.entity.github)
            assertNull(result.entity.github?.bio)
        }
    }
}
