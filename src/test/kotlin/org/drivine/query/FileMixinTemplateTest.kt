package org.drivine.query

import com.embabel.shepherd.domain.HasUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*

class FileMixinTemplateTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var template: FileMixinTemplate

    @BeforeEach
    fun setUp() {
        template = FileMixinTemplate(baseDir = tempDir.resolve(".data"))
    }

    @AfterEach
    fun tearDown() {
        template.clear()
    }

    @Test
    fun `should save and retrieve entity by id`() {
        val entity = TestEntity(id = 123L, name = "Test", description = "A test entity")

        val saved = template.save(entity)

        assertEquals(entity, saved)

        val retrieved = template.findByIdAs<TestEntity>(123L)
        assertNotNull(retrieved)
        assertEquals(entity, retrieved)
    }

    @Test
    fun `should return null for non-existent id`() {
        val result = template.findByIdAs<TestEntity>(999L)
        assertNull(result)
    }

    @Test
    fun `should overwrite entity on save with same id`() {
        val entity1 = TestEntity(id = 1L, name = "Original", description = "First version")
        val entity2 = TestEntity(id = 1L, name = "Updated", description = "Second version")

        template.save(entity1)
        template.save(entity2)

        val ids = template.listIds("TestEntity")
        assertEquals(1, ids.size)
        assertEquals("1", ids[0])
    }

    @Test
    fun `should throw exception when saving entity without Id annotation`() {
        val entity = EntityWithoutId(name = "Test")

        assertThrows(IllegalArgumentException::class.java) {
            template.save(entity)
        }
    }

    @Test
    fun `should list all ids for a type`() {
        template.save(TestEntity(id = 1L, name = "First", description = null))
        template.save(TestEntity(id = 2L, name = "Second", description = null))
        template.save(TestEntity(id = 3L, name = "Third", description = null))

        val ids = template.listIds("TestEntity")

        assertEquals(3, ids.size)
        assertTrue(ids.containsAll(listOf("1", "2", "3")))
    }

    @Test
    fun `should return empty list for unknown type`() {
        val ids = template.listIds("NonExistentType")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `should clear all data`() {
        template.save(TestEntity(id = 1L, name = "Test", description = null))
        template.save(AnotherEntity(id = 2L, value = 42))

        template.clear()

        assertTrue(template.listIds("TestEntity").isEmpty())
        assertTrue(template.listIds("AnotherEntity").isEmpty())
    }

    @Test
    fun `should handle Int id`() {
        val entity = EntityWithIntId(id = 456, name = "Int ID Entity")

        template.save(entity)

        val ids = template.listIds("EntityWithIntId")
        assertEquals(1, ids.size)
        assertEquals("456", ids[0])
    }

    @Test
    fun `should find all entities of a type`() {
        template.save(TestEntity(id = 1L, name = "First", description = "desc1"))
        template.save(TestEntity(id = 2L, name = "Second", description = "desc2"))
        template.save(TestEntity(id = 3L, name = "Third", description = null))

        val all = template.findAll<TestEntity>().toList()

        assertEquals(3, all.size)
        assertTrue(all.any { it.id == 1L && it.name == "First" })
        assertTrue(all.any { it.id == 2L && it.name == "Second" })
        assertTrue(all.any { it.id == 3L && it.name == "Third" })
    }

    @Test
    fun `should return empty iterable for non-existent type`() {
        val all = template.findAll<TestEntity>().toList()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `should only return entities of requested type`() {
        template.save(TestEntity(id = 1L, name = "Test", description = null))
        template.save(AnotherEntity(id = 2L, value = 42))

        val testEntities = template.findAll<TestEntity>().toList()
        val anotherEntities = template.findAll<AnotherEntity>().toList()

        assertEquals(1, testEntities.size)
        assertEquals(1, anotherEntities.size)
        assertEquals("Test", testEntities[0].name)
        assertEquals(42, anotherEntities[0].value)
    }
}

data class TestEntity(
    @Id val id: Long,
    val name: String,
    val description: String?
)

data class AnotherEntity(
    @Id val id: Long,
    val value: Int
)

data class EntityWithoutId(
    val name: String
)

data class EntityWithIntId(
    @Id val id: Int,
    val name: String
)

data class EntityWithUUID(
    override val uuid: UUID,
    val name: String
) : HasUUID

class IdExtractorTest {

    @Test
    fun `AnnotationIdExtractor should extract id from annotated property`() {
        val entity = TestEntity(id = 42L, name = "Test", description = null)
        val id = AnnotationIdExtractor.extractId(entity)
        assertEquals("42", id)
    }

    @Test
    fun `AnnotationIdExtractor should return null for entity without annotation`() {
        val entity = EntityWithoutId(name = "Test")
        val id = AnnotationIdExtractor.extractId(entity)
        assertNull(id)
    }

    @Test
    fun `InterfaceIdExtractor should extract id from interface property`() {
        val extractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val uuid = UUID.randomUUID()
        val entity = EntityWithUUID(uuid = uuid, name = "Test")

        val id = extractor.extractId(entity)
        assertEquals(uuid.toString(), id)
    }

    @Test
    fun `InterfaceIdExtractor should return null for non-matching entity`() {
        val extractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val entity = TestEntity(id = 1L, name = "Test", description = null)

        val id = extractor.extractId(entity)
        assertNull(id)
    }

    @Test
    fun `CompositeIdExtractor should try extractors in order`() {
        val uuidExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val composite = CompositeIdExtractor(uuidExtractor, AnnotationIdExtractor)

        // Entity with both UUID and @Id - should use UUID (first extractor)
        val uuid = UUID.randomUUID()
        val entityWithBoth = EntityWithBothIds(uuid = uuid, id = 999L, name = "Both")

        val id = composite.extractId(entityWithBoth)
        assertEquals(uuid.toString(), id)
    }

    @Test
    fun `CompositeIdExtractor should fall back to next extractor`() {
        val uuidExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val composite = CompositeIdExtractor(uuidExtractor, AnnotationIdExtractor)

        // Entity with only @Id - should fall back to annotation extractor
        val entity = TestEntity(id = 123L, name = "Test", description = null)

        val id = composite.extractId(entity)
        assertEquals("123", id)
    }

    @Nested
    inner class FileMixinTemplateWithHasUUID {

        @TempDir
        lateinit var tempDir: Path

        @Test
        fun `should save and retrieve entity using HasUUID extractor`() {
            val uuidExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
            val template = FileMixinTemplate(
                baseDir = tempDir.resolve(".data"),
                idExtractor = uuidExtractor
            )

            val uuid = UUID.randomUUID()
            val entity = EntityWithUUID(uuid = uuid, name = "UUID Entity")

            template.save(entity)

            val ids = template.listIds("EntityWithUUID")
            assertEquals(1, ids.size)
        }

        @Test
        fun `should save entity using composite extractor with HasUUID priority`() {
            val uuidExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
            val composite = CompositeIdExtractor(uuidExtractor, AnnotationIdExtractor)
            val template = FileMixinTemplate(
                baseDir = tempDir.resolve(".data"),
                idExtractor = composite
            )

            val uuid = UUID.randomUUID()
            val entity = EntityWithBothIds(uuid = uuid, id = 999L, name = "Both IDs")

            template.save(entity)

            val ids = template.listIds("EntityWithBothIds")
            assertEquals(1, ids.size)
        }
    }
}

data class EntityWithBothIds(
    override val uuid: UUID,
    @Id val id: Long,
    val name: String
) : HasUUID
