package com.embabel.shepherd.domain

import org.kohsuke.github.GHUser
import java.util.*

/**
 * Person's GitHub profile information.
 * @param type indicates whether the profile is a User or an Organization.
 */
data class GitHubProfile(
    val login: String,
    val name: String?,
    val bio: String?,
    val blog: String?,
    val location: String?,
    val type: String,
    val publicRepoCount: Int,
    val followers: Int,
    val avatarUrl: String?,
    val htmlUrl: String?,
    val twitter: String? = null,
)

/**
 * Person who is a member of the community.
 */
interface Person : HasUUID {
    val name: String
    val employer: Organization?
    val github: GitHubProfile?

    companion object {

        operator fun invoke(
            uuid: UUID = UUID.randomUUID(),
            name: String,
            github: GitHubProfile? = null,
            employer: Organization? = null,
        ): Person = PersonImpl(
            uuid = uuid,
            name = name,
            github = github,
            employer = employer,
        )

        @JvmStatic
        fun fromGHUser(ghUser: GHUser, employer: Organization? = null): Person {
            val github = GitHubProfile(
                login = ghUser.login,
                name = ghUser.name,
                bio = ghUser.bio,
                blog = ghUser.blog,
                location = ghUser.location,
                type = ghUser.type,
                publicRepoCount = ghUser.publicRepoCount,
                followers = ghUser.followersCount,
                avatarUrl = ghUser.avatarUrl,
                htmlUrl = ghUser.htmlUrl?.toString(),
                twitter = ghUser.twitterUsername,
            )
            return PersonImpl(
                uuid = UUID.randomUUID(),
                name = ghUser.name ?: ghUser.login,
                github = github,
                employer = employer,
            )
        }
    }
}

internal data class PersonImpl(
    override val uuid: UUID,
    override val name: String,
    override val github: GitHubProfile?,
    override val employer: Organization?,
) : Person

