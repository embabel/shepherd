package com.embabel.shepherd.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EntityStatusTest {

    @Test
    fun `should return existing entity when found`() {
        val existing = "existing value"

        val result = EntityStatus.retrieveOrCreate(
            retriever = { existing },
            creator = { "new value" }
        )

        assertFalse(result.created)
        assertEquals("existing value", result.entity)
    }

    @Test
    fun `should create new entity when not found`() {
        val result = EntityStatus.retrieveOrCreate(
            retriever = { null },
            creator = { "new value" }
        )

        assertTrue(result.created)
        assertEquals("new value", result.entity)
    }

    @Test
    fun `should not call creator when entity exists`() {
        var creatorCalled = false

        EntityStatus.retrieveOrCreate(
            retriever = { "existing" },
            creator = {
                creatorCalled = true
                "new"
            }
        )

        assertFalse(creatorCalled)
    }

    @Test
    fun `should have correct created flag for new entity`() {
        val result = EntityStatus(entity = "test", created = true)
        assertTrue(result.created)
    }

    @Test
    fun `should have correct created flag for existing entity`() {
        val result = EntityStatus(entity = "test", created = false)
        assertFalse(result.created)
    }
}
