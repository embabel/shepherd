package org.drivine.query

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

/**
 * Interface for extracting UUIDs from entities.
 */
fun interface UuidExtractor {
    fun extractUuid(entity: Any): UUID?
}

/**
 * File-based implementation of MixinTemplate that serializes entities to JSON files.
 * Entities are stored in a .data directory, organized by type.
 * Not intended for production use, but useful for testing and prototyping.
 *
 * This class uses conventions for storage and reconstruction:
 * - Entities are stored in directories named after their concrete Impl class
 * - For mixin interfaces (anonymous classes), finds the underlying Impl class
 * - Mixin properties (like raisedBy, profile) are stored as UUID references
 * - Reconstruction deserializes the Impl class, then applies mixins via withXxx methods
 *
 * Use [registerPackage] to add packages where entity classes can be found.
 */
class FileMixinTemplate(
    private val baseDir: Path = Path.of(".data"),
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
    private val idExtractor: IdExtractor = AnnotationIdExtractor,
    private val uuidExtractor: UuidExtractor = UuidExtractor { null },
) : MixinTemplate {

    // Registry of packages to search for classes
    private val packageRegistry = mutableListOf<String>()

    // Map of type names to specific packages
    private val typePackages = mutableMapOf<String, String>()

    init {
        baseDir.toFile().mkdirs()
    }

    /**
     * Register a package to search for entity classes.
     * Packages are searched in the order they are registered.
     */
    fun registerPackage(packageName: String) {
        if (packageName !in packageRegistry) {
            packageRegistry.add(packageName)
        }
    }

    /**
     * Register a specific package for a type name.
     */
    fun registerTypePackage(typeName: String, packageName: String) {
        typePackages[typeName] = packageName
    }

    override fun <T> findById(id: Long, mixins: List<Class<out T>>): T? {
        @Suppress("UNCHECKED_CAST")
        return loadEntity(id) as? T
    }

    override fun <T, U : T> findById(id: Long, mixin: Class<U>): U? {
        @Suppress("UNCHECKED_CAST")
        return loadEntity(id, mixin as Class<Any>) as? U
    }

    override fun <T> save(entity: T): T {
        require(entity != null) { "Entity cannot be null" }
        val id = idExtractor.extractId(entity)
            ?: throw IllegalArgumentException("Could not extract ID from entity of type ${entity.javaClass.name}. Ensure it has an @Id property or implements a supported ID interface.")

        val typeName = getStorageTypeName(entity)
        val typeDir = baseDir.resolve(typeName).toFile()
        typeDir.mkdirs()

        val jsonNode = objectMapper.valueToTree<ObjectNode>(entity)
        extractAndSaveReferences(entity, jsonNode)
        jsonNode.put("_type", typeName)

        val file = File(typeDir, "$id.json")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonNode)

        return entity
    }

    /**
     * Get the storage type name for an entity.
     * For anonymous classes (mixins), uses the most specific interface name.
     */
    private fun <T> getStorageTypeName(entity: T): String {
        val clazz = entity!!::class.java

        if (clazz.isAnonymousClass || clazz.simpleName.isNullOrEmpty()) {
            // For anonymous classes, use the first (most specific) interface name
            val primaryInterface = clazz.interfaces.firstOrNull()
            if (primaryInterface != null) {
                return primaryInterface.simpleName
            }
        }

        return clazz.simpleName ?: "Unknown"
    }

    /**
     * Recursively find properties with UUIDs, save them separately, and replace with UUID references.
     */
    private fun <T> extractAndSaveReferences(entity: T, jsonNode: ObjectNode) {
        if (entity == null) return

        for (prop in entity::class.memberProperties) {
            val propName = prop.name
            val propValue = try {
                prop.getter.call(entity)
            } catch (_: Exception) {
                continue
            } ?: continue

            // Check if this property has a UUID (but is not the entity itself)
            if (propValue !== entity) {
                val propUuid = uuidExtractor.extractUuid(propValue)
                if (propUuid != null) {
                    // Save the referenced entity
                    save(propValue)
                    // Replace the embedded object with just the UUID reference
                    jsonNode.put("${propName}Uuid", propUuid.toString())
                    jsonNode.remove(propName)
                }
            }
        }
    }

    override fun <T, U : T> eraseSpecialization(entity: U, mixin: Class<U>): T? {
        @Suppress("UNCHECKED_CAST")
        return save(entity) as? T
    }

    override fun <T> findAll(classType: Class<T>): Iterable<T> {
        val results = mutableListOf<T>()
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory } ?: return emptyList()

        for (typeDir in dataDirs) {
            val storedTypeName = typeDir.name

            if (!isTypeCompatible(storedTypeName, classType)) {
                continue
            }

            typeDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val jsonNode = objectMapper.readTree(file) as ObjectNode
                    val entity = reconstructEntity<T>(jsonNode, storedTypeName, classType)
                    if (entity != null && classType.isInstance(entity)) {
                        results.add(entity)
                    }
                } catch (_: Exception) {
                    // Skip files that can't be deserialized
                }
            }
        }

        return results
    }

    /**
     * Check if a stored type name is compatible with the requested class type.
     * Works with interface names (e.g., RaisableIssue, Issue, Person, PersonWithProfile).
     */
    private fun <T> isTypeCompatible(storedTypeName: String, classType: Class<T>): Boolean {
        val storedClass = tryLoadClass(storedTypeName) ?: return false

        // Direct match: stored as Issue, querying for Issue
        if (classType == storedClass) return true

        // Stored type is assignable to requested type: stored as RaisableIssue, querying for Issue
        if (classType.isAssignableFrom(storedClass)) return true

        // Requested type is assignable to stored type: stored as Issue, querying for RaisableIssue
        // (permissive - we'll filter during reconstruction based on whether mixin refs exist)
        if (storedClass.isAssignableFrom(classType)) return true

        return false
    }

    /**
     * Reconstruct an entity from JSON, applying mixins as needed.
     * Works with both concrete classes and interface names.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> reconstructEntity(jsonNode: ObjectNode, storedTypeName: String, classType: Class<T>): T? {
        jsonNode.remove("_type")

        val storedClass = tryLoadClass(storedTypeName) ?: return null

        // If stored as a concrete class (not interface), deserialize directly
        if (!storedClass.isInterface) {
            resolveReferences(jsonNode, storedClass)
            return objectMapper.treeToValue(jsonNode, storedClass) as? T
        }

        // Stored as interface - check for mixin refs
        val mixinRefs = findMixinReferences(jsonNode, storedClass)

        if (mixinRefs.isEmpty()) {
            // No mixin - find a concrete class for this interface
            val concreteClass = findConcreteClass(storedClass) ?: return null
            resolveReferences(jsonNode, concreteClass)
            return objectMapper.treeToValue(jsonNode, concreteClass) as? T
        }

        // Has mixin refs - find the parent interface and its concrete class
        val parentInterface = storedClass.interfaces.firstOrNull() ?: return null
        val concreteClass = findConcreteClass(parentInterface) ?: return null

        // Resolve references for the concrete class
        resolveReferences(jsonNode, concreteClass)

        // Deserialize the base entity
        var entity: Any = objectMapper.treeToValue(jsonNode, concreteClass) ?: return null

        // Apply mixins via withXxx methods
        for ((propName, uuid) in mixinRefs) {
            entity = applyMixin(entity, propName, uuid) ?: return null
        }

        return entity as? T
    }

    /**
     * Find a concrete class that implements the given interface.
     * If already a concrete class, returns it directly.
     * For interfaces, searches for InterfaceNameImpl.
     */
    private fun findConcreteClass(clazz: Class<*>): Class<*>? {
        if (!clazz.isInterface) return clazz
        val implName = "${clazz.simpleName}Impl"
        return tryLoadClass(implName)
    }

    /**
     * Find UUID references that are mixin properties.
     * These are xxxUuid fields where xxx is not a property of the parent interface.
     */
    private fun findMixinReferences(jsonNode: ObjectNode, storedInterface: Class<*>): List<Pair<String, UUID>> {
        val result = mutableListOf<Pair<String, UUID>>()

        // Get properties from parent interfaces (base properties)
        val baseProps = storedInterface.interfaces
            .flatMap { it.kotlin.memberProperties.map { p -> p.name } }
            .toSet()

        val fieldNames = jsonNode.fieldNames().asSequence().toList()
        for (fieldName in fieldNames) {
            if (fieldName.endsWith("Uuid") && fieldName != "uuid") {
                val propName = fieldName.removeSuffix("Uuid")
                // If this property is not in the parent interfaces, it's a mixin property
                if (propName !in baseProps) {
                    val uuidNode = jsonNode.get(fieldName)
                    if (uuidNode != null && uuidNode.isTextual) {
                        result.add(propName to UUID.fromString(uuidNode.asText()))
                        jsonNode.remove(fieldName)
                    }
                }
            }
        }

        return result
    }

    /**
     * Apply a mixin to an entity by calling its withXxx method.
     */
    private fun applyMixin(entity: Any, propName: String, uuid: UUID): Any? {
        val withMethodName = "with${propName.replaceFirstChar { it.uppercase() }}"
        val withMethod = entity::class.memberFunctions.find { it.name == withMethodName }
            ?: return null

        // Get the parameter type
        val paramType = withMethod.parameters.getOrNull(1)?.type?.classifier as? kotlin.reflect.KClass<*>
            ?: return null

        // Find the mixin value by UUID
        val mixinValue = findByUuid(uuid, paramType.java) ?: return null

        // Call the withXxx method
        return try {
            withMethod.call(entity, mixinValue)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolve UUID references back to full entities.
     */
    internal fun resolveReferences(jsonNode: ObjectNode, targetClass: Class<*>) {
        val fieldNames = jsonNode.fieldNames().asSequence().toList()
        for (fieldName in fieldNames) {
            if (fieldName.endsWith("Uuid") && fieldName != "uuid") {
                val propName = fieldName.removeSuffix("Uuid")
                val uuidNode = jsonNode.get(fieldName)

                if (uuidNode != null && uuidNode.isTextual) {
                    val uuid = UUID.fromString(uuidNode.asText())
                    val kClass = targetClass.kotlin
                    val prop = kClass.memberProperties.find { it.name == propName }
                    if (prop != null) {
                        val classifier = prop.returnType.classifier
                        val propClass = if (classifier is kotlin.reflect.KClass<*>) classifier.java else null

                        if (propClass != null) {
                            val referencedEntity = findByUuid(uuid, propClass)
                            if (referencedEntity != null) {
                                jsonNode.remove(fieldName)
                                jsonNode.set<JsonNode>(propName, objectMapper.valueToTree(referencedEntity))
                            } else {
                                jsonNode.remove(fieldName)
                            }
                        }
                    } else {
                        // Property not in this class - might be a mixin property, leave it
                    }
                }
            }
        }
    }

    /**
     * Find an entity by its UUID across all type directories.
     */
    internal fun findByUuid(uuid: UUID, targetType: Class<*>): Any? {
        val uuidString = uuid.toString()
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory } ?: return null

        for (typeDir in dataDirs) {
            val file = File(typeDir, "$uuidString.json")
            if (!file.exists()) continue

            val concreteClassName = typeDir.name
            val concreteClass = tryLoadClass(concreteClassName) ?: continue

            if (!targetType.isAssignableFrom(concreteClass)) {
                continue
            }

            return try {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                resolveReferences(jsonNode, concreteClass)
                jsonNode.remove("_type")
                objectMapper.treeToValue(jsonNode, concreteClass)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Try to load a class by name, searching registered packages.
     */
    internal fun tryLoadClass(className: String): Class<*>? {
        // Check specific type package first
        typePackages[className]?.let { pkg ->
            try {
                return Class.forName("$pkg.$className")
            } catch (_: ClassNotFoundException) {
                // Continue
            }
        }

        // Try registered packages
        for (pkg in packageRegistry) {
            try {
                return Class.forName("$pkg.$className")
            } catch (_: ClassNotFoundException) {
                // Continue
            }
        }

        return null
    }

    private fun loadEntity(id: Long): Any? {
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory } ?: return null

        for (typeDir in dataDirs) {
            val file = File(typeDir, "$id.json")
            if (file.exists()) {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                jsonNode.remove("_type")
                return objectMapper.treeToValue(jsonNode, Any::class.java)
            }
        }
        return null
    }

    private fun <T : Any> loadEntity(id: Long, type: Class<T>): T? {
        val typeName = type.simpleName
        val typeDir = baseDir.resolve(typeName).toFile()
        val file = File(typeDir, "$id.json")

        if (file.exists()) {
            val jsonNode = objectMapper.readTree(file) as ObjectNode
            jsonNode.remove("_type")
            resolveReferences(jsonNode, type)
            return objectMapper.treeToValue(jsonNode, type)
        }

        val dataDirs = baseDir.toFile().listFiles { f -> f.isDirectory } ?: return null
        for (dir in dataDirs) {
            val altFile = File(dir, "$id.json")
            if (altFile.exists()) {
                val jsonNode = objectMapper.readTree(altFile) as ObjectNode
                jsonNode.remove("_type")
                resolveReferences(jsonNode, type)
                return objectMapper.treeToValue(jsonNode, type)
            }
        }
        return null
    }

    /**
     * Deletes all data in the base directory.
     */
    fun clear() {
        baseDir.toFile().deleteRecursively()
        baseDir.toFile().mkdirs()
    }

    /**
     * Lists all entity IDs stored for a given type as strings.
     */
    fun listIds(typeName: String): List<String> {
        val typeDir = baseDir.resolve(typeName).toFile()
        if (!typeDir.exists()) return emptyList()

        return typeDir.listFiles { file -> file.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
}
