package com.embabel.shepherd.service

import org.kohsuke.github.GHDirection
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueSearchBuilder
import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class IssueReader(
    private val github: GitHub,
) {

    private val logger = LoggerFactory.getLogger(IssueReader::class.java)

    // Get recently updated issues for repositories you're involved in
    fun getMyRecentIssues(hoursBack: Int = 24, max: Int = 10): List<GHIssue> {
        return try {
            val since = LocalDateTime.now().minusHours(hoursBack.toLong())
            val sinceString = since.toString() + "Z"

            // Search for issues involving the authenticated user
            github.searchIssues()
                .q("updated:>=$sinceString involves:${github.myself.login}")
                .sort(GHIssueSearchBuilder.Sort.UPDATED)
                .order(GHDirection.DESC)
                .list()
                .take(max)
                .toList()
        } catch (e: Exception) {
            logger.info("Error fetching my recent issues: ${e.message}")
            emptyList()
        }
    }

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
}