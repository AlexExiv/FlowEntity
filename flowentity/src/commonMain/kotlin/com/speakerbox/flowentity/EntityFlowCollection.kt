package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

typealias CombineMethod<E> = suspend (E, List<Any?>) -> Pair<E, Boolean>

data class CombineSource<E: Entity<*>>(
    val sources: List<Flow<Any?>>,
    val combine: CombineMethod<E>
)

open class EntityFlowCollectionExtra<Id: Any, E: Entity<Id>, CollectionExtra>(
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    collectionExtra: CollectionExtra? = null
)
{
    protected val scope = CoroutineScope(SupervisorJob() + dispatcher)
    protected val items = mutableListOf<EntityFlow<Id, E, *>>()
    protected val _sharedEntities = mutableMapOf<Id, E>()
    protected val combineSources = mutableListOf<CombineSource<E>>()

    private var combineJob: Job? = null

    var collectionExtra: CollectionExtra? = collectionExtra
        protected set

    val sharedEntities: Map<Id, E> get() = _sharedEntities

    var singleFetchCallback: SingleFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null
    var arrayFetchCallback: ArrayFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null
    var allArrayFetchCallback: PageFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>? = null

    internal fun add(flow: EntityFlow<Id, E, *>)
    {
        items.add(flow)
    }

    internal fun remove(flow: EntityFlow<Id, E, *>)
    {
        items.removeAll { it === flow }
    }

    open suspend fun requestForCombine(source: String, entity: E, updateChilds: Boolean = true): E
    {
        var result = entity
        combineSources.forEach {
            result = it.combine(result, combineValues(it.sources).first()).first
        }

        if (updateChilds)
        {
            update(source = source, entity = result)
        }

        return result
    }

    open suspend fun requestForCombine(source: String, entities: List<E>, updateChilds: Boolean = true): List<E>
    {
        var result = entities
        combineSources.forEach { combineSource ->
            val values = combineValues(combineSource.sources).first()
            result = result.map { combineSource.combine(it, values).first }
        }

        if (updateChilds)
            update(source = source, entities = result)

        return result
    }

    open fun update(source: String = "", entity: E)
    {
        _sharedEntities[entity.id] = entity
        items.toList().forEach { it.update(source = source, entity = entity) }
    }

    open fun update(source: String = "", entities: List<E>)
    {
        val map = entities.associateBy {
            _sharedEntities[it.id] = it
            it.id
        }
        items.toList().forEach { it.update(source = source, entities = map) }
    }

    open fun commit(entity: E, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByIds(ids = setOf(entity.id))
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                _sharedEntities[entity.id] = entity
                items.toList().forEach { it.update(entity = entity, operation = operation) }
            }
        }
    }

    open fun commitById(id: Id, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByIds(ids = setOf(id))
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                val fetch = requireNotNull(singleFetchCallback) {
                    "To commit by id you must specify singleFetchCallback before"
                }

                scope.launch {
                    val params = SingleParams<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>(id = id)
                    val entity = fetch(params)
                    if (entity != null)
                    {
                        commit(entity = requestForCombine(source = "", entity = entity, updateChilds = false), operation = operation)
                    }
                }
            }
        }
    }

    open fun commitById(id: Id, changes: (E) -> E)
    {
        val entity = _sharedEntities[id]
        if (entity != null)
        {
            val new = changes(entity)
            _sharedEntities[id] = new
            items.toList().forEach { it.update(entity = new, operation = UpdateOperation.Update) }
        }
    }

    open fun commit(entities: List<E>, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByIds(ids = entities.map { it.id }.toSet())
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                val forUpdate = mutableMapOf<Id, E>()
                entities.forEach {
                    forUpdate[it.id] = it
                    _sharedEntities[it.id] = it
                }

                items.toList().forEach { it.update(entities = forUpdate, operation = operation) }
            }
        }
    }

    open fun commit(entities: List<E>, operations: List<UpdateOperation>)
    {
        if (operations.contains(UpdateOperation.Clear))
            commitClear()

        val deleteIds = entities
            .filterIndexed { index, _ -> operations.getOrNull(index) == UpdateOperation.Delete }
            .map { it.id }
            .toSet()
        val otherEntities = entities.filterIndexed { index, _ -> operations.getOrNull(index) != UpdateOperation.Delete }
        val otherOperations = operations.filter { it != UpdateOperation.Delete }

        commitDeleteByIds(ids = deleteIds)

        val forUpdate = mutableMapOf<Id, E>()
        val operationUpdate = mutableMapOf<Id, UpdateOperation>()
        otherEntities.forEachIndexed { index, entity ->
            forUpdate[entity.id] = entity
            operationUpdate[entity.id] = otherOperations.getOrElse(index) { UpdateOperation.None }
            _sharedEntities[entity.id] = entity
        }

        items.toList().forEach { it.update(entities = forUpdate, operations = operationUpdate) }
    }

    open fun commitByIds(ids: List<Id>, operation: UpdateOperation)
    {
        when (operation)
        {
            UpdateOperation.Delete -> commitDeleteByIds(ids = ids.toSet())
            UpdateOperation.Clear -> commitClear()
            else ->
            {
                val fetch = requireNotNull(arrayFetchCallback) {
                    "To commit by ids you must specify arrayFetchCallback before"
                }

                scope.launch {
                    val params = KeyParams<Id, EntityCollectionExtraParamsEmpty, CollectionExtra>(ids = ids)
                    val fetched = fetch(params)
                    val entities = requestForCombine(source = "", entities = fetched, updateChilds = false)
                    commit(entities = entities, operation = operation)
                }
            }
        }
    }

    open fun commitByIds(ids: List<Id>, operations: List<UpdateOperation>)
    {
        if (operations.contains(UpdateOperation.Clear))
            commitClear()

        val deleteIds = ids
            .filterIndexed { index, _ -> operations.getOrNull(index) == UpdateOperation.Delete }
            .toSet()
        val otherIds = ids.filterIndexed { index, _ -> operations.getOrNull(index) != UpdateOperation.Delete }
        val otherOperations = operations.filter { it != UpdateOperation.Delete }

        commitDeleteByIds(ids = deleteIds)

        val fetch = requireNotNull(arrayFetchCallback) {
            "To commit by ids you must specify arrayFetchCallback before"
        }

        scope.launch {
            val params = KeyParams<Id, EntityCollectionExtraParamsEmpty, CollectionExtra>(ids = otherIds)
            val fetched = fetch(params)
            val entities = requestForCombine(source = "", entities = fetched, updateChilds = false)
            commit(entities = entities, operations = otherOperations)
        }
    }

    open fun commitByIds(ids: List<Id>, changes: (E) -> E)
    {
        val forUpdate = mutableMapOf<Id, E>()
        ids.forEach {
            val entity = _sharedEntities[it]
            if (entity != null)
            {
                val new = changes(entity)
                _sharedEntities[it] = new
                forUpdate[it] = new
            }
        }

        items.toList().forEach { it.update(entities = forUpdate, operation = UpdateOperation.Update) }
    }

    open fun commitDeleteByIds(ids: Set<Id>)
    {
        ids.forEach { _sharedEntities.remove(it) }
        items.toList().forEach { it.delete(ids = ids) }
    }

    open fun commitClear()
    {
        _sharedEntities.clear()
        items.toList().forEach { it.clear() }
    }

    fun refresh(resetCache: Boolean = false, collectionExtra: CollectionExtra? = null)
    {
        scope.launch {
            refreshNow(resetCache = resetCache, collectionExtra = collectionExtra)
        }
    }

    suspend fun refreshNow(resetCache: Boolean = false, collectionExtra: CollectionExtra? = null)
    {
        this.collectionExtra = collectionExtra ?: this.collectionExtra
        items.toList().forEach { it.refreshData(resetCache = resetCache, data = this.collectionExtra) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> combineLatest(source: Flow<T>, merge: suspend (E, T) -> Pair<E, Boolean>)
    {
        val combineSource: CombineSource<E> = CombineSource(
            sources = listOf(source),
            combine = { entity, values -> merge(entity, values[0] as T) }
        )

        combineSources.add(combineSource)
        buildCombines()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T0: Any, T1: Any> combineLatest(
        source0: Flow<T0>,
        source1: Flow<T1>,
        merge: suspend (E, T0, T1) -> Pair<E, Boolean>
    )
    {
        val combineSource: CombineSource<E> = CombineSource(
            sources = listOf(source0, source1),
            combine = { entity, values -> merge(entity, values[0] as T0, values[1] as T1) }
        )

        combineSources.add(combineSource)
        buildCombines()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T0: Any, T1: Any, T2: Any> combineLatest(
        source0: Flow<T0>,
        source1: Flow<T1>,
        source2: Flow<T2>,
        merge: suspend (E, T0, T1, T2) -> Pair<E, Boolean>
    )
    {
        val combineSource: CombineSource<E> = CombineSource(
            sources = listOf(source0, source1, source2),
            combine = { entity, values -> merge(entity, values[0] as T0, values[1] as T1, values[2] as T2) }
        )

        combineSources.add(combineSource)
        buildCombines()
    }

    fun createSingle(initial: E, refresh: Boolean = false): SingleFlow<Id, E>
    {
        val fetch = requireNotNull(singleFetchCallback) {
            "To create SingleFlow with initial value you must specify singleFetchCallback before"
        }

        return SingleFlowCollectionExtra(
            holder = this,
            collectionExtra = collectionExtra,
            initial = initial,
            refresh = refresh,
            fetch = fetch
        )
    }

    fun createSingle(id: Id? = null, start: Boolean = true, refresh: Boolean = false): SingleFlow<Id, E>
    {
        val fetch = requireNotNull(singleFetchCallback) {
            "To create SingleFlow with default fetch method you must specify singleFetchCallback before"
        }
        val entity = if (id == null) null else _sharedEntities[id]
        return if (entity == null)
            createSingle(id = id, start = start, fetch = fetch)
        else
            createSingle(initial = entity, refresh = refresh)
    }

    fun createSingle(
        id: Id? = null,
        start: Boolean = true,
        fetch: SingleFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): SingleFlow<Id, E>
    {
        return SingleFlowCollectionExtra(
            holder = this,
            id = id,
            collectionExtra = collectionExtra,
            start = start,
            fetch = fetch
        )
    }

    fun <Extra> createSingleExtra(
        id: Id? = null,
        extra: Extra? = null,
        start: Boolean = true,
        fetch: SingleFetchCallback<Id, E, Extra, CollectionExtra>
    ): SingleFlowExtra<Id, E, Extra>
    {
        return SingleFlowCollectionExtra(
            holder = this,
            id = id,
            extra = extra,
            collectionExtra = collectionExtra,
            start = start,
            fetch = fetch
        )
    }

    fun createKeyArray(initial: List<E>): ArrayKeyFlow<Id, E>
    {
        val fetch = requireNotNull(arrayFetchCallback) {
            "To create ArrayKeyFlow with initial values you must specify arrayFetchCallback before"
        }

        return ArrayKeyFlowCollectionExtra(
            holder = this,
            initial = initial,
            collectionExtra = collectionExtra,
            fetch = fetch
        )
    }

    fun createKeyArray(ids: List<Id>, start: Boolean = true): ArrayKeyFlow<Id, E>
    {
        val fetch = requireNotNull(arrayFetchCallback) {
            "To create ArrayKeyFlow with default fetch method you must specify arrayFetchCallback before"
        }

        return createKeyArray(ids = ids, start = start, fetch = fetch)
    }

    fun createKeyArray(
        ids: List<Id> = listOf(),
        start: Boolean = true,
        fetch: ArrayFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): ArrayKeyFlow<Id, E>
    {
        return ArrayKeyFlowCollectionExtra(
            holder = this,
            ids = ids,
            collectionExtra = collectionExtra,
            start = start,
            fetch = fetch
        )
    }

    fun <Extra> createKeyArrayExtra(
        ids: List<Id> = listOf(),
        extra: Extra? = null,
        start: Boolean = true,
        fetch: ArrayFetchCallback<Id, E, Extra, CollectionExtra>
    ): ArrayKeyFlowExtra<Id, E, Extra>
    {
        return ArrayKeyFlowCollectionExtra(
            holder = this,
            ids = ids,
            extra = extra,
            collectionExtra = collectionExtra,
            start = start,
            fetch = fetch
        )
    }

    fun createArray(start: Boolean = true): ArrayFlow<Id, E>
    {
        val fetch = requireNotNull(allArrayFetchCallback) {
            "To create ArrayFlow with default fetch method you must specify allArrayFetchCallback before"
        }

        return createArray(start = start, fetch = fetch)
    }

    fun createArray(
        start: Boolean = true,
        fetch: PageFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): ArrayFlow<Id, E>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            collectionExtra = collectionExtra,
            start = start,
            fetch = fetch
        )
    }

    fun <Extra> createArrayExtra(
        extra: Extra? = null,
        start: Boolean = true,
        fetch: PageFetchCallback<Id, E, Extra, CollectionExtra>
    ): ArrayFlowExtra<Id, E, Extra>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            extra = extra,
            collectionExtra = collectionExtra,
            start = start,
            fetch = fetch
        )
    }

    fun createPaginator(
        perPage: Int = 35,
        start: Boolean = true,
        fetch: PageFetchCallback<Id, E, EntityCollectionExtraParamsEmpty, CollectionExtra>
    ): PaginatorFlow<Id, E>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            collectionExtra = collectionExtra,
            perPage = perPage,
            start = start,
            fetch = fetch
        )
    }

    fun <Extra> createPaginatorExtra(
        extra: Extra? = null,
        perPage: Int = 35,
        start: Boolean = true,
        fetch: PageFetchCallback<Id, E, Extra, CollectionExtra>
    ): PaginatorFlowExtra<Id, E, Extra>
    {
        return PaginatorFlowCollectionExtra(
            holder = this,
            extra = extra,
            collectionExtra = collectionExtra,
            perPage = perPage,
            start = start,
            fetch = fetch
        )
    }

    operator fun get(id: Id): E? = sharedEntities[id]

    fun close()
    {
        items.toList().forEach { it.dispose() }
        scope.cancel()
    }

    private fun buildCombines()
    {
        combineJob?.cancel()
        if (combineSources.isEmpty())
            return

        val flows = combineSources.map { it.asFlow() }
        combineJob = scope.launch {
            val combined = if (flows.size == 1)
                flows[0].map { listOf(it) }
            else
                combine(flows) { it.toList() }

            combined.collect { applyCombines(combines = it) }
        }
    }

    private suspend fun applyCombines(combines: List<Pair<CombineMethod<E>, List<Any?>>>)
    {
        val toUpdate = mutableMapOf<Id, E>()
        _sharedEntities.keys.toList().forEach {
            var entity = _sharedEntities[it] ?: return@forEach
            var updated = false

            combines.forEach {
                val result = it.first(entity, it.second)
                entity = result.first
                updated = updated || result.second
            }

            if (updated)
            {
                _sharedEntities[it] = entity
                toUpdate[it] = entity
            }
        }

        if (toUpdate.isNotEmpty())
        {
            items.toList().forEach { it.update(source = "", entities = toUpdate) }
        }
    }

    private fun CombineSource<E>.asFlow(): Flow<Pair<CombineMethod<E>, List<Any?>>>
    {
        return combineValues(sources).map { Pair(combine, it) }
    }

    private fun combineValues(sources: List<Flow<Any?>>): Flow<List<Any?>>
    {
        return when (sources.size)
        {
            0 -> flowOf(listOf())
            1 -> sources[0].map { listOf(it) }
            else -> combine(sources) { it.toList() }
        }
    }

    companion object
    {
        fun <Id: Any, E: Entity<Id>, CollectionExtra> create(
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            collectionExtra: CollectionExtra? = null
        ) = EntityFlowCollectionExtra<Id, E, CollectionExtra>(dispatcher, collectionExtra)

        fun <Id: Any, E: Entity<Id>> create(
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ) = EntityFlowCollectionExtra<Id, E, EntityCollectionExtraParamsEmpty>(
            dispatcher,
            EntityCollectionExtraParamsEmpty()
        )

        fun <E: Entity<String>, CollectionExtra> createString(
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            collectionExtra: CollectionExtra? = null
        ) = EntityFlowCollectionExtra<String, E, CollectionExtra>(dispatcher, collectionExtra)

        fun <E: Entity<String>> createString(
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ) = EntityFlowCollectionExtra<String, E, EntityCollectionExtraParamsEmpty>(
            dispatcher,
            EntityCollectionExtraParamsEmpty()
        )

        fun <E: Entity<Long>, CollectionExtra> createLong(
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            collectionExtra: CollectionExtra? = null
        ) = EntityFlowCollectionExtra<Long, E, CollectionExtra>(dispatcher, collectionExtra)

        fun <E: Entity<Long>> createLong(
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ) = EntityFlowCollectionExtra<Long, E, EntityCollectionExtraParamsEmpty>(
            dispatcher,
            EntityCollectionExtraParamsEmpty()
        )

        fun <E: Entity<Int>, CollectionExtra> createInt(
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            collectionExtra: CollectionExtra? = null
        ) = EntityFlowCollectionExtra<Int, E, CollectionExtra>(dispatcher, collectionExtra)

        fun <E: Entity<Int>> createInt(
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ) = EntityFlowCollectionExtra<Int, E, EntityCollectionExtraParamsEmpty>(
            dispatcher,
            EntityCollectionExtraParamsEmpty()
        )

        fun <Id: Any, E: Entity<Id>, EB: EntityBack<Id>, CollectionExtra> createBack(
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            collectionExtra: CollectionExtra? = null,
            map: (EB) -> E
        ) = EntityFlowCollectionExtraBack<Id, E, EB, CollectionExtra>(
            dispatcher = dispatcher,
            collectionExtra = collectionExtra,
            map = map
        )
    }
}

typealias EntityFlowCollection<Id, Entity> =
    EntityFlowCollectionExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias EntityFlowCollectionExtraInt<Entity, CollectionExtra> =
    EntityFlowCollectionExtra<Int, Entity, CollectionExtra>
typealias EntityFlowCollectionInt<Entity> = EntityFlowCollection<Int, Entity>

typealias EntityFlowCollectionExtraLong<Entity, CollectionExtra> =
    EntityFlowCollectionExtra<Long, Entity, CollectionExtra>
typealias EntityFlowCollectionLong<Entity> = EntityFlowCollection<Long, Entity>

typealias EntityFlowCollectionExtraString<Entity, CollectionExtra> =
    EntityFlowCollectionExtra<String, Entity, CollectionExtra>
typealias EntityFlowCollectionString<Entity> = EntityFlowCollection<String, Entity>
