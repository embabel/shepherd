package com.embabel.shepherd.service

import com.embabel.common.util.loggerFor
import com.embabel.shepherd.conf.ShepherdProperties
import org.kohsuke.github.GHDirection
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueSearchBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Make GitHub API available as a Spring bean.
 */
@Configuration
class GitHubConfig(
    properties: ShepherdProperties,
) {

    private val logger = LoggerFactory.getLogger(GitHubConfig::class.java)

    private val github: GitHub = if (properties.githubToken != null) {
        loggerFor<GitHubConfig>().info("🔑 Connecting to GitHub using personal access token")
        GitHub.connectUsingOAuth(properties.githubToken)
    } else {
        logger.warn("No GitHub token configured: The application will connect anonymously and will be rate limited.")
        GitHub.connectAnonymously()
    }

    @Bean
    fun github(): GitHub = github

//    // Search for recently updated issues across all repositories you have access to
//    fun searchRecentIssues(hoursBack: Int = 24, state: String = "open"): List<GHIssue> {
//        return try {
//            val since = LocalDateTime.now().minusHours(hoursBack.toLong())
//            val sinceString = since.toString() + "Z" // ISO format
//
//            github.searchIssues()
//                .q("updated:>=$sinceString state:$state")
//                .sort(GHIssueSearchBuilder.Sort.UPDATED)
//                .order(GHDirection.DESC)
//                .list()
//                .take(100) // Limit results
//                .toList()
//        } catch (e: Exception) {
//            println("Error searching recent issues: ${e.message}")
//            emptyList()
//        }
//    }

    // Get recently updated issues for repositories you're involved in
    fun getMyRecentIssues(hoursBack: Int = 24): List<GHIssue> {
        return try {
            val since = LocalDateTime.now().minusHours(hoursBack.toLong())
            val sinceString = since.toString() + "Z"

            // Search for issues involving the authenticated user
            github.searchIssues()
                .q("updated:>=$sinceString involves:${github.myself.login}")
                .sort(GHIssueSearchBuilder.Sort.UPDATED)
                .order(GHDirection.DESC)
                .list()
                .take(50)
                .toList()
        } catch (e: Exception) {
            logger.info("Error fetching my recent issues: ${e.message}")
            emptyList()
        }
    }
}