package org.drivine.query

import com.embabel.shepherd.domain.Profile
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfileDeserializationTest {

    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    @Test
    fun `should deserialize Profile without uuid using default value`() {
        // This is the JSON that the LLM returns - missing uuid
        val llmJson = """
        {
          "bio": "Software Engineer and Open Source Enthusiast",
          "blog": "https://example.com/blog",
          "email": "contact@example.com",
          "homepage": "https://example.com",
          "location": "San Francisco, CA",
          "programmingLanguages": ["Kotlin", "Java"],
          "frameworks": ["Spring", "Ktor"],
          "importance": 0.75,
          "categories": ["developer", "contributor"]
        }
        """.trimIndent()

        // Should now work because uuid has a default value
        val profile = objectMapper.readValue(llmJson, Profile::class.java)

        assertEquals("Software Engineer and Open Source Enthusiast", profile.bio)
        assertEquals("https://example.com/blog", profile.blog)
        assertEquals("contact@example.com", profile.email)
        assertEquals("https://example.com", profile.homepage)
        assertEquals("San Francisco, CA", profile.location)
        assertEquals(setOf("Kotlin", "Java"), profile.programmingLanguages)
        assertEquals(setOf("Spring", "Ktor"), profile.frameworks)
        assertEquals(0.75, profile.importance)
        assertNotNull(profile.uuid) // Should be auto-generated
        assertNotNull(profile.retrieved) // Should use default value
    }

    @Test
    fun `should successfully deserialize Profile with uuid`() {
        val jsonWithUuid = """
        {
          "uuid": "550e8400-e29b-41d4-a716-446655440000",
          "bio": "Software Engineer and Open Source Enthusiast",
          "blog": "https://example.com/blog",
          "email": "contact@example.com",
          "homepage": "https://example.com",
          "location": "San Francisco, CA",
          "programmingLanguages": ["Python"],
          "frameworks": ["Django"],
          "importance": 0.9,
          "categories": ["maintainer"]
        }
        """.trimIndent()

        val profile = objectMapper.readValue(jsonWithUuid, Profile::class.java)

        assertEquals("Software Engineer and Open Source Enthusiast", profile.bio)
        assertEquals("https://example.com/blog", profile.blog)
        assertEquals("contact@example.com", profile.email)
        assertEquals("https://example.com", profile.homepage)
        assertEquals("San Francisco, CA", profile.location)
        assertEquals(setOf("Python"), profile.programmingLanguages)
        assertEquals(setOf("Django"), profile.frameworks)
        assertEquals(0.9, profile.importance)
        assertNotNull(profile.uuid)
        assertNotNull(profile.retrieved) // Should use default value
    }
}
