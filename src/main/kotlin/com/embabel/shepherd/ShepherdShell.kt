package com.embabel.shepherd

import com.embabel.agent.api.invocation.UtilityInvocation
import com.embabel.agent.core.AgentPlatform
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.domain.Employer
import com.embabel.shepherd.domain.Person
import com.embabel.shepherd.domain.PullRequest
import com.embabel.shepherd.domain.RaisableIssue
import com.embabel.shepherd.domain.Repository
import com.embabel.shepherd.service.IssueReader
import com.embabel.shepherd.service.RepoId
import com.embabel.sherlock.domain.PersonWithProfile
import com.fasterxml.jackson.databind.ObjectMapper
import org.drivine.query.FileMixinTemplate
import org.drivine.query.MixinTemplate
import org.drivine.query.findAll
import org.drivine.query.findById
import org.kohsuke.github.GHIssueState
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@ShellComponent
class ShepherdShell(
    private val agentPlatform: AgentPlatform,
    private val issueReader: IssueReader,
    private val mixinTemplate: MixinTemplate,
    private val properties: ShepherdProperties,
    private val objectMapper: ObjectMapper,
) {

    @ShellMethod("fetch issues")
    fun lastIssues(
        @ShellOption(defaultValue = "5", help = "How many issues to get") count: Int,
    ) {
        val repos = properties.repositoriesToMonitor
            .mapNotNull { RepoId.fromUrl(it) }
        for (repo in repos) {
            val issues = issueReader.getLastIssues(repo, count, GHIssueState.OPEN)
            for (issue in issues) {
                UtilityInvocation.on(agentPlatform)
                    .terminateWhenStuck()
                    .run(issue)
            }
        }
    }

    @ShellMethod("stars")
    fun stars(
        @ShellOption(defaultValue = "5", help = "How many stars to get") count: Int,
    ) {
        val repos = properties.repositoriesToMonitor
            .mapNotNull { RepoId.fromUrl(it) }
        for (repo in repos) {
            val stargazers = issueReader.getLastStargazers(repo, count)
            for (stargazer in stargazers) {
                UtilityInvocation.on(agentPlatform)
                    .terminateWhenStuck()
                    .run(stargazer)
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
            val deep = mixinTemplate.findById<PersonWithProfile>(issue.raisedBy.uuid.toString())
            println(
                "Profile: " + objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(deep)
            )
        }
    }

    @ShellMethod
    fun prs() {
        val prs = mixinTemplate.findAll<PullRequest>()
        for (issue in prs) {
            println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(issue))
        }
    }

    @ShellMethod
    fun employers() {
        val employers = mixinTemplate.findAll<Employer>()
        for (employer in employers) {
            println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(employer))
        }
    }

    @ShellMethod
    fun repos() {
        val repositories = mixinTemplate.findAll<Repository>()
        for (repo in repositories) {
            println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(repo))
        }
    }

    @ShellMethod
    fun zap(): String {
        (mixinTemplate as FileMixinTemplate).clear()
        return "Data deleted"
    }


}
