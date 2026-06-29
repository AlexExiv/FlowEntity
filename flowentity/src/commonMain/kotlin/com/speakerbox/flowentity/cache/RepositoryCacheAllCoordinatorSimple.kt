package com.speakerbox.flowentity.cache

import com.speakerbox.flowentity.EntityAllRepositoryInterface
import com.speakerbox.flowentity.EntityBack
import com.speakerbox.flowentity.EntityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RepositoryCacheAllCoordinatorSimple<Id: Any, EB: EntityBack<Id>>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val source: RepositoryCacheAllSourceInterface<Id, EB>,
    private val storage: RepositoryCacheAllStorageInterface<Id, EB>,
    private val updateDelayMillis: Long = 1000,
    private val singleUpdate: Boolean = false
) : EntityRepository<Id, EB>(), EntityAllRepositoryInterface<Id, EB>
{
    enum class State
    {
        Wait,
        Updating,
        Updated
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var updateJob: Job? = null

    var updateState = State.Wait
        private set

    init
    {
        scope.launch {
            storage.updates.collect { emitUpdated(it) }
        }
    }

    override suspend fun fetchAll(): List<EB>
    {
        val entities = storage.fetchAll()
        return if (entities.isEmpty())
        {
            updateNow()
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
        return if (entities.isEmpty() && ids.isNotEmpty())
        {
            getFromSource(ids = ids)
        }
        else
        {
            update()
            entities
        }
    }

    fun resetUpdate()
    {
        updateState = State.Wait
    }

    private suspend fun updateNow(): List<EB>
    {
        val entities = source.fetchAll()
        val saved = storage.rewriteAll(entities = entities)
        updateState = State.Updated
        return saved
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

    private fun update()
    {
        if (updateState != State.Wait)
        {
            return
        }

        updateState = State.Updating
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(updateDelayMillis)
            runCatching { updateNow() }
            updateState = if (singleUpdate) State.Updated else State.Wait
        }
    }
}
