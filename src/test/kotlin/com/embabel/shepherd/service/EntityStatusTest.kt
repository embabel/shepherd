package com.embabel.shepherd.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EntityStatusTest {

    @Test
    fun `should return existing entity when found`() {
        val existing = "existing value"

        val result = EntityStatus.retrieveOrCreate(
            retriever = { existing },
            creator = { NewEntity("new value", emptyList()) }
        )

        assertFalse(result.created)
        assertEquals("existing value", result.entity)
    }

    @Test
    fun `should create new entity when not found`() {
        val result = EntityStatus.retrieveOrCreate(
            retriever = { null },
            creator = { NewEntity("new value", emptyList()) }
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
                NewEntity("new", emptyList())
            }
        )

        assertFalse(creatorCalled)
    }

    @Test
    fun `NewEntity should have created flag true`() {
        val result = NewEntity(entity = "test", otherNewEntities = emptyList())
        assertTrue(result.created)
    }

    @Test
    fun `ExistingEntity should have created flag false`() {
        val result = ExistingEntity(entity = "test")
        assertFalse(result.created)
    }

    @Test
    fun `NewEntity should include entity in newEntities list`() {
        val result = NewEntity(entity = "main", otherNewEntities = listOf("other1", "other2"))
        assertEquals(listOf("main", "other1", "other2"), result.newEntities)
    }

    @Test
    fun `ExistingEntity should have empty newEntities list`() {
        val result = ExistingEntity(entity = "test")
        assertTrue(result.newEntities.isEmpty())
    }
}
