package com.embabel.shepherd.community.service

import org.kohsuke.github.GHIssue

/**
 * Interface so we can dry run without making actual changes
 */
interface GitHubUpdater {

    fun labelIssue(ghIssue: GHIssue, labels: Collection<String>)
}

