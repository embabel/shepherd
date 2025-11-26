package com.embabel.shepherd

import com.embabel.agent.api.invocation.UtilityInvocation
import com.embabel.agent.core.AgentPlatform
import com.embabel.shepherd.service.IssueReader
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod

@Profile("!test")
@ShellComponent
class ShepherdShell(
    private val agentPlatform: AgentPlatform,
    private val issueReader: IssueReader,
) {

    @ShellMethod("run")
    fun run() {
        val lastIssue = issueReader.getMyRecentIssues(200, 1).single()
        UtilityInvocation.on(agentPlatform)
            .terminateWhenStuck()
            .run(lastIssue)
    }
}
