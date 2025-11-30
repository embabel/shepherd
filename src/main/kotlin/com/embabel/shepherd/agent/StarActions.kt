package com.embabel.shepherd.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.shepherd.conf.ShepherdProperties
import com.embabel.shepherd.service.Store
import org.kohsuke.github.GHStargazer
import org.slf4j.LoggerFactory

@EmbabelComponent
class StarActions(
    val properties: ShepherdProperties,
    private val store: Store,
) {

    private val logger = LoggerFactory.getLogger(StarActions::class.java)

    /**
     * React to a stargazer by finding or creating the person who starred the repository.
     */
    @Action
    fun reactToStar(stargazer: GHStargazer): NewPerson? {
        val user = stargazer.user
        val starredAt = stargazer.starredAt

        logger.info(
            "User '{}' starred repository '{}' at {}",
            user.login,
            stargazer.repository.fullName,
            starredAt
        )

        val personRetrieval = store.retrieveOrCreatePersonFrom(user)
        if (!personRetrieval.existing) {
            logger.info("Created new person for stargazer: login='{}', name='{}'", user.login, user.name)
            store.save(personRetrieval.entity)
            return NewPerson(personRetrieval.entity)
        }
        return null
    }
}
