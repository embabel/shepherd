package org.drivine.query

import com.embabel.shepherd.domain.HasUUID
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.nio.file.Path
import kotlin.reflect.full.isSuperclassOf

/**
 * File-based implementation of MixinTemplate that serializes entities to JSON files.
 * Entities are stored in a .data directory, organized by type.
 * Not intended for production use, but useful for testing and prototyping.
 */
class FileMixinTemplate(
    private val baseDir: Path = Path.of(".data"),
    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()),
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

        val file = File(typeDir, "$id.json")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, entity)

        return entity
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
                    @Suppress("UNCHECKED_CAST")
                    val entity = objectMapper.readValue(file, concreteClass) as? T
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
