package com.speakerbox.flowentity.cache

import com.speakerbox.flowentity.EntityRepository
import com.speakerbox.flowentity.EntityUpdated
import com.speakerbox.flowentity.TestEntityBack
import com.speakerbox.flowentity.UpdateOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class TestSource(
    private val entities: List<TestEntityBack>
) : EntityRepository<Int, TestEntityBack>(), RepositoryCacheAllSourceInterface<Int, TestEntityBack>
{
    var fetchAllCalls = 0
    var requestedIds = listOf<Int>()
    var failuresRemaining = 0
    var fetchStarted: CompletableDeferred<Unit>? = null
    var waitForCancellation = false

    override suspend fun fetchAll(): List<TestEntityBack>
    {
        fetchAllCalls++
        fetchStarted?.complete(Unit)

        if (waitForCancellation)
            awaitCancellation()

        if (failuresRemaining > 0)
        {
            failuresRemaining--
            throw IllegalStateException("test failure")
        }

        return entities
    }

    override suspend fun get(id: Int): TestEntityBack? =
        entities.firstOrNull { it.id == id }

    override suspend fun get(ids: List<Int>): List<TestEntityBack>
    {
        requestedIds = ids
        return entities.filter { it.id in ids }
    }
}

private class TestStorage(
    initialEntities: List<TestEntityBack>
) : EntityRepository<Int, TestEntityBack>(), RepositoryCacheAllStorageInterface<Int, TestEntityBack>
{
    private val entities = initialEntities.toMutableList()

    override suspend fun fetchAll(): List<TestEntityBack> = entities.toList()

    override suspend fun get(id: Int): TestEntityBack? =
        entities.firstOrNull { it.id == id }

    override suspend fun get(ids: List<Int>): List<TestEntityBack> =
        entities.filter { it.id in ids }

    override suspend fun save(entities: List<TestEntityBack>): List<TestEntityBack>
    {
        upsert(entities)
        return entities
    }

    override suspend fun rewriteAll(entities: List<TestEntityBack>): List<TestEntityBack>
    {
        this.entities.clear()
        this.entities.addAll(entities)
        return entities
    }

    fun emitUpdate(entity: TestEntityBack)
    {
        tryEmitUpdated(
            listOf(
                EntityUpdated(
                    id = entity.id,
                    entity = entity,
                    operation = UpdateOperation.Update
                )
            )
        )
    }

    private fun upsert(newEntities: List<TestEntityBack>)
    {
        newEntities.forEach { newEntity ->
            val index = entities.indexOfFirst { it.id == newEntity.id }
            if (index == -1)
                entities.add(newEntity)
            else
                entities[index] = newEntity
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryCacheAllCoordinatorSimpleTest
{
    private val entityOne = TestEntityBack(id = 1, value = "one")
    private val entityTwo = TestEntityBack(id = 2, value = "two")

    @Test
    fun emptyCacheFetchAllLoadsImmediately()
    {
        runTest {
            val source = TestSource(listOf(entityOne, entityTwo))
            val storage = TestStorage(emptyList())
            val coordinator = coordinator(source, storage)

            assertEquals(listOf(entityOne, entityTwo), coordinator.fetchAll())
            assertEquals(1, source.fetchAllCalls)
            assertEquals(RepositoryCacheAllCoordinatorSimple.State.Updated, coordinator.updateState)

            coordinator.close()
        }
    }

    @Test
    fun cachedFetchAllReturnsImmediatelyAndSchedulesDelayedUpdate()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne, entityTwo))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 1_000
            )

            assertEquals(listOf(entityOne), coordinator.fetchAll())
            assertEquals(0, source.fetchAllCalls)

            advanceTimeBy(1_000)
            advanceUntilIdle()

            assertEquals(1, source.fetchAllCalls)
            coordinator.close()
        }
    }

    @Test
    fun singleUpdatePreventsAnotherBackgroundUpdateUntilReset()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 0,
                singleUpdate = true
            )

            coordinator.get(entityOne.id)
            advanceUntilIdle()

            assertEquals(1, source.fetchAllCalls)
            assertEquals(RepositoryCacheAllCoordinatorSimple.State.Updated, coordinator.updateState)

            coordinator.get(entityOne.id)
            advanceUntilIdle()
            assertEquals(1, source.fetchAllCalls)

            coordinator.resetUpdate()
            coordinator.get(entityOne.id)
            advanceUntilIdle()

            assertEquals(2, source.fetchAllCalls)
            coordinator.close()
        }
    }

    @Test
    fun nonSingleUpdateCanBeScheduledAgainAfterSuccess()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 0
            )

            coordinator.get(entityOne.id)
            advanceUntilIdle()
            coordinator.get(entityOne.id)
            advanceUntilIdle()

            assertEquals(2, source.fetchAllCalls)
            assertEquals(RepositoryCacheAllCoordinatorSimple.State.Wait, coordinator.updateState)
            coordinator.close()
        }
    }

    @Test
    fun resetUpdateCancelsDelayedUpdate()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 1_000
            )

            coordinator.get(entityOne.id)
            coordinator.resetUpdate()
            advanceTimeBy(2_000)
            advanceUntilIdle()

            assertEquals(0, source.fetchAllCalls)
            assertEquals(RepositoryCacheAllCoordinatorSimple.State.Wait, coordinator.updateState)
            coordinator.close()
        }
    }

    @Test
    fun updateRetriesAfterFailure()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne)).apply {
                failuresRemaining = 2
            }
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 0,
                retryDelayMillis = 100
            )

            coordinator.get(entityOne.id)
            advanceUntilIdle()

            assertEquals(3, source.fetchAllCalls)
            assertEquals(RepositoryCacheAllCoordinatorSimple.State.Wait, coordinator.updateState)
            coordinator.close()
        }
    }

    @Test
    fun parallelGetsScheduleOnlyOneUpdate()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 0
            )

            val first = async { coordinator.get(entityOne.id) }
            val second = async { coordinator.get(entityOne.id) }

            advanceUntilIdle()
            first.await()
            second.await()

            assertEquals(1, source.fetchAllCalls)
            coordinator.close()
        }
    }

    @Test
    fun getLoadsOnlyMissingIds()
    {
        runTest {
            val source = TestSource(listOf(entityTwo))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(source, storage)

            assertEquals(
                listOf(entityOne, entityTwo),
                coordinator.get(listOf(entityOne.id, entityTwo.id))
            )
            assertEquals(listOf(entityTwo.id), source.requestedIds)
            coordinator.close()
        }
    }

    @Test
    fun storageUpdatesAreForwarded()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher
            )
            val received = async { coordinator.updates.first() }

            runCurrent()
            storage.emitUpdate(entityOne)

            assertEquals(entityOne.id, received.await().single().id)
            coordinator.close()
        }
    }

    @Test
    fun cancellationDoesNotCompleteUpdate()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne)).apply {
                fetchStarted = CompletableDeferred()
                waitForCancellation = true
            }
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 0
            )

            coordinator.get(entityOne.id)
            runCurrent()
            source.fetchStarted!!.await()

            coordinator.resetUpdate()
            advanceUntilIdle()

            assertEquals(RepositoryCacheAllCoordinatorSimple.State.Wait, coordinator.updateState)
            coordinator.close()
        }
    }

    @Test
    fun closeCancelsUpdateScope()
    {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val source = TestSource(listOf(entityOne))
            val storage = TestStorage(listOf(entityOne))
            val coordinator = coordinator(
                source = source,
                storage = storage,
                dispatcher = dispatcher,
                updateDelayMillis = 1_000
            )

            coordinator.get(entityOne.id)
            coordinator.close()
            advanceTimeBy(2_000)
            advanceUntilIdle()

            assertEquals(0, source.fetchAllCalls)
        }
    }

    private fun coordinator(
        source: TestSource,
        storage: TestStorage,
        dispatcher: CoroutineDispatcher? = null,
        updateDelayMillis: Long = 1_000,
        retryDelayMillis: Long = 20_000,
        singleUpdate: Boolean = false
    ): RepositoryCacheAllCoordinatorSimple<Int, TestEntityBack>
    {
        return RepositoryCacheAllCoordinatorSimple(
            dispatcher = dispatcher ?: StandardTestDispatcher(),
            source = source,
            storage = storage,
            updateDelayMillis = updateDelayMillis,
            singleUpdate = singleUpdate,
            retryDelayMillis = retryDelayMillis
        )
    }
}
