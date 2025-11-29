package com.embabel.shepherd.conf

import com.embabel.common.ai.model.LlmOptions
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Shepherd application.
 * @param githubToken Personal access token for GitHub API, if null, the application connects anonymously
 * and may be rate limited.
 * @param repositoriesToMonitor Set of URLs of GitHub repositories to monitor, in the format "https://github.com/owner/repo"
 * @param issueLabels Set of labels to apply to created issues, such as "bug", "enhancement"
 */
@ConfigurationProperties(prefix = "shepherd")
data class ShepherdProperties(
    val githubToken: String? = null,
    val triageLlm: LlmOptions,

    val repositoriesToMonitor: Set<String>,

    val issueLabels: Set<String>,

    val programmingLanguages: Set<String>,

    val frameworks: Set<String>,
)