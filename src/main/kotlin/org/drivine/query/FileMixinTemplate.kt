package org.drivine.query

import com.embabel.shepherd.domain.HasUUID
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

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

        val typeName = entity::class.simpleName ?: "Unknown"
        val typeDir = baseDir.resolve(typeName).toFile()
        typeDir.mkdirs()

        // Convert to JSON tree so we can manipulate it
        val jsonNode = objectMapper.valueToTree<ObjectNode>(entity)

        // Find and extract HasUUID properties, save them separately, replace with references
        extractAndSaveReferences(entity, jsonNode)

        val file = File(typeDir, "$id.json")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonNode)

        return entity
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
                val propType = prop.returnType.javaType
                // Only extract if it's a concrete HasUUID type (not UUID itself)
                if (propType is Class<*> && HasUUID::class.java.isAssignableFrom(propType) && propType != UUID::class.java) {
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
            // The directory name is the concrete class name
            val concreteClassName = typeDir.name

            // Try to load the concrete class
            val concreteClass = try {
                Class.forName("${getPackageForType(concreteClassName)}.$concreteClassName")
            } catch (e: ClassNotFoundException) {
                // Try common packages
                tryLoadClass(concreteClassName)
            } ?: continue

            // Check if the concrete class is assignable to the requested type
            if (!classType.isAssignableFrom(concreteClass)) {
                continue
            }

            // Load all files from this directory
            typeDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    // Read as JSON tree first so we can resolve references
                    val jsonNode = objectMapper.readTree(file) as ObjectNode
                    resolveReferences(jsonNode, concreteClass)

                    @Suppress("UNCHECKED_CAST")
                    val entity = objectMapper.treeToValue(jsonNode, concreteClass) as? T
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
     * Resolve UUID references back to full entities.
     * Looks for properties ending in "Uuid" and replaces them with the full entity.
     */
    private fun resolveReferences(jsonNode: ObjectNode, targetClass: Class<*>) {
        // Find all properties in the target class that are HasUUID types
        val kClass = targetClass.kotlin
        for (prop in kClass.memberProperties) {
            val propName = prop.name
            val propType = prop.returnType.javaType

            // Check if this is a HasUUID property type
            if (propType is Class<*> && HasUUID::class.java.isAssignableFrom(propType) && propType != UUID::class.java) {
                val uuidFieldName = "${propName}Uuid"
                val uuidNode = jsonNode.get(uuidFieldName)

                if (uuidNode != null && uuidNode.isTextual) {
                    val uuid = UUID.fromString(uuidNode.asText())
                    // Find the referenced entity
                    val referencedEntity = findByUuid(uuid, propType)
                    if (referencedEntity != null) {
                        // Replace the UUID reference with the full entity
                        jsonNode.remove(uuidFieldName)
                        jsonNode.set<JsonNode>(propName, objectMapper.valueToTree(referencedEntity))
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
            } ?: continue

            // Check if compatible with target type
            if (!targetType.isAssignableFrom(concreteClass)) {
                continue
            }

            return try {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                // Recursively resolve any nested references
                resolveReferences(jsonNode, concreteClass)
                objectMapper.treeToValue(jsonNode, concreteClass)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun getPackageForType(typeName: String): String {
        // Return the package based on known types - this is a simple heuristic
        return typePackages[typeName] ?: "com.embabel.shepherd.domain"
    }

    private fun tryLoadClass(className: String): Class<*>? {
        val packagesToTry = listOf(
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
                return objectMapper.readValue(file, Any::class.java)
            }
        }
        return null
    }

    private fun <T : Any> loadEntity(id: Long, type: Class<T>): T? {
        val typeName = type.simpleName
        val typeDir = baseDir.resolve(typeName).toFile()
        val file = File(typeDir, "$id.json")

        if (file.exists()) {
            return objectMapper.readValue(file, type)
        }

        // Also search other directories in case the concrete type differs
        val dataDirs = baseDir.toFile().listFiles { f -> f.isDirectory } ?: return null
        for (dir in dataDirs) {
            val altFile = File(dir, "$id.json")
            if (altFile.exists()) {
                return objectMapper.readValue(altFile, type)
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
