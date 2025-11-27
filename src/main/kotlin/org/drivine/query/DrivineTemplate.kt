package org.drivine.query

interface MixinTemplate {

    fun <T> findById(id: Long, mixins: List<Class<out T>>): T?

    fun <T> findById(id: Long): T? = findById(id, emptyList())

    fun <T, U : T> findById(id: Long, mixin: Class<U>): U?

    fun <T> save(entity: T): T

    fun <T, U : T> eraseSpecialization(entity: U, mixin: Class<U>): T?

}

interface DrivineTemplate : MixinTemplate {

    /**
     * Find entities matching the given where clause, applying the given mixins.
     * The whereClause merely finds the id. The convention is that n
     * will be the node being queried.
     */
    fun <T> findWhere(whereClause: String, mixins: List<Class<out T>>): Iterable<T>


}