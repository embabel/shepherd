package com.embabel.shepherd.conf

import com.embabel.shepherd.domain.HasUUID
import org.drivine.connection.ConnectionProperties
import org.drivine.connection.DataSourceMap
import org.drivine.connection.DatabaseType
import org.drivine.manager.PersistenceManager
import org.drivine.manager.PersistenceManagerFactory
import org.drivine.query.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import java.util.Map

@Configuration
@ComponentScan(basePackages = ["org.drivine"])
class DrivineConfiguration2 {

    @Value("\${spring.neo4j.uri:bolt://localhost:7687}")
    private val neo4jUri: String? = null

    @Value("\${spring.neo4j.authentication.username:neo4j}")
    private val username: String? = null

    @Value("\${spring.neo4j.authentication.password:brahmsian}")
    private val password: String? = null

    @Value("\${spring.data.neo4j.database:neo4j}")
    private val database: String? = null

//    @Bean
//    fun databaseRegistry(dataSourceMap: DataSourceMap): DatabaseRegistry {
//        return DatabaseRegistry(dataSourceMap)
//    }
//
//    @Bean
//    fun transactionContextHolder(): TransactionContextHolder {
//        return TransactionContextHolder()
//    }
//
//    @Bean
//    fun transactionManager(contextHolder: TransactionContextHolder): PlatformTransactionManager {
//        return DrivineTransactionManager(contextHolder)
//    }

//    @Bean
//    fun factory(databaseRegistry: DatabaseRegistry): PersistenceManagerFactory {
//        return PersistenceManagerFactory(databaseRegistry, transactionContextHolder())
//    }

    @Bean
    fun mixinTemplate(): MixinTemplate {
        // Configure ID extractor: try HasUUID first, then @Id annotation
        val uuidIdExtractor = InterfaceIdExtractor(HasUUID::class.java) { it.uuid }
        val compositeIdExtractor = CompositeIdExtractor(uuidIdExtractor, AnnotationIdExtractor)

        // Configure UUID extractor for reference properties
        val uuidExtractor = UuidExtractor { entity ->
            (entity as? HasUUID)?.uuid
        }

        return FileMixinTemplate(
            idExtractor = compositeIdExtractor,
            uuidExtractor = uuidExtractor,
        ).apply {
            // Register packages where domain classes live
            registerPackage("com.embabel.shepherd.community.domain")
            registerPackage("com.embabel.shepherd.proprietary.domain")
        }
    }

    @Bean
//    @Profile("local & !test")
    fun dataSourceMap(): DataSourceMap {
        // Parse the URI to extract host and port
        var host = "localhost"
        var port = 7687

        if (neo4jUri!!.startsWith("bolt://")) {
            val hostPort = neo4jUri.substring(7)
            val parts = hostPort.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size > 0) {
                host = parts[0]
            }
            if (parts.size > 1) {
                port = parts[1].toInt()
            }
        }

        val props = ConnectionProperties(
            DatabaseType.NEO4J,
            host,
            port,
            username,
            password,
            null,
            null,
            null,
            null,
            null,
            null
        )

        return DataSourceMap(Map.of<String?, ConnectionProperties?>("neo", props))
    }

    @Bean("neo")
    fun neoManager(factory: PersistenceManagerFactory): PersistenceManager {
        return factory.get("neo")
    }

}