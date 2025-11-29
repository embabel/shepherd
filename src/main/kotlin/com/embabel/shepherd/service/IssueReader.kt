package com.embabel.shepherd.service

import org.kohsuke.github.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class RepoId(
    val owner: String,
    val repo: String,
) {
    companion object {
        private val GITHUB_URL_REGEX = Regex("github\\.com/([^/]+)/([^/]+)")

        /**
         * Parse a GitHub URL into a RepoId.
         * Supports URLs like "https://github.com/owner/repo" or "https://github.com/owner/repo.git"
         * @return RepoId if the URL is valid, null otherwise
         */
        fun fromUrl(url: String): RepoId? {
            return GITHUB_URL_REGEX.find(url)?.let { match ->
                RepoId(
                    owner = match.groupValues[1],
                    repo = match.groupValues[2].removeSuffix(".git")
                )
            }
        }
    }
}


/**
 * Service to read issues from GitHub
 */
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

    /**
     * Get the most recently created issues for a specific repository.
     */
    fun getLastIssues(
        repoId: RepoId,
        count: Int = 10,
        state: GHIssueState,
    ): List<GHIssue> {
        val (owner, repo) = repoId
        return try {
            github.getRepository("$owner/$repo")
                .queryIssues()
                .state(state)
                .sort(GHIssueQueryBuilder.Sort.CREATED)
                .direction(GHDirection.DESC)
                .list()
                .take(count)
                .toList()
        } catch (e: Exception) {
            logger.error("Error fetching last issues for $owner/$repo: ${e.message}")
            emptyList()
        }
    }
}