package com.embabel.shepherd.service

import com.embabel.shepherd.domain.Employer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class EmployerCanonicalizerTest {

    private val canonicalizer = RegexEmployerCanonicalizer()

    @Nested
    inner class CanonicalizeTests {

        @Test
        fun `should convert to lowercase`() {
            assertEquals("google", canonicalizer.canonicalize("Google"))
            assertEquals("google", canonicalizer.canonicalize("GOOGLE"))
            assertEquals("google", canonicalizer.canonicalize("gOoGlE"))
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
            assertEquals(expected, canonicalizer.canonicalize(input))
        }

        @Test
        fun `should remove punctuation`() {
            assertEquals("acme", canonicalizer.canonicalize("Acme."))
            assertEquals("acme", canonicalizer.canonicalize("Acme,"))
            assertEquals("acme industries", canonicalizer.canonicalize("Acme, Industries"))
        }

        @Test
        fun `should trim whitespace`() {
            assertEquals("google", canonicalizer.canonicalize("  Google  "))
            assertEquals("google", canonicalizer.canonicalize("Google "))
        }

        @Test
        fun `should handle multiple normalizations together`() {
            assertEquals("acme industries", canonicalizer.canonicalize("  ACME Industries Inc.  "))
            assertEquals("tech startup", canonicalizer.canonicalize("Tech Startup LLC."))
        }

        @Test
        fun `should preserve internal spacing`() {
            assertEquals("red hat", canonicalizer.canonicalize("Red Hat"))
            assertEquals("the new york times", canonicalizer.canonicalize("The New York Times Company"))
        }

        @Test
        fun `should not remove suffix-like words in the middle`() {
            assertEquals("incorporate solutions", canonicalizer.canonicalize("Incorporate Solutions"))
            assertEquals("llc partners", canonicalizer.canonicalize("LLC Partners"))
        }
    }

    @Nested
    inner class MatchesTests {

        @Test
        fun `should match exact canonical name case-insensitively`() {
            val employer = Employer(name = "Google")

            assertTrue(canonicalizer.matches("Google", employer))
            assertTrue(canonicalizer.matches("google", employer))
            assertTrue(canonicalizer.matches("GOOGLE", employer))
            assertTrue(canonicalizer.matches("gOoGlE", employer))
        }

        @Test
        fun `should match with common suffixes`() {
            val employer = Employer(name = "Google")

            assertTrue(canonicalizer.matches("Google Inc", employer))
            assertTrue(canonicalizer.matches("Google Inc.", employer))
            assertTrue(canonicalizer.matches("Google LLC", employer))
            assertTrue(canonicalizer.matches("Google Corporation", employer))
            assertTrue(canonicalizer.matches("Google Company", employer))
        }

        @Test
        fun `should match against aliases`() {
            val employer = Employer(
                name = "Alphabet Inc",
                aliases = setOf("google", "youtube", "deepmind")
            )

            assertTrue(canonicalizer.matches("Google", employer))
            assertTrue(canonicalizer.matches("YouTube", employer))
            assertTrue(canonicalizer.matches("DeepMind", employer))
            assertTrue(canonicalizer.matches("Alphabet Inc", employer))
            assertTrue(canonicalizer.matches("Alphabet", employer))
        }

        @Test
        fun `should match aliases with suffixes`() {
            val employer = Employer(
                name = "Meta",
                aliases = setOf("facebook")
            )

            assertTrue(canonicalizer.matches("Facebook Inc", employer))
            assertTrue(canonicalizer.matches("Facebook LLC", employer))
            assertTrue(canonicalizer.matches("Meta Corporation", employer))
        }

        @Test
        fun `should not match unrelated companies`() {
            val employer = Employer(name = "Google")

            assertFalse(canonicalizer.matches("Microsoft", employer))
            assertFalse(canonicalizer.matches("Apple", employer))
            assertFalse(canonicalizer.matches("Googles", employer)) // typo
        }

        @Test
        fun `should handle empty aliases`() {
            val employer = Employer(name = "Startup", aliases = emptySet())

            assertTrue(canonicalizer.matches("Startup", employer))
            assertFalse(canonicalizer.matches("OtherCompany", employer))
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
            assertTrue(canonicalizer.matches(input, employer))
        }
    }

    @Nested
    inner class CustomCanonicalizerTests {

        @Test
        fun `should allow custom canonicalizer implementation`() {
            // Custom canonicalizer that only lowercases
            val simpleCanonicalizer = object : EmployerCanonicalizer {
                override fun canonicalize(companyName: String): String {
                    return companyName.lowercase().trim()
                }
            }

            // This won't strip "Inc" like the default
            assertEquals("google inc", simpleCanonicalizer.canonicalize("Google Inc"))

            val employer = Employer(name = "Google Inc")
            assertTrue(simpleCanonicalizer.matches("google inc", employer))
            assertFalse(simpleCanonicalizer.matches("Google", employer)) // Won't match without suffix
        }
    }
}
