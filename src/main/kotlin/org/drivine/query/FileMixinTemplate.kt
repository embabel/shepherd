package org.drivine.query

import com.embabel.shepherd.community.domain.HasUUID
import com.embabel.shepherd.community.domain.Issue
import com.embabel.shepherd.community.domain.Person
import com.embabel.shepherd.community.domain.RaisableIssue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.reflect.full.memberProperties

/**
 * File-based implementation of MixinTemplate that serializes entities to JSON files.
 * Entities are stored in a .data directory, organized by type.
 * Not intended for production use, but useful for testing and prototyping.
 */
class FileMixinTemplate(
    private val baseDir: Path = Path.of(".data"),
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
    private val idExtractor: IdExtractor = CompositeIdExtractor(
        AnnotationIdExtractor,
        InterfaceIdExtractor(HasUUID::class.java) { it.uuid })
) : MixinTemplate {

    init {
        baseDir.toFile().mkdirs()
    }

    override fun <T> findById(id: Long, mixins: List<Class<out T>>): T? {
        // For file-based storage, mixins don't change how we load - we load the full entity
        // The caller would need to cast to the appropriate mixin interface
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

        // Determine the storage type name - use interface name for anonymous classes
        val typeName = getStorageTypeName(entity)
        val typeDir = baseDir.resolve(typeName).toFile()
        typeDir.mkdirs()

        // Convert to JSON tree so we can manipulate it
        val jsonNode = objectMapper.valueToTree<ObjectNode>(entity)

        // Find and extract HasUUID properties, save them separately, replace with references
        extractAndSaveReferences(entity, jsonNode)

        // Store type metadata for reconstruction
        jsonNode.put("_type", typeName)

        val file = File(typeDir, "$id.json")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonNode)

        return entity
    }

    /**
     * Get the storage type name for an entity. For anonymous classes, use the most specific interface.
     */
    private fun <T> getStorageTypeName(entity: T): String {
        val clazz = entity!!::class.java
        // If it's an anonymous class or has no simple name, find the most specific interface
        if (clazz.isAnonymousClass || clazz.simpleName.isNullOrEmpty()) {
            // Look for known interfaces in order of specificity
            return when {
                entity is RaisableIssue -> "RaisableIssue"
                entity is Issue -> "Issue"
                entity is HasUUID -> "HasUUID"
                else -> clazz.simpleName ?: "Unknown"
            }
        }
        return clazz.simpleName ?: "Unknown"
    }

    /**
     * Recursively find HasUUID properties, save them separately, and replace with UUID references.
     */
    private fun <T> extractAndSaveReferences(entity: T, jsonNode: ObjectNode) {
        if (entity == null) return

        for (prop in entity::class.memberProperties) {
            val propName = prop.name
            val propValue = try {
                prop.getter.call(entity)
            } catch (e: Exception) {
                continue
            } ?: continue

            // Check if this property is a HasUUID (but not the entity itself)
            if (propValue is HasUUID && propValue !== entity) {
                // Get the raw class, handling nullable types by getting the classifier
                val classifier = prop.returnType.classifier
                val propClass = if (classifier is kotlin.reflect.KClass<*>) classifier.java else null

                // Only extract if it's a concrete HasUUID type (not UUID itself)
                if (propClass != null && HasUUID::class.java.isAssignableFrom(propClass) && propClass != UUID::class.java) {
                    // Save the referenced entity
                    save(propValue)
                    // Replace the embedded object with just the UUID reference
                    jsonNode.put("${propName}Uuid", propValue.uuid.toString())
                    jsonNode.remove(propName)
                }
            }
        }
    }

    override fun <T, U : T> eraseSpecialization(entity: U, mixin: Class<U>): T? {
        // For file storage, we just re-save the entity without the mixin properties
        // This is a simplified implementation
        @Suppress("UNCHECKED_CAST")
        return save(entity) as? T
    }

    override fun <T> findAll(classType: Class<T>): Iterable<T> {
        val results = mutableListOf<T>()
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory } ?: return emptyList()

        for (typeDir in dataDirs) {
            val storedTypeName = typeDir.name

            // Check if this directory's type is compatible with requested type
            if (!isTypeCompatible(storedTypeName, classType)) {
                continue
            }

            // Load all files from this directory
            typeDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val jsonNode = objectMapper.readTree(file) as ObjectNode
                    val entity = reconstructEntity<T>(jsonNode, storedTypeName, classType)
                    if (entity != null) {
                        results.add(entity)
                    }
                } catch (e: Exception) {
                    // Skip files that can't be deserialized
                }
            }
        }

        return results
    }

    /**
     * Check if a stored type name is compatible with the requested class type.
     */
    private fun <T> isTypeCompatible(storedTypeName: String, classType: Class<T>): Boolean {
        // Handle interface-based stored types
        return when (storedTypeName) {
            "RaisableIssue" -> RaisableIssue::class.java.isAssignableFrom(classType) ||
                    classType.isAssignableFrom(RaisableIssue::class.java)

            "Issue" -> Issue::class.java.isAssignableFrom(classType) ||
                    classType.isAssignableFrom(Issue::class.java)

            else -> {
                // Try to load the concrete class
                val concreteClass = try {
                    Class.forName("${getPackageForType(storedTypeName)}.$storedTypeName")
                } catch (e: ClassNotFoundException) {
                    tryLoadClass(storedTypeName)
                } ?: return false
                classType.isAssignableFrom(concreteClass)
            }
        }
    }

    /**
     * Reconstruct an entity from JSON, handling delegation-based types.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> reconstructEntity(jsonNode: ObjectNode, storedTypeName: String, classType: Class<T>): T? {
        // Remove _type field before deserialization
        jsonNode.remove("_type")

        return when (storedTypeName) {
            "RaisableIssue" -> reconstructRaisableIssue(jsonNode) as? T
            else -> {
                val concreteClass = try {
                    Class.forName("${getPackageForType(storedTypeName)}.$storedTypeName")
                } catch (e: ClassNotFoundException) {
                    tryLoadClass(storedTypeName)
                } ?: return null

                resolveReferences(jsonNode, concreteClass)
                objectMapper.treeToValue(jsonNode, concreteClass) as? T
            }
        }
    }

    /**
     * Reconstruct a RaisableIssue by loading the base Issue and Person, then using delegation.
     */
    private fun reconstructRaisableIssue(jsonNode: ObjectNode): RaisableIssue? {
        // Get the person UUID reference
        val personUuidNode = jsonNode.get("raisedByUuid") ?: return null
        val personUuid = UUID.fromString(personUuidNode.asText())

        // Find the person
        val person = findByUuid(personUuid, Person::class.java) as? Person ?: return null

        // Remove the person reference field
        jsonNode.remove("raisedByUuid")

        // Load the base issue - we need to find the concrete Issue class
        // The JSON contains all Issue fields, so we can deserialize to IssueImpl
        val issueClass = tryLoadClass("IssueImpl") ?: return null
        val issue = objectMapper.treeToValue(jsonNode, issueClass) as? Issue ?: return null

        // Reconstruct using delegation
        return issue.withRaisedBy(person)
    }

    /**
     * Resolve UUID references back to full entities.
     * Looks for fields ending in "Uuid" in the JSON and replaces them with the full entity.
     */
    private fun resolveReferences(jsonNode: ObjectNode, targetClass: Class<*>) {
        // Find all fields in the JSON that end with "Uuid" and try to resolve them
        val fieldNames = jsonNode.fieldNames().asSequence().toList()
        for (fieldName in fieldNames) {
            if (fieldName.endsWith("Uuid") && fieldName != "uuid") {
                val propName = fieldName.removeSuffix("Uuid")
                val uuidNode = jsonNode.get(fieldName)

                if (uuidNode != null && uuidNode.isTextual) {
                    val uuid = UUID.fromString(uuidNode.asText())
                    // Find the property type from the target class
                    val kClass = targetClass.kotlin
                    val prop = kClass.memberProperties.find { it.name == propName }
                    if (prop != null) {
                        val classifier = prop.returnType.classifier
                        val propClass = if (classifier is kotlin.reflect.KClass<*>) classifier.java else null

                        if (propClass != null && HasUUID::class.java.isAssignableFrom(propClass)) {
                            // Find the referenced entity
                            val referencedEntity = findByUuid(uuid, propClass)
                            if (referencedEntity != null) {
                                // Replace the UUID reference with the full entity
                                jsonNode.remove(fieldName)
                                jsonNode.set<JsonNode>(propName, objectMapper.valueToTree(referencedEntity))
                            } else {
                                // If we can't find the entity, remove the UUID field for nullable properties
                                jsonNode.remove(fieldName)
                            }
                        }
                    } else {
                        // Property not found in class, just remove the UUID field
                        jsonNode.remove(fieldName)
                    }
                }
            }
        }
    }

    /**
     * Find an entity by its UUID across all type directories.
     */
    private fun findByUuid(uuid: UUID, targetType: Class<*>): Any? {
        val uuidString = uuid.toString()
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory } ?: return null

        for (typeDir in dataDirs) {
            val file = File(typeDir, "$uuidString.json")
            if (!file.exists()) continue

            val concreteClassName = typeDir.name
            val concreteClass = try {
                Class.forName("${getPackageForType(concreteClassName)}.$concreteClassName")
            } catch (e: ClassNotFoundException) {
                tryLoadClass(concreteClassName)
            }

            if (concreteClass == null) {
                continue
            }

            // Check if compatible with target type
            if (!targetType.isAssignableFrom(concreteClass)) {
                continue
            }

            return try {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                // Recursively resolve any nested references
                resolveReferences(jsonNode, concreteClass)
                // Remove the _type field before deserialization
                jsonNode.remove("_type")
                objectMapper.treeToValue(jsonNode, concreteClass)
            } catch (e: Exception) {
                // Skip files that can't be deserialized
                null
            }
        }
        return null
    }

    private fun getPackageForType(typeName: String): String {
        // Return the package based on known types - this is a simple heuristic
        return typePackages[typeName] ?: "com.embabel.shepherd.community.domain"
    }

    private fun tryLoadClass(className: String): Class<*>? {
        val packagesToTry = listOf(
            "com.embabel.shepherd.community.domain",
            "com.embabel.shepherd.domain",
            "org.drivine.query"
        )
        for (pkg in packagesToTry) {
            try {
                return Class.forName("$pkg.$className")
            } catch (e: ClassNotFoundException) {
                // Continue trying
            }
        }
        return null
    }

    private val typePackages = mutableMapOf<String, String>()

    /**
     * Register a package for a type name so findAll can discover it.
     */
    fun registerTypePackage(typeName: String, packageName: String) {
        typePackages[typeName] = packageName
    }

    private fun loadEntity(id: Long): Any? {
        // Search all type directories for an entity with this ID
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

        // Also search other directories in case the concrete type differs
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
