package com.embabel.shepherd.agent.domain

import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.domain.PersonRepository
import com.embabel.shepherd.util.Neo4jPropertiesInitializer
import com.github.javaparser.utils.Utils.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Rollback
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.util.UUID


@SpringBootTest
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class PersonRepositoryTest(@param:Autowired val repo: PersonRepository) {

    @Test
    fun `should save a person`() {
        val person = Person(
            uuid = UUID.randomUUID(),
            name = "Peter",
            bio = "A very annoying guy. Avoid",
            githubId = "peterwashere"
        )

        val saved = repo.save(person)
        assertNotNull(saved)
        println("saved: $saved")
    }
}
