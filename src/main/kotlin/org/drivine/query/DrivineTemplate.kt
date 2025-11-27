package org.drivine.query

interface DrivineTemplate : MixinTemplate {

    /**
     * Find entities matching the given where clause, applying the given mixins.
     * The whereClause merely finds the id. The convention is that n
     * will be the node being queried.
     */
    fun <T> findWhere(whereClause: String, mixins: List<Class<out T>>): Iterable<T>

}