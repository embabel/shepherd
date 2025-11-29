package com.embabel.sherlock.conf

import com.embabel.common.ai.model.LlmOptions
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sherlock")
data class SherlockProperties(
    val researchLLm: LlmOptions,
)