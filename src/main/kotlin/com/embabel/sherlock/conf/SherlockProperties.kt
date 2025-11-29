package com.embabel.sherlock.conf

import com.embabel.common.ai.model.LlmOptions
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Sherlock
 *  @param profileCategories Set of profile categories to use in Shepherd, such as "friend", "foo"
 */
@ConfigurationProperties(prefix = "sherlock")
data class SherlockProperties(
    val researchLLm: LlmOptions,
    val profileCategories: Set<String>,
)