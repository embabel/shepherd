package com.embabel.shepherd.util

import org.testcontainers.containers.Neo4jContainer
import java.nio.file.Paths

class Neo4jTestContainer : Neo4jContainer<Neo4jTestContainer> {
    private constructor(imageName: String) : super(imageName)

    companion object {
        /**
         * Toggle between local Neo4j and TestContainers.
         * Set to true to use local Neo4j (requires Neo4j running on localhost:7687).
         * Set to false to use TestContainers (slower startup, but fully isolated).
         */
        const val USE_LOCAL_NEO4J = false

        private const val LOCAL_NEO4J_URL = "bolt://localhost:7687"
        private const val LOCAL_NEO4J_USERNAME = "neo4j"
        private const val LOCAL_NEO4J_PASSWORD = "brahmsian"

        @JvmField
        val instance: Neo4jTestContainer? = if (USE_LOCAL_NEO4J) null else createTestContainer()

        fun useLocalNeo4j(): Boolean {
            return USE_LOCAL_NEO4J
        }

        fun getBoltUrl(): String {
            return if (USE_LOCAL_NEO4J) {
                LOCAL_NEO4J_URL
            } else {
                instance?.boltUrl ?: throw IllegalStateException("Neo4j test container not initialized")
            }
        }

        fun getUsername(): String {
            return if (USE_LOCAL_NEO4J) {
                LOCAL_NEO4J_USERNAME
            } else {
                "neo4j"
            }
        }

        fun getPassword(): String {
            return if (USE_LOCAL_NEO4J) {
                LOCAL_NEO4J_PASSWORD
            } else {
                instance?.adminPassword ?: throw IllegalStateException("Neo4j test container not initialized")
            }
        }

        private fun createTestContainer(): Neo4jTestContainer {
            val container = Neo4jTestContainer("neo4j:5.26.1-community")
                .withNeo4jConfig("dbms.logs.query.enabled", "INFO")
                .withNeo4jConfig("dbms.logs.query.parameter_logging_enabled", "true")
                .withAdminPassword("testpassword")
                // Install APOC and APOC Extended automatically
                .withEnv("NEO4J_PLUGINS", "[\"apoc\", \"apoc-extended\"]")
                .withNeo4jConfig("dbms.security.procedures.unrestricted", "apoc.*")
                .withNeo4jConfig("dbms.security.procedures.allowlist", "apoc.*")

            container.start()
            return container
        }

        private fun findApocJar(): String? {
            // Try different common APOC locations
            val possiblePaths = listOf(
                Paths.get(
                    System.getProperty("user.home"),
                    ".m2", "repository", "org", "neo4j", "procedure", "apoc",
                    "5.26.1", "apoc-5.26.1.jar"
                ),
                Paths.get(
                    System.getProperty("user.home"),
                    ".m2", "repository", "org", "neo4j", "procedure", "apoc-core",
                    "5.26.1", "apoc-core-5.26.1.jar"
                )
            )

            return possiblePaths
                .map { it.toString() }
                .firstOrNull { Paths.get(it).toFile().exists() }
        }

        init {
            // Container is initialized lazily in the instance property
        }
    }
}

