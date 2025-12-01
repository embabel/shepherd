package com.embabel.shepherd.service

import com.embabel.shepherd.domain.Organization
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class OrganizationCanonicalizerTest {

    private val canonicalizer = RegexOrganizationCanonicalizer()

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
            val organization = Organization(name = "Google")

            assertTrue(canonicalizer.matches("Google", organization))
            assertTrue(canonicalizer.matches("google", organization))
            assertTrue(canonicalizer.matches("GOOGLE", organization))
            assertTrue(canonicalizer.matches("gOoGlE", organization))
        }

        @Test
        fun `should match with common suffixes`() {
            val organization = Organization(name = "Google")

            assertTrue(canonicalizer.matches("Google Inc", organization))
            assertTrue(canonicalizer.matches("Google Inc.", organization))
            assertTrue(canonicalizer.matches("Google LLC", organization))
            assertTrue(canonicalizer.matches("Google Corporation", organization))
            assertTrue(canonicalizer.matches("Google Company", organization))
        }

        @Test
        fun `should match against aliases`() {
            val organization = Organization(
                name = "Alphabet Inc",
                aliases = setOf("google", "youtube", "deepmind")
            )

            assertTrue(canonicalizer.matches("Google", organization))
            assertTrue(canonicalizer.matches("YouTube", organization))
            assertTrue(canonicalizer.matches("DeepMind", organization))
            assertTrue(canonicalizer.matches("Alphabet Inc", organization))
            assertTrue(canonicalizer.matches("Alphabet", organization))
        }

        @Test
        fun `should match aliases with suffixes`() {
            val organization = Organization(
                name = "Meta",
                aliases = setOf("facebook")
            )

            assertTrue(canonicalizer.matches("Facebook Inc", organization))
            assertTrue(canonicalizer.matches("Facebook LLC", organization))
            assertTrue(canonicalizer.matches("Meta Corporation", organization))
        }

        @Test
        fun `should not match unrelated companies`() {
            val organization = Organization(name = "Google")

            assertFalse(canonicalizer.matches("Microsoft", organization))
            assertFalse(canonicalizer.matches("Apple", organization))
            assertFalse(canonicalizer.matches("Googles", organization)) // typo
        }

        @Test
        fun `should handle empty aliases`() {
            val organization = Organization(name = "Startup", aliases = emptySet())

            assertTrue(canonicalizer.matches("Startup", organization))
            assertFalse(canonicalizer.matches("OtherCompany", organization))
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
            val organization = Organization(name = "Red Hat")
            assertTrue(canonicalizer.matches(input, organization))
        }
    }

    @Nested
    inner class CustomCanonicalizerTests {

        @Test
        fun `should allow custom canonicalizer implementation`() {
            // Custom canonicalizer that only lowercases
            val simpleCanonicalizer = object : OrganizationCanonicalizer {
                override fun canonicalize(organizationName: String): String {
                    return organizationName.lowercase().trim()
                }
            }

            // This won't strip "Inc" like the default
            assertEquals("google inc", simpleCanonicalizer.canonicalize("Google Inc"))

            val organization = Organization(name = "Google Inc")
            assertTrue(simpleCanonicalizer.matches("google inc", organization))
            assertFalse(simpleCanonicalizer.matches("Google", organization)) // Won't match without suffix
        }
    }
}
