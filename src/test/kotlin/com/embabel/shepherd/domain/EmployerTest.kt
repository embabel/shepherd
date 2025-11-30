package com.embabel.shepherd.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class EmployerTest {

    @Nested
    inner class NormalizeTests {

        @Test
        fun `should convert to lowercase`() {
            assertEquals("google", Employer.canonicalize("Google"))
            assertEquals("google", Employer.canonicalize("GOOGLE"))
            assertEquals("google", Employer.canonicalize("gOoGlE"))
        }

        @ParameterizedTest
        @CsvSource(
            "Google Inc, google",
            "Google Inc., google",
            "Google LLC, google",
            "Google LLC., google",
            "Google Ltd, google",
            "Google Ltd., google",
            "Google Corp, google",
            "Google Corp., google",
            "Google Corporation, google",
            "Google Company, google",
            "Google Co, google",
            "Google Co., google",
        )
        fun `should remove common company suffixes`(input: String, expected: String) {
            assertEquals(expected, Employer.canonicalize(input))
        }

        @Test
        fun `should remove punctuation`() {
            assertEquals("acme", Employer.canonicalize("Acme."))
            assertEquals("acme", Employer.canonicalize("Acme,"))
            assertEquals("acme industries", Employer.canonicalize("Acme, Industries"))
        }

        @Test
        fun `should trim whitespace`() {
            assertEquals("google", Employer.canonicalize("  Google  "))
            assertEquals("google", Employer.canonicalize("Google "))
        }

        @Test
        fun `should handle multiple normalizations together`() {
            assertEquals("acme industries", Employer.canonicalize("  ACME Industries Inc.  "))
            assertEquals("tech startup", Employer.canonicalize("Tech Startup LLC."))
        }

        @Test
        fun `should preserve internal spacing`() {
            assertEquals("red hat", Employer.canonicalize("Red Hat"))
            assertEquals("the new york times", Employer.canonicalize("The New York Times Company"))
        }

        @Test
        fun `should not remove suffix-like words in the middle`() {
            assertEquals("incorporate solutions", Employer.canonicalize("Incorporate Solutions"))
            assertEquals("llc partners", Employer.canonicalize("LLC Partners"))
        }
    }

    @Nested
    inner class MatchesTests {

        @Test
        fun `should match exact canonical name case-insensitively`() {
            val employer = Employer(name = "Google")

            assertTrue(employer.matches("Google"))
            assertTrue(employer.matches("google"))
            assertTrue(employer.matches("GOOGLE"))
            assertTrue(employer.matches("gOoGlE"))
        }

        @Test
        fun `should match with common suffixes`() {
            val employer = Employer(name = "Google")

            assertTrue(employer.matches("Google Inc"))
            assertTrue(employer.matches("Google Inc."))
            assertTrue(employer.matches("Google LLC"))
            assertTrue(employer.matches("Google Corporation"))
            assertTrue(employer.matches("Google Company"))
        }

        @Test
        fun `should match against aliases`() {
            val employer = Employer(
                name = "Alphabet Inc",
                aliases = setOf("google", "youtube", "deepmind")
            )

            assertTrue(employer.matches("Google"))
            assertTrue(employer.matches("YouTube"))
            assertTrue(employer.matches("DeepMind"))
            assertTrue(employer.matches("Alphabet Inc"))
            assertTrue(employer.matches("Alphabet"))
        }

        @Test
        fun `should match aliases with suffixes`() {
            val employer = Employer(
                name = "Meta",
                aliases = setOf("facebook")
            )

            assertTrue(employer.matches("Facebook Inc"))
            assertTrue(employer.matches("Facebook LLC"))
            assertTrue(employer.matches("Meta Corporation"))
        }

        @Test
        fun `should not match unrelated companies`() {
            val employer = Employer(name = "Google")

            assertFalse(employer.matches("Microsoft"))
            assertFalse(employer.matches("Apple"))
            assertFalse(employer.matches("Googles")) // typo
        }

        @Test
        fun `should handle empty aliases`() {
            val employer = Employer(name = "Startup", aliases = emptySet())

            assertTrue(employer.matches("Startup"))
            assertFalse(employer.matches("OtherCompany"))
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "Red Hat",
                "Red Hat Inc",
                "Red Hat, Inc.",
                "RED HAT",
                "red hat",
                "Red Hat LLC",
            ]
        )
        fun `should match multi-word company names`(input: String) {
            val employer = Employer(name = "Red Hat")
            assertTrue(employer.matches(input))
        }
    }

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
    }
}
