package com.speakerbox.flowentity

import kotlinx.coroutines.launch

class SingleFlowCollectionExtra<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
    id: Id?,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    start: Boolean = true,
    private val fetch: SingleFetchCallback<Id, E, Extra, CollectionExtra>
) : SingleFlowExtra<Id, E, Extra>(holder, id, extra)
{
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
                extra = extra,
                collectionExtra = collectionExtra
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
        {
            refresh()
        }
    }

    init
    {
        updateLoading(if (start) Loading.FirstLoading else Loading.None)

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
        updateLoading(if (params.first) Loading.FirstLoading else Loading.Loading, null)
        if (params.first)
        {
            setState(State.Initializing)
        }

        scope.launch {
            val fetched = try
            {
                fetch(params)
            }
            catch (throwable: Throwable)
            {
                if (throwable is EntityFetchExceptionInterface)
                {
                    setState(State.NotFound)
                }
                else
                {
                    updateLoading(Loading.None, throwable)
                }
                publish(null)
                return@launch
            }

            if (fetched == null)
            {
                updateLoading(Loading.None)
                setState(State.NotFound)
                publish(null)
            }
            else
            {
                val value = holder.requestForCombine(source = uuid, entity = fetched)
                updateLoading(Loading.None)
                setState(State.Ready)
                publish(value)
            }
        }
    }
}
