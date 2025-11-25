package com.embabel.shepherd.conf

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Shepherd application.
 * @param githubToken Personal access token for GitHub API, if null, the application connects anonymously
 * and may be rate limited.
 */
@ConfigurationProperties(prefix = "shepherd")
data class ShepherdProperties(
    val githubToken: String? = null,
)