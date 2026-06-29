package com.speakerbox.flowentity

import kotlinx.coroutines.launch

open class ArrayKeyFlowExtra<Id: Any, E: Entity<Id>, Extra>(
    holder: EntityFlowCollectionExtra<Id, E, *>,
    ids: List<Id>,
    extra: Extra? = null
) : ArrayFlowExtra<Id, E, Extra>(holder, extra = extra)
{
    protected open var _ids = ids.toMutableList()

    open var ids: List<Id>
        get() = _ids
        set(value)
        {
            _ids = value.toMutableList()
        }

    override fun update(entities: Map<Id, E>, operation: UpdateOperation)
    {
        this.entities.toList().forEach {
            val entity = entities[it.id]
            if (entity != null)
            {
                apply(entity = entity, operation = operation)
            }
        }
    }

    override fun update(entities: Map<Id, E>, operations: Map<Id, UpdateOperation>)
    {
        this.entities.toList().forEach {
            val entity = entities[it.id]
            val operation = operations[it.id]
            if (entity != null && operation != null)
            {
                apply(entity = entity, operation = operation)
            }
        }
    }

    protected fun apply(entity: E, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.None,
            UpdateOperation.Insert,
            UpdateOperation.Update -> setEntity(entity = entity)
            UpdateOperation.Delete -> remove(id = entity.id)
            UpdateOperation.Clear -> clear()
        }
    }

    fun add(id: Id)
    {
        _ids.appendNotExistId(id)
    }

    override fun add(entity: E)
    {
        super.add(entity = entity)
        _ids.appendNotExistId(entity.id)
    }

    override fun remove(entity: E)
    {
        super.remove(entity = entity)
        _ids.remove(entity.id)
    }

    override fun remove(id: Id)
    {
        super.remove(id = id)
        _ids.remove(id)
    }

    override fun clear()
    {
        super.clear()
        _ids = mutableListOf()
    }
}

class ArrayKeyFlowCollectionExtra<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
    ids: List<Id>,
    start: Boolean = true,
    extra: Extra? = null,
    private var collectionExtra: CollectionExtra? = null,
    private val fetch: ArrayFetchCallback<Id, E, Extra, CollectionExtra>
) : ArrayKeyFlowExtra<Id, E, Extra>(holder, ids, extra)
{
    override var ids: List<Id>
        get() = super.ids
        set(value)
        {
            super.ids = value
            val params = KeyParams(
                ids = value,
                extra = extra,
                collectionExtra = collectionExtra
            )
            request(params)
        }

    constructor(
        holder: EntityFlowCollectionExtra<Id, E, CollectionExtra>,
        initial: List<E>,
        collectionExtra: CollectionExtra? = null,
        fetch: ArrayFetchCallback<Id, E, Extra, CollectionExtra>
    ) : this(
        holder = holder,
        ids = initial.map { it.id },
        start = false,
        collectionExtra = collectionExtra,
        fetch = fetch
    )
    {
        scope.launch {
            setEntities(entities = holder.requestForCombine(source = uuid, entities = initial))
        }
    }

    init
    {
        updateLoading(if (start) Loading.FirstLoading else Loading.None)

        if (start)
        {
            val params = KeyParams(
                first = true,
                ids = ids,
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
        val params = KeyParams(
            refreshing = true,
            resetCache = resetCache,
            ids = ids,
            extra = this.extra,
            collectionExtra = collectionExtra
        )
        request(params)
    }

    override fun refreshData(resetCache: Boolean, data: Any?)
    {
        @Suppress("UNCHECKED_CAST")
        collectionExtra = data as? CollectionExtra ?: collectionExtra
        refresh(resetCache = resetCache, extra = extra)
    }

    private fun request(params: KeyParams<Id, Extra, CollectionExtra>)
    {
        if (params.ids.isEmpty())
        {
            updateLoading(Loading.None)
            setEntities(entities = listOf())
            return
        }

        updateLoading(if (params.first) Loading.FirstLoading else Loading.Loading, null)

        scope.launch {
            val value = try
            {
                fetchElements(params = params)
            }
            catch (throwable: Throwable)
            {
                updateLoading(Loading.None, throwable)
                listOf()
            }

            val combined = holder.requestForCombine(source = uuid, entities = value)
            updateLoading(Loading.None)
            setEntities(entities = combined)
        }
    }

    private suspend fun fetchElements(params: KeyParams<Id, Extra, CollectionExtra>): List<E>
    {
        if (params.refreshing)
        {
            return fetch(params)
        }

        val existing = params.ids.mapNotNull { id ->
            holder.sharedEntities[id] ?: entities.firstOrNull { it.id == id }
        }

        if (existing.size == params.ids.size)
        {
            return existing
        }

        val missingIds = params.ids.filter { id ->
            holder.sharedEntities[id] == null && entities.firstOrNull { it.id == id } == null
        }
        val fetched = fetch(params.copy(ids = missingIds))
        val existingMap = existing.toEntitiesMap()
        val fetchedMap = fetched.toEntitiesMap()

        return params.ids.mapNotNull { existingMap[it] ?: fetchedMap[it] }
    }
}

typealias ArrayKeyFlow<Id, Entity> = ArrayKeyFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias ArrayKeyFlowExtraInt<Entity, Extra> = ArrayKeyFlowExtra<Int, Entity, Extra>
typealias ArrayKeyFlowInt<Entity> = ArrayKeyFlow<Int, Entity>

typealias ArrayKeyFlowExtraLong<Entity, Extra> = ArrayKeyFlowExtra<Long, Entity, Extra>
typealias ArrayKeyFlowLong<Entity> = ArrayKeyFlow<Long, Entity>

typealias ArrayKeyFlowExtraString<Entity, Extra> = ArrayKeyFlowExtra<String, Entity, Extra>
typealias ArrayKeyFlowString<Entity> = ArrayKeyFlow<String, Entity>
