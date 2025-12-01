package com.embabel.shepherd.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EmployerTest {

    @Nested
    inner class EmployerCreationTests {

        @Test
        fun `should create employer with name only`() {
            val employer = Employer(name = "Google")

            assertEquals("Google", employer.name)
            assertTrue(employer.aliases.isEmpty())
            assertNotNull(employer.uuid)
        }

        @Test
        fun `should create employer with aliases`() {
            val employer = Employer(
                name = "Alphabet",
                aliases = setOf("Google", "YouTube")
            )

            assertEquals("Alphabet", employer.name)
            assertEquals(setOf("Google", "YouTube"), employer.aliases)
        }

        @Test
        fun `should generate unique UUIDs`() {
            val employer1 = Employer(name = "Company A")
            val employer2 = Employer(name = "Company B")

            assertNotEquals(employer1.uuid, employer2.uuid)
        }

        @Test
        fun `should be a data class with equality`() {
            val uuid = java.util.UUID.randomUUID()
            val employer1 = Employer(name = "Google", aliases = setOf("Alphabet"), uuid = uuid)
            val employer2 = Employer(name = "Google", aliases = setOf("Alphabet"), uuid = uuid)

            assertEquals(employer1, employer2)
        }
    }
}
