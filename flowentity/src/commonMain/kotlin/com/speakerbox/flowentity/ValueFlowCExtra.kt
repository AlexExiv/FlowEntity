package com.speakerbox.flowentity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class ValueFlowCExtra<Value: Any, Extra, CollectionExtra>(
    holder: ValueFlowCollectionExtra<CollectionExtra>,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    start: Boolean = true,
    private val fetch: ValueFetchCallback<Value, Extra, CollectionExtra>
) : ValueFlowExtra<Value, Extra>(
    collection = holder,
    dispatcher = holder.dispatcher,
    extra = extra
)
{
    private sealed class Result<Value: Any>
    {
        class Success<Value: Any>(val value: Value?) : Result<Value>()
        class Failure<Value: Any> : Result<Value>()
    }

    private val rxRefresh = MutableSharedFlow<ValueParams<Value, Extra, CollectionExtra>>(replay = 1, extraBufferCapacity = 64)
    private var started = false

    init
    {
        holder.add(this)
        updateLoading(if (start) Loading.FirstLoading else Loading.None)

        rxRefresh
            .onEach {
                updateLoading(if (it.first) Loading.FirstLoading else Loading.Loading, null)
                if (it.first)
                    setState(State.Initializing)
            }
            .mapLatest {
                try
                {
                    Result.Success(fetch(it))
                }
                catch (throwable: Throwable)
                {
                    if (throwable is CancellationException)
                        throw throwable

                    updateLoading(Loading.None, throwable)
                    Result.Failure<Value>()
                }
            }
            .onEach {
                updateLoading(Loading.None)

                when (it)
                {
                    is Result.Success ->
                    {
                        setState(if (it.value == null) State.Empty else State.Ready)
                        publish(it.value)
                    }
                    is Result.Failure ->
                    {
                        if (value == null)
                            setState(State.Empty)
                    }
                }
            }
            .launchInNow(scope)

        if (start)
        {
            started = true

            val params = ValueParams(
                first = true,
                last = value,
                extra = extra,
                collectionExtra = collectionExtra
            )

            request(params)
        }
    }

    override fun refresh(resetCache: Boolean, extra: Extra?)
    {
        requestRefresh(resetCache = resetCache, extra = extra)
    }

    override suspend fun refreshNow(resetCache: Boolean, extra: Extra?)
    {
        requestRefresh(resetCache = resetCache, extra = extra)
    }

    override fun refreshData(resetCache: Boolean, collectionExtra: Any?)
    {
        @Suppress("UNCHECKED_CAST")
        this.collectionExtra = collectionExtra as CollectionExtra?
        refresh(resetCache = resetCache, extra = extra)
    }

    private fun requestRefresh(resetCache: Boolean, extra: Extra?)
    {
        this.extra = extra ?: this.extra

        if (resetCache)
        {
            publish(null)
            setState(State.Empty)
        }

        val params = ValueParams(
            refreshing = true,
            resetCache = resetCache,
            first = !started,
            last = value,
            extra = this.extra,
            collectionExtra = collectionExtra
        )

        request(params)
        started = true
    }

    private fun request(params: ValueParams<Value, Extra, CollectionExtra>)
    {
        rxRefresh.tryEmit(params)
    }
}
