package com.speakerbox.flowentity.cache

import com.speakerbox.flowentity.EntityAllRepositoryInterface
import com.speakerbox.flowentity.EntityBack
import com.speakerbox.flowentity.EntityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RepositoryCacheAllCoordinatorSimple<Id: Any, EB: EntityBack<Id>>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val source: RepositoryCacheAllSourceInterface<Id, EB>,
    private val storage: RepositoryCacheAllStorageInterface<Id, EB>,
    private val updateDelayMillis: Long = 1000,
    private val singleUpdate: Boolean = false,
    private val retryDelayMillis: Long = 20_000
) : EntityRepository<Id, EB>(), EntityAllRepositoryInterface<Id, EB>
{
    enum class State
    {
        Wait,
        Updating,
        Updated
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val updateScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val updateMutex = Mutex()
    private val updateGeneration = MutableStateFlow(0L)
    private val _updateState = MutableStateFlow(State.Wait)

    val updateState: State
        get() = _updateState.value

    val updateStateFlow: StateFlow<State> = _updateState.asStateFlow()

    init
    {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            storage.updates.collect { emitUpdated(it) }
        }
    }

    override suspend fun fetchAll(): List<EB>
    {
        val entities = storage.fetchAll()
        return if (entities.isEmpty())
        {
            updateImmediately()
        }
        else
        {
            update()
            entities
        }
    }

    override suspend fun get(id: Id): EB?
    {
        val entity = storage.get(id)
        return if (entity == null)
        {
            getFromSource(id = id)
        }
        else
        {
            update()
            entity
        }
    }

    override suspend fun get(ids: List<Id>): List<EB>
    {
        val entities = storage.get(ids)

        if (ids.isEmpty())
        {
            update()
            return entities
        }

        val cachedById = entities.associateBy { it.id }
        val missingIds = ids
            .distinct()
            .filterNot { it in cachedById }

        if (missingIds.isEmpty())
        {
            update()
            return ids.mapNotNull { cachedById[it] }
        }

        val loaded = storage.save(source.get(missingIds))
        val loadedById = loaded.associateBy { it.id }

        return ids.mapNotNull { cachedById[it] ?: loadedById[it] }
    }

    fun resetUpdate()
    {
        updateGeneration.update { it + 1 }
        updateScope.coroutineContext[Job]?.cancelChildren()
        _updateState.value = State.Wait
    }

    fun close()
    {
        updateScope.cancel()
        scope.cancel()
    }

    private suspend fun updateNow(): List<EB>
    {
        val entities = source.fetchAll()
        return storage.rewriteAll(entities = entities)
    }

    private suspend fun updateImmediately(): List<EB>
    {
        return updateMutex.withLock {
            val current = storage.fetchAll()
            if (current.isNotEmpty())
                return@withLock current

            updateGeneration.update { it + 1 }
            val currentGeneration = updateGeneration.value
            updateScope.coroutineContext[Job]?.cancelChildren()
            _updateState.value = State.Updating

            try
            {
                val saved = updateNow()

                if (updateGeneration.value == currentGeneration)
                    _updateState.value = State.Updated

                saved
            }
            catch (e: CancellationException)
            {
                if (updateGeneration.value == currentGeneration)
                    _updateState.value = State.Wait

                throw e
            }
            catch (e: Throwable)
            {
                if (updateGeneration.value == currentGeneration)
                    _updateState.value = State.Wait

                throw e
            }
        }
    }

    private suspend fun getFromSource(id: Id): EB?
    {
        val entity = source.get(id)
        return if (entity == null)
        {
            null
        }
        else
        {
            val entities = listOf(entity)
            storage.save(entities).firstOrNull()
        }
    }

    private suspend fun getFromSource(ids: List<Id>): List<EB>
    {
        return storage.save(source.get(ids))
    }

    private suspend fun update()
    {
        if (updateState != State.Wait || !updateScope.isActive)
            return

        updateMutex.withLock {
            if (_updateState.value != State.Wait || !updateScope.isActive)
                return@withLock

            val currentGeneration = updateGeneration.value
            _updateState.value = State.Updating

            val job = updateScope.launch(start = CoroutineStart.LAZY) {
                runScheduledUpdate(currentGeneration)
            }

            if (updateGeneration.value != currentGeneration)
            {
                job.cancel()
                _updateState.value = State.Wait
                return@withLock
            }

            job.start()
        }
    }

    private suspend fun runScheduledUpdate(currentGeneration: Long)
    {
        delay(updateDelayMillis)

        while (currentCoroutineContext().isActive)
        {
            if (updateGeneration.value != currentGeneration)
                return

            try
            {
                val result = updateMutex.withLock {
                    if (updateGeneration.value != currentGeneration)
                        return@withLock null

                    val saved = updateNow()

                    if (updateGeneration.value == currentGeneration)
                        _updateState.value = if (singleUpdate) State.Updated else State.Wait

                    saved
                }

                if (result == null)
                    return

                return
            }
            catch (e: CancellationException)
            {
                throw e
            }
            catch (_: Throwable)
            {
                delay(retryDelayMillis)
            }
        }
    }
}
