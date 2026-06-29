package com.speakerbox.flowentity

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface EntityFetchExceptionInterface
{
    val code: Int
}

class EntityFetchException(override val code: Int) : IllegalArgumentException(), EntityFetchExceptionInterface

open class SingleFlowExtra<Id: Any, E: Entity<Id>, Extra>(
    holder: EntityFlowCollectionExtra<Id, E, *>,
    open var id: Id?,
    extra: Extra? = null
) : EntityFlow<Id, E, E?>(holder)
{
    enum class State
    {
        Initializing,
        Ready,
        NotFound,
        Deleted
    }

    private val _state = MutableStateFlow(State.Initializing)
    protected val data = MutableStateFlow<E?>(null)

    var extra: Extra? = extra
        protected set

    val state: StateFlow<State> = _state.asStateFlow()
    val entity: E? get() = data.value

    override fun update(source: String, entity: E)
    {
        if (id == entity.id && source != uuid)
        {
            publish(entity)
        }
    }

    override fun update(source: String, entities: Map<Id, E>)
    {
        val entity = entities[id]
        if (entity != null && source != uuid)
        {
            publish(entity)
        }
    }

    override fun update(entities: Map<Id, E>, operation: UpdateOperation)
    {
        val entity = entities[id]
        if (entity != null)
        {
            when (operation)
            {
                UpdateOperation.None,
                UpdateOperation.Insert,
                UpdateOperation.Update ->
                {
                    publish(entity)
                    _state.value = State.Ready
                }
                UpdateOperation.Delete,
                UpdateOperation.Clear -> clear()
            }
        }
    }

    override fun update(entities: Map<Id, E>, operations: Map<Id, UpdateOperation>)
    {
        val entity = entities[id]
        val operation = operations[id]

        if (entity != null && operation != null)
        {
            when (operation)
            {
                UpdateOperation.None,
                UpdateOperation.Insert,
                UpdateOperation.Update ->
                {
                    publish(entity)
                    _state.value = State.Ready
                }
                UpdateOperation.Delete,
                UpdateOperation.Clear -> clear()
            }
        }
    }

    override fun delete(ids: Set<Id>)
    {
        if (ids.contains(id))
        {
            clear()
        }
    }

    override fun clear()
    {
        publish(null)
        _state.value = State.Deleted
    }

    override suspend fun collect(collector: FlowCollector<E?>)
    {
        attachCollector()
        try
        {
            data.collect(collector)
        }
        finally
        {
            detachCollector()
        }
    }

    open fun refresh(resetCache: Boolean = false, extra: Extra? = null)
    {
    }

    open suspend fun refreshNow(resetCache: Boolean = false, extra: Extra? = null)
    {
        this.extra = extra ?: this.extra
    }

    protected fun publish(entity: E?)
    {
        data.value = entity
    }

    protected fun setState(state: State)
    {
        _state.value = state
    }
}

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
            request(
                SingleParams(
                    resetCache = true,
                    first = true,
                    id = value,
                    extra = extra,
                    collectionExtra = collectionExtra
                )
            )
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
            request(
                SingleParams(
                    first = true,
                    id = id,
                    last = entity,
                    extra = extra,
                    collectionExtra = collectionExtra
                )
            )
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
        request(
            SingleParams(
                refreshing = true,
                resetCache = resetCache,
                first = !started,
                id = id,
                last = entity,
                extra = this.extra,
                collectionExtra = collectionExtra
            )
        )
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

typealias SingleFlow<Id, Entity> = SingleFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias SingleFlowExtraInt<Entity, Extra> = SingleFlowExtra<Int, Entity, Extra>
typealias SingleFlowInt<Entity> = SingleFlow<Int, Entity>

typealias SingleFlowExtraLong<Entity, Extra> = SingleFlowExtra<Long, Entity, Extra>
typealias SingleFlowLong<Entity> = SingleFlow<Long, Entity>

typealias SingleFlowExtraString<Entity, Extra> = SingleFlowExtra<String, Entity, Extra>
typealias SingleFlowString<Entity> = SingleFlow<String, Entity>
