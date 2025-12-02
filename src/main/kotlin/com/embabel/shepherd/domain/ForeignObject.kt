package com.embabel.shepherd.domain

interface ForeignObject<SERVICE, FO> {

    /**
     * Get full foreign details
     */
    fun materialize(service: SERVICE): FO

    /**
     * Sync the local object with the foreign object
     */
    fun sync(service: SERVICE)
}