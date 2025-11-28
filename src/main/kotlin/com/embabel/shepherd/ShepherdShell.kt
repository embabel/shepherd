package com.embabel.shepherd

import com.embabel.agent.api.invocation.UtilityInvocation
import com.embabel.agent.core.AgentPlatform
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.domain.RaisableIssue
import com.embabel.shepherd.service.IssueReader
import com.embabel.shepherd.service.RepoId
import com.fasterxml.jackson.databind.ObjectMapper
import org.drivine.query.FileMixinTemplate
import org.drivine.query.MixinTemplate
import org.drivine.query.findAll
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod

@Profile("!test")
@ShellComponent
class ShepherdShell(
    private val agentPlatform: AgentPlatform,
    private val issueReader: IssueReader,
    private val mixinTemplate: MixinTemplate,
    private val properties: ShepherdProperties,
    private val objectMapper: ObjectMapper,
) {

    @ShellMethod("run")
    fun run() {
        val repos = properties.repositoriesToMonitor
            .map { RepoId.fromUrl(it) }
        for (repo in repos) {
            val issues = issueReader.getLastIssues(repo, 1)
            for (issue in issues) {
                UtilityInvocation.on(agentPlatform)
                    .terminateWhenStuck()
                    .run(issue)
            }

        }
    }


    @ShellMethod(value = "people")
    fun people() {
        val people = mixinTemplate.findAll(Person::class.java)
        for (person in people) {
            println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(person))
        }
    }

    @ShellMethod
    fun issues() {
        val issues = mixinTemplate.findAll<RaisableIssue>()
        for (issue in issues) {
            println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(issue))
        }
    }

    @ShellMethod
    fun zap(): String {
        (mixinTemplate as FileMixinTemplate).clear()
        return "Data deleted"
    }


}
