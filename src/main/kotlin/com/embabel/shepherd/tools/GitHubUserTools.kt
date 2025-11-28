package com.embabel.shepherd.tools

import org.kohsuke.github.GHUser
import org.springframework.ai.tool.annotation.Tool

/**
 * Expose GHUser as Spring AI Tools
 */
class GitHubUserTools(
    private val ghUser: GHUser
) {

    @Tool
    fun userName(): String {
        return ghUser.name ?: ghUser.login
    }

    @Tool(description = "Get the list of repositories for the user")
    fun repositories(): List<String> {
        return ghUser.repositories.map {
            """
                ${it.value.htmlUrl}
                Description: ${it.value.description}
                Language: ${it.value.language}
                Fork: ${it.value.isFork}
                Homepage: ${it.value.homepage}
            """.trimIndent()
        }
    }

    @Tool(description = "Get the list of organizations the user belongs to")
    fun organizations(): List<String> {
        return ghUser.organizations.map {
            """
                ${it.htmlUrl}
                ${it.name}
            """.trimIndent()
        }
    }


}