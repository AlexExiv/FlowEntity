package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

typealias SingleFetchBackCallback<Id, E, EB, Extra, CollectionExtra> =
    suspend (SingleParams<Id, E, Extra, CollectionExtra>) -> EB?

typealias ArrayFetchBackCallback<Id, EB, Extra, CollectionExtra> =
    suspend (KeyParams<Id, Extra, CollectionExtra>) -> List<EB>

typealias PageFetchBackCallback<Id, EB, Extra, CollectionExtra> =
    suspend (PageParams<Id, Extra, CollectionExtra>) -> List<EB>

class EntityFlowCollectionExtraBack<Id: Any, E: Entity<Id>, EB: EntityBack<Id>, CollectionExtra>(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    collectionExtra: CollectionExtra? = null,
    private val map: (EB) -> E
) : EntityFlowCollectionExtra<Id, E, CollectionExtra>(dispatcher, collectionExtra)
{
    private var repositoryJob: Job? = null

    var repository: EntityRepositoryInterface<Id, EB>? = null
        set(value)
        {
            field = value
            repositoryJob?.cancel()

            if (value == null)
            {
                singleFetchCallback = null
                arrayFetchCallback = null
                allArrayFetchCallback = null
                return
            }

            singleFetchCallback = {
                if (it.id == null)
                {
                    null
                }
                else
                {
                    value.get(it.id)?.let { map(it) }
                }
            }
            arrayFetchCallback = { value.get(it.ids).map { map(it) } }
            allArrayFetchCallback = if (value is EntityAllRepositoryInterface<Id, EB>)
            {
                { value.fetchAll().map { map(it) } }
            }
            else
            {
                null
            }

            repositoryJob = scope.launch {
                value.updates.collect {
                    val ids = it.filter { it.entity == null }
                    val entities = it.filter { it.entity != null }

                    if (ids.size == 1)
                    {
                        commitById(id = ids[0].id, operation = ids[0].operation)
                    }
                    else if (ids.size > 1)
                    {
                        commitByIds(ids = ids.map { it.id }, operations = ids.map { it.operation })
                    }

                    if (entities.isNotEmpty())
                    {
                        commit(
                            entities = entities.map { map(it.entity!!) },
                            operations = entities.map { it.operation }
                        )
                    }
                }
            }
        }

    fun createSingleBack(
        id: Id? = null,
        start: Boolean = true,
        fetch: SingleFetchBackCallback<Id, E, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): SingleFlow<Id, E>
    {
        return SingleFlowCollectionExtra(
            holder = this,
            id = id,
            collectionExtra = collectionExtra,
            start = start,
            fetch = { fetch(it)?.let { map(it) } }
        )
    }

    fun <Extra> createSingleBackExtra(
        id: Id? = null,
        extra: Extra? = null,
        start: Boolean = true,
        fetch: SingleFetchBackCallback<Id, E, EB, Extra, CollectionExtra>
    ): SingleFlowExtra<Id, E, Extra>
    {
        return SingleFlowCollectionExtra(
            holder = this,
            id = id,
            extra = extra,
            collectionExtra = collectionExtra,
            start = start,
            fetch = { fetch(it)?.let { map(it) } }
        )
    }

    fun createKeyArrayBack(
        ids: List<Id> = listOf(),
        start: Boolean = true,
        fetch: ArrayFetchBackCallback<Id, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): ArrayKeyFlow<Id, E>
    {
        return ArrayKeyFlowCollectionExtra(
            holder = this,
            ids = ids,
            collectionExtra = collectionExtra,
            start = start,
            fetch = { fetch(it).map { map(it) } }
        )
    }

    fun <Extra> createKeyArrayBackExtra(
        ids: List<Id> = listOf(),
        extra: Extra? = null,
        start: Boolean = true,
        fetch: ArrayFetchBackCallback<Id, EB, Extra, CollectionExtra>
    ): ArrayKeyFlowExtra<Id, E, Extra>
    {
        return ArrayKeyFlowCollectionExtra(
            holder = this,
            ids = ids,
            extra = extra,
            collectionExtra = collectionExtra,
            start = start,
            fetch = { fetch(it).map { map(it) } }
        )
    }

    fun createArrayBack(
        start: Boolean = true,
        fetch: PageFetchBackCallback<Id, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): ArrayFlow<Id, E>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            collectionExtra = collectionExtra,
            start = start,
            fetch = { fetch(it).map { map(it) } }
        )
    }

    fun <Extra> createArrayBackExtra(
        extra: Extra? = null,
        start: Boolean = true,
        fetch: PageFetchBackCallback<Id, EB, Extra, CollectionExtra>
    ): ArrayFlowExtra<Id, E, Extra>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            extra = extra,
            collectionExtra = collectionExtra,
            start = start,
            fetch = { fetch(it).map { map(it) } }
        )
    }

    fun createPaginatorBack(
        perPage: Int = 35,
        start: Boolean = true,
        fetch: PageFetchBackCallback<Id, EB, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): PaginatorFlow<Id, E>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            collectionExtra = collectionExtra,
            perPage = perPage,
            start = start,
            fetch = { fetch(it).map { map(it) } }
        )
    }

    fun <Extra> createPaginatorBackExtra(
        extra: Extra? = null,
        perPage: Int = 35,
        start: Boolean = true,
        fetch: PageFetchBackCallback<Id, EB, Extra, CollectionExtra>
    ): PaginatorFlowExtra<Id, E, Extra>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            extra = extra,
            collectionExtra = collectionExtra,
            perPage = perPage,
            start = start,
            fetch = { fetch(it).map { map(it) } }
        )
    }
}

typealias EntityFlowCollectionBack<Id, Entity, EntityBack> =
    EntityFlowCollectionExtraBack<Id, Entity, EntityBack, EntityCollectionExtraParamsEmpty>

typealias EntityFlowCollectionExtraBackInt<Entity, EntityBack, CollectionExtra> =
    EntityFlowCollectionExtraBack<Int, Entity, EntityBack, CollectionExtra>
typealias EntityFlowCollectionBackInt<Entity, EntityBack> =
    EntityFlowCollectionBack<Int, Entity, EntityBack>

typealias EntityFlowCollectionExtraBackLong<Entity, EntityBack, CollectionExtra> =
    EntityFlowCollectionExtraBack<Long, Entity, EntityBack, CollectionExtra>
typealias EntityFlowCollectionBackLong<Entity, EntityBack> =
    EntityFlowCollectionBack<Long, Entity, EntityBack>

typealias EntityFlowCollectionExtraBackString<Entity, EntityBack, CollectionExtra> =
    EntityFlowCollectionExtraBack<String, Entity, EntityBack, CollectionExtra>
typealias EntityFlowCollectionBackString<Entity, EntityBack> =
    EntityFlowCollectionBack<String, Entity, EntityBack>
