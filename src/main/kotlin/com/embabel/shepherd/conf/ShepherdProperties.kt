package com.embabel.shepherd.conf

import com.embabel.common.ai.model.LlmOptions
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Shepherd application.
 * @param githubToken Personal access token for GitHub API, if null, the application connects anonymously
 * and may be rate limited.
 * @param profileCategories Set of profile categories to use in Shepherd, such as "friend", "foo"
 * @param repositoriesToMonitor Set of URLS of GitHub repositories to monitor, in the format "https://github.com/owner/repo"
 */
@ConfigurationProperties(prefix = "shepherd")
data class ShepherdProperties(
    val githubToken: String? = null,
    val firstResponderLlm: LlmOptions,
    val researcherLlm: LlmOptions,

    val profileCategories: Set<String>,

    val repositoriesToMonitor: Set<String>,
)