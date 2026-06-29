package com.speakerbox.flowentity

import kotlinx.coroutines.launch

const val PAGINATOR_END = -9999

open class PaginatorFlowExtra<Id: Any, E: Entity<Id>, Extra>(
    holder: EntityFlowCollectionExtra<Id, E, *>,
    perPage: Int,
    extra: Extra? = null
) : ArrayFlowExtra<Id, E, Extra>(holder, perPage, extra)
{
    open fun next()
    {
    }

    protected open fun append(entities: List<E>): List<E>
    {
        if (perPage == ARRAY_PER_PAGE)
        {
            page = PAGINATOR_END
            return entities
        }

        val newEntities = _entities.toMutableList()
        newEntities.appendOrReplaceEntity(entities)
        page = if (entities.size >= perPage) page + 1 else PAGINATOR_END
        return newEntities
    }
}

class PaginatorFlowCollectionExtra<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    perPage: Int = ARRAY_PER_PAGE,
    start: Boolean = true,
    private val fetch: PageFetchCallback<Id, E, Extra, CollectionExtra>
) : PaginatorFlowExtra<Id, E, Extra>(holder, perPage, extra)
{
    private var started = false

    constructor(
        holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
        collectionExtra: CollectionExtra? = null,
        initial: List<E>,
        fetch: PageFetchCallback<Id, E, Extra, CollectionExtra>
    ) : this(
        holder = holder,
        collectionExtra = collectionExtra,
        start = false,
        fetch = fetch
    )
    {
        scope.launch {
            setEntities(entities = holder.requestForCombine(source = uuid, entities = initial))
        }

        started = true
        page = PAGINATOR_END
    }

    init
    {
        updateLoading(if (start) Loading.FirstLoading else Loading.None)

        if (start)
        {
            started = true
            val params: PageParams<Id, Extra, CollectionExtra> = PageParams(
                page = 0,
                perPage = perPage,
                first = true,
                extra = extra,
                collectionExtra = collectionExtra
            )
            request(params)
        }
    }

    override fun refresh(resetCache: Boolean, extra: Extra?)
    {
        scope.launch {
            refreshNow(resetCache = resetCache, extra = extra)
        }
    }

    override suspend fun refreshNow(resetCache: Boolean, extra: Extra?)
    {
        super.refreshNow(resetCache = resetCache, extra = extra)
        val params: PageParams<Id, Extra, CollectionExtra> = PageParams(
            page = page + 1,
            perPage = perPage,
            refreshing = true,
            resetCache = resetCache,
            first = !started,
            extra = this.extra,
            collectionExtra = collectionExtra
        )
        request(params)
        started = true
    }

    override fun refreshData(resetCache: Boolean, data: Any?)
    {
        @Suppress("UNCHECKED_CAST")
        collectionExtra = data as? CollectionExtra ?: collectionExtra
        refresh(resetCache = resetCache, extra = extra)
    }

    override fun next()
    {
        if (loading.value.isLoading || page == PAGINATOR_END)
        {
            return
        }

        if (started)
        {
            val params: PageParams<Id, Extra, CollectionExtra> = PageParams(
                page = page + 1,
                perPage = perPage,
                extra = extra,
                collectionExtra = collectionExtra
            )
            request(params)
        }
        else
        {
            refresh()
        }
    }

    private fun request(params: PageParams<Id, Extra, CollectionExtra>)
    {
        if (params.page < 0)
        {
            return
        }

        updateLoading(if (params.first) Loading.FirstLoading else Loading.Loading, null)

        scope.launch {
            val value = try
            {
                fetch(params)
            }
            catch (throwable: Throwable)
            {
                updateLoading(Loading.None, throwable)
                listOf()
            }

            val combined = holder.requestForCombine(source = uuid, entities = value)
            updateLoading(Loading.None)
            setEntities(entities = append(combined))
        }
    }
}

typealias PaginatorFlow<Id, Entity> =
    PaginatorFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias PaginatorFlowExtraInt<Entity, Extra> = PaginatorFlowExtra<Int, Entity, Extra>
typealias PaginatorFlowInt<Entity> = PaginatorFlow<Int, Entity>

typealias PaginatorFlowExtraLong<Entity, Extra> = PaginatorFlowExtra<Long, Entity, Extra>
typealias PaginatorFlowLong<Entity> = PaginatorFlow<Long, Entity>

typealias PaginatorFlowExtraString<Entity, Extra> = PaginatorFlowExtra<String, Entity, Extra>
typealias PaginatorFlowString<Entity> = PaginatorFlow<String, Entity>
