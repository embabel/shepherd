package org.drivine.query

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.isAccessible

/**
 * Marks a property as the identifier for the entity.
 * Similar to Spring Data's @Id annotation.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Id

/**
 * Strategy for extracting an identifier from an entity.
 */
interface IdExtractor {
    /**
     * Extract the ID from the given entity, or return null if not applicable.
     */
    fun <T : Any> extractId(entity: T): String?
}

/**
 * Extracts ID from properties annotated with @Id.
 */
object AnnotationIdExtractor : IdExtractor {
    override fun <T : Any> extractId(entity: T): String? {
        val kClass = entity::class
        for (prop in kClass.declaredMemberProperties) {
            @Suppress("UNCHECKED_CAST")
            val property = prop as KProperty1<T, *>
            if (property.findAnnotation<Id>() != null) {
                property.isAccessible = true
                return property.get(entity)?.toString()
            }
        }
        return null
    }
}

/**
 * Extracts ID from entities implementing HasId interface.
 */
class InterfaceIdExtractor<I : Any>(
    private val idInterface: Class<I>,
    private val idProperty: (I) -> Any?
) : IdExtractor {
    override fun <T : Any> extractId(entity: T): String? {
        if (!idInterface.isInstance(entity)) return null
        @Suppress("UNCHECKED_CAST")
        return idProperty(entity as I)?.toString()
    }
}

/**
 * Composite extractor that tries multiple strategies in order.
 */
class CompositeIdExtractor(
    private val extractors: List<IdExtractor>
) : IdExtractor {
    constructor(vararg extractors: IdExtractor) : this(extractors.toList())

    override fun <T : Any> extractId(entity: T): String? {
        for (extractor in extractors) {
            val id = extractor.extractId(entity)
            if (id != null) return id
        }
        return null
    }
}

/**
 * Template that returns mixins
 */
interface MixinTemplate {

    fun <T> findById(id: String, clazz: Class<T>, mixins: List<Class<out T>>): T?

    fun <T> findById(id: String, clazz: Class<T>): T? = findById(id, clazz, emptyList())

    fun <T, U : T> findById(id: String, clazz: Class<T>, mixin: Class<U>): U?

    fun <T> save(entity: T): T

    fun <T, U : T> eraseSpecialization(entity: U, mixin: Class<U>): T?

    fun <T> findAll(classType: Class<T>): Iterable<T>

}

/**
 * Reified extension to find by ID with a specific type.
 */
inline fun <reified T : Any> MixinTemplate.findByIdAs(id: String): T? = findById(id, T::class.java)

/**
 * Reified extension to find by ID with a specific type (alternative syntax).
 */
inline fun <reified T : Any> MixinTemplate.findById(id: String): T? = findById(id, T::class.java)

/**
 * Reified extension to find all entities of a specific type.
 */
inline fun <reified T : Any> MixinTemplate.findAll(): Iterable<T> = findAll(T::class.java)