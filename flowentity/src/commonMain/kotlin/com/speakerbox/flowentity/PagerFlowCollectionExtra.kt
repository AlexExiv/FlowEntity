package com.speakerbox.flowentity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PagerFlowCollectionExtra<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    perPage: Int = ARRAY_PER_PAGE,
    start: Boolean = true,
    private val fetch: PageFetchCallback<Id, E, Extra, CollectionExtra>
) : PagerFlowExtra<Id, E, Extra>(holder, perPage, extra)
{
    private val rxPage = Channel<PageParams<Id, Extra, CollectionExtra>>(capacity = Channel.BUFFERED)
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

        rxPage
            .receiveAsFlow()
            .filter { it.page >= 0 }
            .onEach {
                updateLoading(if (it.first) Loading.FirstLoading else Loading.Loading, null)
            }
            .mapLatest {
                val value = try
                {
                    fetch(it)
                }
                catch (throwable: Throwable)
                {
                    if (throwable is CancellationException)
                    {
                        throw throwable
                    }

                    updateLoading(Loading.None, throwable)
                    listOf()
                }

                holder.requestForCombine(source = uuid, entities = value)
            }
            .onEach {
                updateLoading(Loading.None)
                setEntities(entities = append(it))
            }
            .launchInNow(scope)

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
            rxPage.trySend(params)
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
        rxPage.trySend(params)
    }
}
