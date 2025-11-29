package com.embabel.shepherd.service

import com.embabel.common.util.loggerFor
import com.embabel.common.util.trim
import com.embabel.shepherd.conf.ShepherdProperties
import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Make GitHub API available as a Spring bean.
 */
@Configuration
internal class GitHubConfig(
    properties: ShepherdProperties,
) {

    private val logger = LoggerFactory.getLogger(GitHubConfig::class.java)

    private val github: GitHub = if (properties.githubToken != null) {
        loggerFor<GitHubConfig>().info(
            "🔑 Connecting to GitHub using personal access token {}",
            trim(properties.githubToken, 12, 4, "***")
        )
        GitHub.connectUsingOAuth(properties.githubToken)
    } else {
        logger.warn("No GitHub token configured: The application will connect anonymously and will be rate limited.")
        GitHub.connectAnonymously()
    }

    @Bean
    fun github(): GitHub = github

}