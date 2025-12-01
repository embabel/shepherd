package org.drivine.query

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.time.Instant
import java.util.*
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
 * This implementation uses a simpler approach:
 * - Serialization: Stores all properties from the entity + metadata about interfaces
 * - Deserialization: Uses JDK dynamic proxies to implement interfaces from stored data
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

    @Suppress("UNCHECKED_CAST")
    override fun <T> findById(id: String, clazz: Class<T>, mixins: List<Class<out T>>): T? {
        return loadEntityById(id, clazz as Class<Any>) as? T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T, U : T> findById(id: String, clazz: Class<T>, mixin: Class<U>): U? {
        return loadEntityById(id, mixin as Class<Any>) as? U
    }

    override fun <T> save(entity: T): T {
        require(entity != null) { "Entity cannot be null" }
        val id = idExtractor.extractId(entity)
            ?: throw IllegalArgumentException("Could not extract ID from entity of type ${entity.javaClass.name}. Ensure it has an @Id property or implements a supported ID interface.")

        val typeName = getStorageTypeName(entity)

        // Collect all interfaces this entity implements (excluding common ones)
        val interfaces = collectInterfaces(entity)

        val jsonNode: ObjectNode
        if (shouldTreatAsConcreteClass(entity)) {
            // Concrete class: use Jackson's default serialization
            jsonNode = objectMapper.valueToTree(entity)
            jsonNode.put("_type", typeName)
            jsonNode.put("_concreteClass", entity::class.java.name)
            // Also store labels for type querying
            val labelsArray = jsonNode.putArray("_labels")
            labelsArray.add(typeName)
            interfaces.forEach { labelsArray.add(it.simpleName) }
        } else {
            // Interface-based: extract properties manually
            jsonNode = objectMapper.createObjectNode()
            val interfaceNames = interfaces.map { it.name }

            // Store metadata
            jsonNode.put("_type", typeName)
            val interfacesArray = jsonNode.putArray("_interfaces")
            interfaceNames.forEach { interfacesArray.add(it) }

            // Store labels (simple names) for type querying
            val labelsArray = jsonNode.putArray("_labels")
            interfaces.forEach { labelsArray.add(it.simpleName) }

            // Extract all properties from the entity via reflection
            extractAllProperties(entity, jsonNode, interfaces)
        }

        // Store in a flat structure - all entities go in "entities" directory
        val entitiesDir = baseDir.resolve("entities").toFile()
        entitiesDir.mkdirs()
        val file = File(entitiesDir, "$id.json")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonNode)

        // Also store in type-specific directory for backwards compatibility with listIds
        val typeDir = baseDir.resolve(typeName).toFile()
        typeDir.mkdirs()
        val typeFile = File(typeDir, "$id.json")
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(typeFile, jsonNode)

        return entity
    }

    /**
     * Collect all relevant interfaces implemented by the entity.
     * Returns empty list for concrete classes that don't implement domain interfaces.
     */
    private fun <T> collectInterfaces(entity: T): List<Class<*>> {
        val result = mutableListOf<Class<*>>()
        val visited = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()

        // Start with interfaces from the entity's class
        entity!!::class.java.interfaces.forEach { queue.add(it) }

        while (queue.isNotEmpty()) {
            val iface = queue.poll()
            if (iface in visited) continue
            visited.add(iface)

            // Skip common JDK interfaces
            if (iface.name.startsWith("java.") || iface.name.startsWith("kotlin.")) continue

            result.add(iface)

            // Add parent interfaces
            iface.interfaces.forEach { queue.add(it) }
        }

        return result
    }

    /**
     * Determine if an entity should be serialized as a concrete class.
     * Data classes with only marker interfaces (like HasUUID) are treated as concrete.
     * Anonymous classes (created via delegation) are treated as interface-based.
     */
    private fun <T> shouldTreatAsConcreteClass(entity: T): Boolean {
        val clazz = entity!!::class.java

        // Anonymous classes are interface-based (delegation pattern)
        if (clazz.isAnonymousClass || clazz.simpleName.isNullOrEmpty()) {
            return false
        }

        // Data classes with only simple marker interfaces are concrete
        val interfaces = collectInterfaces(entity)
        if (interfaces.isEmpty()) return true

        // If the only interfaces are marker interfaces (have only val properties, no methods)
        // and the class is a data class, treat as concrete
        if (clazz.kotlin.isData) {
            // Check if interfaces define any properties beyond 'uuid' (marker interface)
            val interfaceProps = interfaces.flatMap { iface ->
                iface.kotlin.memberProperties.map { it.name }
            }.toSet()

            // If interfaces only define 'uuid', treat as concrete class
            return interfaceProps == setOf("uuid")
        }

        return false
    }

    /**
     * Extract all properties from the entity, including from all interfaces.
     * Referenced entities with UUIDs are stored as UUID references.
     */
    private fun <T> extractAllProperties(entity: T, jsonNode: ObjectNode, interfaces: List<Class<*>>) {
        val processedProps = mutableSetOf<String>()

        // Get all property names from all interfaces
        val allProps = interfaces.flatMap { iface ->
            iface.kotlin.memberProperties.map { it.name }
        }.toSet()

        for (prop in entity!!::class.memberProperties) {
            val propName = prop.name
            if (propName in processedProps) continue
            if (propName !in allProps) continue // Only store interface properties
            processedProps.add(propName)

            val propValue = try {
                prop.getter.call(entity)
            } catch (_: Exception) {
                continue
            }

            if (propValue == null) {
                jsonNode.putNull(propName)
                continue
            }

            // Check if this property value is a separately-managed entity reference.
            // Only treat as reference if:
            // 1. The property value has a UUID
            // 2. The property TYPE is an interface (not a concrete data class)
            // This ensures Profile (a data class) is stored inline, while Person (an interface) is stored as reference
            if (propValue !== entity) {
                val propType = prop.returnType.classifier as? kotlin.reflect.KClass<*>
                val isInterfaceType = propType?.java?.isInterface == true

                if (isInterfaceType) {
                    val propUuid = uuidExtractor.extractUuid(propValue)
                    if (propUuid != null) {
                        // Save the referenced entity
                        save(propValue)
                        // Store as UUID reference
                        jsonNode.put("${propName}_ref", propUuid.toString())
                        continue
                    }
                }
            }

            // Store the value directly
            jsonNode.set<JsonNode>(propName, objectMapper.valueToTree(propValue))
        }
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

    override fun <T, U : T> eraseSpecialization(entity: U, mixin: Class<U>): T? {
        @Suppress("UNCHECKED_CAST")
        return save(entity) as? T
    }

    override fun <T> findAll(classType: Class<T>): Iterable<T> {
        val results = mutableListOf<T>()
        val seenIds = mutableSetOf<String>()
        val targetLabel = classType.simpleName

        // First, search the flat entities directory
        val entitiesDir = baseDir.resolve("entities").toFile()
        if (entitiesDir.exists()) {
            entitiesDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val jsonNode = objectMapper.readTree(file) as ObjectNode

                    // Check if this entity has the target label
                    val labels = (jsonNode.get("_labels") as? ArrayNode)
                        ?.map { it.asText() }?.toSet() ?: emptySet()
                    val storedType = jsonNode.get("_type")?.asText()
                    val concreteClass = jsonNode.get("_concreteClass")?.asText()

                    // Match if: target label is in labels, stored type matches, concrete class matches, or asking for Any
                    val matches = targetLabel in labels ||
                            storedType == targetLabel ||
                            concreteClass?.endsWith(".$targetLabel") == true ||
                            concreteClass == targetLabel ||
                            classType == Any::class.java

                    if (matches) {
                        val entity = reconstructEntity<T>(jsonNode, classType)
                        if (entity != null && classType.isInstance(entity)) {
                            results.add(entity)
                            seenIds.add(file.nameWithoutExtension)
                        }
                    }
                } catch (_: Exception) {
                    // Skip files that can't be deserialized
                }
            }
        }

        // Fall back to type directories for backwards compatibility
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory && file.name != "entities" } ?: return results

        for (typeDir in dataDirs) {
            typeDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                if (file.nameWithoutExtension in seenIds) return@forEach // Already processed

                try {
                    val jsonNode = objectMapper.readTree(file) as ObjectNode
                    val entity = reconstructEntity<T>(jsonNode, classType)
                    if (entity != null && classType.isInstance(entity)) {
                        results.add(entity)
                        seenIds.add(file.nameWithoutExtension)
                    }
                } catch (_: Exception) {
                    // Skip files that can't be deserialized
                }
            }
        }

        return results
    }

    /**
     * Reconstruct an entity from JSON.
     * Uses dynamic proxy for interface-based entities, Jackson for concrete classes.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> reconstructEntity(jsonNode: ObjectNode, targetType: Class<T>): T? {
        // Check if this is a concrete class
        val concreteClassName = jsonNode.get("_concreteClass")?.asText()
        if (concreteClassName != null) {
            return reconstructConcreteClass(jsonNode, targetType, concreteClassName)
        }

        // Get the interfaces this entity implements
        val interfacesNode = jsonNode.get("_interfaces") as? ArrayNode ?: return null
        val interfaces = interfacesNode.mapNotNull { node ->
            try {
                Class.forName(node.asText())
            } catch (_: ClassNotFoundException) {
                null
            }
        }

        if (interfaces.isEmpty()) return null

        // Check if targetType is compatible with stored interfaces
        // The proxy must be able to be cast to targetType, which means:
        // - One of the stored interfaces must be assignable to targetType, OR
        // - targetType must be assignable from one of the stored interfaces
        val isCompatible = interfaces.any { targetType.isAssignableFrom(it) }
        if (!isCompatible) return null

        // Build the property map from JSON
        val properties = buildPropertyMap(jsonNode, interfaces)

        // Create dynamic proxy
        val handler = InterfaceProxyHandler(properties, interfaces)
        val proxy = Proxy.newProxyInstance(
            this.javaClass.classLoader,
            interfaces.toTypedArray(),
            handler
        )

        return proxy as T
    }

    /**
     * Reconstruct a concrete class from JSON using Jackson.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> reconstructConcreteClass(jsonNode: ObjectNode, targetType: Class<T>, concreteClassName: String): T? {
        val concreteClass = try {
            Class.forName(concreteClassName)
        } catch (_: ClassNotFoundException) {
            return null
        }

        // Check type compatibility
        if (!targetType.isAssignableFrom(concreteClass) && targetType != Any::class.java) {
            return null
        }

        // Remove metadata fields before deserialization
        jsonNode.remove("_type")
        jsonNode.remove("_concreteClass")
        jsonNode.remove("_labels")

        return try {
            objectMapper.treeToValue(jsonNode, concreteClass) as? T
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Build a map of property names to values from the JSON node.
     * Resolves UUID references to actual entities.
     */
    private fun buildPropertyMap(jsonNode: ObjectNode, interfaces: List<Class<*>>): Map<String, Any?> {
        val properties = mutableMapOf<String, Any?>()

        // Get property types from interfaces
        val propTypes = mutableMapOf<String, Class<*>>()
        for (iface in interfaces) {
            for (method in iface.methods) {
                val methodName = method.name
                if (methodName.startsWith("get") && method.parameterCount == 0) {
                    val propName = methodName.removePrefix("get").replaceFirstChar { it.lowercase() }
                    propTypes[propName] = method.returnType
                }
            }
            // Also check Kotlin property accessors
            for (prop in iface.kotlin.memberProperties) {
                propTypes[prop.name] = (prop.returnType.classifier as? kotlin.reflect.KClass<*>)?.java
                    ?: continue
            }
        }

        val fieldNames = jsonNode.fieldNames().asSequence().toList()
        for (fieldName in fieldNames) {
            // Skip metadata fields
            if (fieldName.startsWith("_")) continue

            // Handle UUID references
            if (fieldName.endsWith("_ref")) {
                val propName = fieldName.removeSuffix("_ref")
                val uuidStr = jsonNode.get(fieldName)?.asText() ?: continue
                val uuid = UUID.fromString(uuidStr)
                val propType = propTypes[propName] ?: continue
                val referencedEntity = findByUuid(uuid, propType)
                properties[propName] = referencedEntity
                continue
            }

            val node = jsonNode.get(fieldName) ?: continue
            val propType = propTypes[fieldName]

            val value = deserializeValue(node, propType)
            properties[fieldName] = value
        }

        return properties
    }

    /**
     * Deserialize a JSON node to the appropriate type.
     */
    private fun deserializeValue(node: JsonNode, targetType: Class<*>?): Any? {
        if (node.isNull) return null

        return when {
            targetType == null -> objectMapper.treeToValue(node, Any::class.java)
            targetType == UUID::class.java -> UUID.fromString(node.asText())
            targetType == String::class.java -> node.asText()
            targetType == Int::class.java || targetType == java.lang.Integer::class.java -> node.asInt()
            targetType == Long::class.java || targetType == java.lang.Long::class.java -> node.asLong()
            targetType == Double::class.java || targetType == java.lang.Double::class.java -> node.asDouble()
            targetType == Float::class.java || targetType == java.lang.Float::class.java -> node.floatValue()
            targetType == Boolean::class.java || targetType == java.lang.Boolean::class.java -> node.asBoolean()
            targetType == Instant::class.java -> Instant.parse(node.asText())
            Set::class.java.isAssignableFrom(targetType) -> {
                (node as? ArrayNode)?.map { it.asText() }?.toSet() ?: emptySet<String>()
            }
            List::class.java.isAssignableFrom(targetType) -> {
                (node as? ArrayNode)?.map { it.asText() } ?: emptyList<String>()
            }
            targetType.isEnum -> {
                @Suppress("UNCHECKED_CAST")
                java.lang.Enum.valueOf(targetType as Class<out Enum<*>>, node.asText())
            }
            else -> objectMapper.treeToValue(node, targetType)
        }
    }

    /**
     * Find an entity by its UUID.
     * Uses the flat "entities" directory which contains the latest version of each entity.
     */
    internal fun findByUuid(uuid: UUID, targetType: Class<*>): Any? {
        val uuidString = uuid.toString()

        // Look in the flat entities directory first - this has the most complete data
        val entitiesDir = baseDir.resolve("entities").toFile()
        if (entitiesDir.exists()) {
            val file = File(entitiesDir, "$uuidString.json")
            if (file.exists()) {
                try {
                    val jsonNode = objectMapper.readTree(file) as ObjectNode
                    val result = reconstructEntity(jsonNode, targetType)
                    if (result != null) return result
                } catch (_: Exception) {
                    // Continue to search other directories
                }
            }
        }

        // Fall back to searching type directories (for backwards compatibility)
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory && file.name != "entities" } ?: return null
        for (typeDir in dataDirs) {
            val file = File(typeDir, "$uuidString.json")
            if (!file.exists()) continue

            try {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                val result = reconstructEntity(jsonNode, targetType)
                if (result != null) return result
            } catch (_: Exception) {
                // Continue to next directory
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

    private fun <T : Any> loadEntityById(id: String, type: Class<T>): T? {
        // Look in the flat entities directory first - this has the most complete data
        val entitiesDir = baseDir.resolve("entities").toFile()
        if (entitiesDir.exists()) {
            val file = File(entitiesDir, "$id.json")
            if (file.exists()) {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                val result = reconstructEntity(jsonNode, type)
                if (result != null) return result
            }
        }

        // Fall back to searching type directories
        val dataDirs = baseDir.toFile().listFiles { file -> file.isDirectory && file.name != "entities" } ?: return null
        for (typeDir in dataDirs) {
            val file = File(typeDir, "$id.json")
            if (file.exists()) {
                val jsonNode = objectMapper.readTree(file) as ObjectNode
                val result = reconstructEntity(jsonNode, type)
                if (result != null) return result
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
        val results = mutableListOf<String>()

        // First check direct type directory
        val typeDir = baseDir.resolve(typeName).toFile()
        if (typeDir.exists()) {
            typeDir.listFiles { file -> file.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?.let { results.addAll(it) }
        }

        // Also search entities directory by label for interface types
        val entitiesDir = baseDir.resolve("entities").toFile()
        if (entitiesDir.exists()) {
            entitiesDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val jsonNode = objectMapper.readTree(file) as ObjectNode
                    val labels = (jsonNode.get("_labels") as? ArrayNode)
                        ?.map { it.asText() }?.toSet() ?: emptySet()
                    if (typeName in labels && file.nameWithoutExtension !in results) {
                        results.add(file.nameWithoutExtension)
                    }
                } catch (_: Exception) {
                    // Skip files that can't be parsed
                }
            }
        }

        return results
    }
}

/**
 * InvocationHandler for dynamic proxies that implement domain interfaces.
 * Returns values from the property map for getter methods.
 */
private class InterfaceProxyHandler(
    private val properties: Map<String, Any?>,
    private val interfaces: List<Class<*>>
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val methodName = method.name

        // Handle Object methods
        when (methodName) {
            "hashCode" -> return properties.hashCode()
            "toString" -> return "Proxy[${interfaces.map { it.simpleName }}]$properties"
            "equals" -> {
                val other = args?.firstOrNull() ?: return false
                if (!Proxy.isProxyClass(other.javaClass)) return false
                val otherHandler = Proxy.getInvocationHandler(other)
                if (otherHandler !is InterfaceProxyHandler) return false
                return properties == otherHandler.properties
            }
        }

        // Handle Kotlin property getters (getXxx or just propertyName for Kotlin interfaces)
        val propName = when {
            methodName.startsWith("get") && methodName.length > 3 -> {
                methodName.removePrefix("get").replaceFirstChar { it.lowercase() }
            }
            methodName.startsWith("is") && methodName.length > 2 && method.returnType == Boolean::class.java -> {
                methodName.removePrefix("is").replaceFirstChar { it.lowercase() }
            }
            else -> methodName
        }

        return properties[propName]
    }
}
