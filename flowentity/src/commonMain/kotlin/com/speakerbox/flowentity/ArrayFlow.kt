package com.speakerbox.flowentity

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow

const val ARRAY_PER_PAGE = 999999

open class ArrayFlowExtra<Id: Any, E: Entity<Id>, Extra>(
    holder: EntityFlowCollectionExtra<Id, E, *>,
    perPage: Int = ARRAY_PER_PAGE,
    extra: Extra? = null
) : EntityFlow<Id, E, List<E>>(holder)
{
    enum class UpdatePolicy
    {
        Update,
        Reload
    }

    protected val data = MutableStateFlow<List<E>>(listOf())
    protected var _entities = mutableListOf<E>()

    var extra: Extra? = extra
        protected set

    var page: Int = -1
        protected set

    var perPage: Int = perPage
        protected set

    var updatePolicy: UpdatePolicy = UpdatePolicy.Update

    val entities: List<E> get() = _entities

    operator fun get(index: Int): SingleFlow<Id, E>
    {
        return holder.createSingle(initial = _entities[index])
    }

    override fun update(source: String, entity: E)
    {
        val index = _entities.indexOfFirst { it.id == entity.id }
        if (index != -1 && source != uuid)
        {
            _entities[index] = entity
            publish()
        }
    }

    override fun update(source: String, entities: Map<Id, E>)
    {
        if (source != uuid)
        {
            var was = false
            _entities.forEachIndexed { index, entity ->
                val updated = entities[entity.id]
                if (updated != null)
                {
                    _entities[index] = updated
                    was = true
                }
            }

            if (was)
            {
                publish()
            }
        }
    }

    override fun update(entities: Map<Id, E>, operation: UpdateOperation)
    {
        if (operation == UpdateOperation.Insert || updatePolicy == UpdatePolicy.Reload && operation == UpdateOperation.Update)
        {
            refresh(extra = extra)
        }
        else if (operation == UpdateOperation.Clear)
        {
            clear()
        }
        else
        {
            this.entities.toList().forEach {
                val entity = entities[it.id]
                if (entity != null)
                {
                    when (operation)
                    {
                        UpdateOperation.Update -> setEntity(entity = entity)
                        UpdateOperation.Delete -> remove(id = entity.id)
                        else -> {}
                    }
                }
            }
        }
    }

    override fun update(entities: Map<Id, E>, operations: Map<Id, UpdateOperation>)
    {
        if (operations.values.contains(UpdateOperation.Insert) ||
            updatePolicy == UpdatePolicy.Reload && operations.values.contains(UpdateOperation.Update)
        )
        {
            refresh(extra = extra)
        }
        else
        {
            this.entities.toList().forEach {
                val entity = entities[it.id]
                val operation = operations[it.id]
                if (entity != null && operation != null)
                {
                    when (operation)
                    {
                        UpdateOperation.Update -> setEntity(entity = entity)
                        UpdateOperation.Delete -> remove(id = entity.id)
                        UpdateOperation.Clear -> clear()
                        else -> {}
                    }
                }
            }
        }
    }

    override fun delete(ids: Set<Id>)
    {
        entities.toList().forEach {
            if (ids.contains(it.id))
            {
                remove(id = it.id)
            }
        }
    }

    override fun clear()
    {
        setEntities(entities = listOf())
    }

    fun setEntity(entity: E)
    {
        val index = _entities.indexOfFirst { it.id == entity.id }
        if (index != -1)
        {
            _entities[index] = entity
            publish()
        }
    }

    fun setEntities(entities: List<E>)
    {
        _entities = entities.toMutableList()
        publish()
    }

    open fun add(entity: E)
    {
        _entities.appendNotExistEntity(entity = entity)
        publish()
    }

    open fun remove(entity: E)
    {
        _entities.removeEntity(entity = entity)
        publish()
    }

    open fun remove(id: Id)
    {
        _entities.removeEntityById(id = id)
        publish()
    }

    open fun refresh(resetCache: Boolean = false, extra: Extra? = null)
    {
    }

    open suspend fun refreshNow(resetCache: Boolean = false, extra: Extra? = null)
    {
        this.extra = extra ?: this.extra
        page = -1
        if (perPage != ARRAY_PER_PAGE || resetCache)
        {
            setEntities(entities = listOf())
        }
    }

    override suspend fun collect(collector: FlowCollector<List<E>>)
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

    protected fun publish()
    {
        data.value = _entities.toMutableList()
    }
}

typealias ArrayFlow<Id, Entity> = ArrayFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias ArrayFlowExtraInt<Entity, Extra> = ArrayFlowExtra<Int, Entity, Extra>
typealias ArrayFlowInt<Entity> = ArrayFlow<Int, Entity>

typealias ArrayFlowExtraLong<Entity, Extra> = ArrayFlowExtra<Long, Entity, Extra>
typealias ArrayFlowLong<Entity> = ArrayFlow<Long, Entity>

typealias ArrayFlowExtraString<Entity, Extra> = ArrayFlowExtra<String, Entity, Extra>
typealias ArrayFlowString<Entity> = ArrayFlow<String, Entity>
