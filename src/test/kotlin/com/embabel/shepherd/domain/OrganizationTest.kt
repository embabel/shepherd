package com.embabel.shepherd.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OrganizationTest {

    @Nested
    inner class OrganizationCreationTests {

        @Test
        fun `should create employer with name only`() {
            val organization = Organization(name = "Google")

            assertEquals("Google", organization.name)
            assertTrue(organization.aliases.isEmpty())
            assertNotNull(organization.uuid)
        }

        @Test
        fun `should create employer with aliases`() {
            val organization = Organization(
                name = "Alphabet",
                aliases = setOf("Google", "YouTube")
            )

            assertEquals("Alphabet", organization.name)
            assertEquals(setOf("Google", "YouTube"), organization.aliases)
        }

        @Test
        fun `should generate unique UUIDs`() {
            val organization1 = Organization(name = "Company A")
            val organization2 = Organization(name = "Company B")

            assertNotEquals(organization1.uuid, organization2.uuid)
        }

        @Test
        fun `should be a data class with equality`() {
            val uuid = java.util.UUID.randomUUID()
            val organization1 = Organization(name = "Google", aliases = setOf("Alphabet"), uuid = uuid)
            val organization2 = Organization(name = "Google", aliases = setOf("Alphabet"), uuid = uuid)

            assertEquals(organization1, organization2)
        }
    }
}
