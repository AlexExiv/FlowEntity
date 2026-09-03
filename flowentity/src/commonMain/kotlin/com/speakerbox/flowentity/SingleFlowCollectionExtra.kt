package com.speakerbox.flowentity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlowCollectionExtra<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
    id: Id?,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    start: Boolean = true,
    private val fetch: SingleFetchCallback<Id, E, Extra, CollectionExtra>
) : SingleFlowExtra<Id, E, Extra>(holder, id, extra)
{
    private val rxRefresh = MutableSharedFlow<SingleParams<Id, E, Extra, CollectionExtra>>(replay = 1, extraBufferCapacity = 64)
    private var lastParams: SingleParams<Id, E, Extra, CollectionExtra>? = null
    private var started = false

    override var id: Id?
        get() = super.id
        set(value)
        {
            super.id = value
            val params: SingleParams<Id, E, Extra, CollectionExtra> = SingleParams(
                resetCache = true,
                first = true,
                id = value,
                extra = lastParams?.extra,
                collectionExtra = lastParams?.collectionExtra
            )
            request(params)
        }

    constructor(
        holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
        collectionExtra: CollectionExtra? = null,
        initial: E,
        refresh: Boolean,
        fetch: SingleFetchCallback<Id, E, Extra, CollectionExtra>
    ) : this(
        holder = holder,
        id = initial.id,
        collectionExtra = collectionExtra,
        start = false,
        fetch = fetch
    )
    {
        scope.launch {
            val value = holder.requestForCombine(source = uuid, entity = initial)
            publish(value)
            setState(State.Ready)
        }

        started = !refresh

        if (refresh)
            refresh()
    }

    init
    {
        updateLoading(if (start) Loading.FirstLoading else Loading.None)

        rxRefresh
            .onEach {
                updateLoading(if (it.first) Loading.FirstLoading else Loading.Loading, null)
                if (it.first)
                {
                    setState(State.Initializing)
                }
            }
            .mapLatest {
                val fetched = try
                {
                    fetch(it)
                }
                catch (throwable: Throwable)
                {
                    if (throwable is CancellationException)
                    {
                        throw throwable
                    }

                    if (throwable is EntityFetchExceptionInterface)
                    {
                        setState(State.NotFound)
                    }
                    else
                    {
                        updateLoading(Loading.None, throwable)
                    }

                    null
                }

                if (fetched == null)
                {
                    null
                }
                else
                {
                    holder.requestForCombine(source = uuid, entity = fetched)
                }
            }
            .onEach {
                updateLoading(Loading.None)
                setState(if (it == null) State.NotFound else State.Ready)
                publish(it)
            }
            .launchInNow(scope)

        if (start)
        {
            started = true

            val params: SingleParams<Id, E, Extra, CollectionExtra> = SingleParams(
                first = true,
                id = id,
                last = entity,
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

        val params: SingleParams<Id, E, Extra, CollectionExtra> = SingleParams(
            refreshing = true,
            resetCache = resetCache,
            first = !started,
            id = id,
            last = entity,
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

    private fun request(params: SingleParams<Id, E, Extra, CollectionExtra>)
    {
        lastParams = params
        rxRefresh.tryEmit(params)
    }
}
