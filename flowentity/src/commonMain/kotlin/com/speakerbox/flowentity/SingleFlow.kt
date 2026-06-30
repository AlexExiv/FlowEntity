package com.speakerbox.flowentity

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

typealias SingleFlow<Id, Entity> = SingleFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias SingleFlowExtraInt<Entity, Extra> = SingleFlowExtra<Int, Entity, Extra>
typealias SingleFlowInt<Entity> = SingleFlow<Int, Entity>

typealias SingleFlowExtraLong<Entity, Extra> = SingleFlowExtra<Long, Entity, Extra>
typealias SingleFlowLong<Entity> = SingleFlow<Long, Entity>

typealias SingleFlowExtraString<Entity, Extra> = SingleFlowExtra<String, Entity, Extra>
typealias SingleFlowString<Entity> = SingleFlow<String, Entity>
