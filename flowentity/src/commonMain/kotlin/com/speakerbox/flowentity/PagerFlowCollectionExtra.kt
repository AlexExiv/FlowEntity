package com.speakerbox.flowentity

import kotlinx.coroutines.launch

class PagerFlowCollectionExtra<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    perPage: Int = ARRAY_PER_PAGE,
    start: Boolean = true,
    private val fetch: PageFetchCallback<Id, E, Extra, CollectionExtra>
) : PagerFlowExtra<Id, E, Extra>(holder, perPage, extra)
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
        page = PAGER_END
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
        if (loading.value.isLoading || page == PAGER_END)
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
