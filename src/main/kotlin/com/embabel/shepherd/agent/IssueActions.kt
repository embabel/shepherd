package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import org.kohsuke.github.GHIssue

@EmbabelComponent
class IssueActions {

    @Action
    fun doSomething(issue: GHIssue) {
        println("Found new issue #${issue.number}: ${issue.title}\n${issue.htmlUrl}")
    }
}