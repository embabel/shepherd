package com.embabel.shepherd.service

import org.kohsuke.github.GHIssue

object DummyGitHubUpdater : GitHubUpdater {

    override fun labelIssue(ghIssue: GHIssue, labels: Collection<String>) {
        println("************ Dry run: would label issue #${ghIssue.number} in ${ghIssue.repository.name} with labels $labels")
    }

    override fun addComment(ghIssue: GHIssue, comment: String) {
        println("************ Dry run: would add comment to issue #${ghIssue.number} in ${ghIssue.repository.name}:\n$comment")
    }
}