package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach

class ValueFlowCollectionExtra<CollectionExtra>(
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    collectionExtra: CollectionExtra? = null
)
{
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val items = mutableListOf<FlowWeakReference<ValueFlowExtra<*, *>>>()
    private var sourceJob: Job? = null

    var collectionExtra: CollectionExtra? = collectionExtra
        private set

    internal fun add(flow: ValueFlowExtra<*, *>)
    {
        items.add(FlowWeakReference(flow))
    }

    internal fun remove(flow: ValueFlowExtra<*, *>)
    {
        items.removeAll {
            val value = it.get()
            value == null || value === flow
        }
    }

    fun <Value: Any> createValue(
        start: Boolean = true,
        fetch: ValueFetchCallback<Value, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): ValueFlow<Value>
    {
        return ValueFlowCExtra(
            holder = this,
            start = start,
            collectionExtra = collectionExtra,
            fetch = fetch
        )
    }

    fun <Value: Any, Extra> createValueExtra(
        extra: Extra? = null,
        start: Boolean = true,
        fetch: ValueFetchCallback<Value, Extra, CollectionExtra>
    ): ValueFlowExtra<Value, Extra>
    {
        return ValueFlowCExtra(
            holder = this,
            extra = extra,
            start = start,
            collectionExtra = collectionExtra,
            fetch = fetch
        )
    }

    fun refresh(resetCache: Boolean = false)
    {
        items.forEach { it.get()?.refreshData(resetCache = resetCache, collectionExtra = collectionExtra) }
    }

    fun refresh(collectionExtra: CollectionExtra?, resetCache: Boolean = false)
    {
        this.collectionExtra = collectionExtra
        refresh(resetCache = resetCache)
    }

    suspend fun refreshNow(resetCache: Boolean = false)
    {
        refresh(resetCache = resetCache)
    }

    suspend fun refreshNow(collectionExtra: CollectionExtra?, resetCache: Boolean = false)
    {
        refresh(collectionExtra = collectionExtra, resetCache = resetCache)
    }

    fun refreshOn(source: Flow<CollectionExtra?>, resetCache: Boolean = true): FlowSubscription
    {
        sourceJob?.cancel()

        val job = source
            .onEach { refresh(collectionExtra = it, resetCache = resetCache) }
            .launchInNow(scope)

        sourceJob = job
        return FlowJobSubscription(job)
    }

    fun refreshOn(source: StateFlow<CollectionExtra?>, resetCache: Boolean = true): FlowSubscription
    {
        val changed = collectionExtra != source.value
        collectionExtra = source.value

        if (changed)
            refresh(resetCache = resetCache)

        return refreshOn(source = source.drop(1), resetCache = resetCache)
    }

    fun close()
    {
        sourceJob?.cancel()
        items.toList().forEach { it.get()?.dispose() }
        scope.cancel()
    }

    companion object
    {
        fun create(dispatcher: CoroutineDispatcher = Dispatchers.Default) =
            ValueFlowCollectionExtra<EntityCollectionExtraParamsEmpty>(
                dispatcher = dispatcher,
                collectionExtra = EntityCollectionExtraParamsEmpty()
            )
    }
}

typealias ValueFlowCollection = ValueFlowCollectionExtra<EntityCollectionExtraParamsEmpty>
