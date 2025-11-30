package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.service.Store
import org.kohsuke.github.GHStargazer
import org.kohsuke.github.GHUser
import org.slf4j.LoggerFactory

@EmbabelComponent
class StarActions(
    val properties: ShepherdProperties,
    private val store: Store,
) {

    private val logger = LoggerFactory.getLogger(StarActions::class.java)

    /**
     * React to a stargazer by extracting the user who starred the repository.
     */
    @Action
    fun reactToStar(stargazer: GHStargazer): GHUser {
        val user = stargazer.user
        val starredAt = stargazer.starredAt

        logger.info(
            "User '{}' starred repository '{}' at {}",
            user.login,
            stargazer.repository.fullName,
            starredAt
        )

        return user
    }
}
