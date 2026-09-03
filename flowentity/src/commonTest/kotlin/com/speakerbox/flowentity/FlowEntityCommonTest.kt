package com.speakerbox.flowentity

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class TestEntity(
    override val id: Int,
    val value: String,
    val indirectId: Int = 0,
    val indirectValue: String = ""
) : EntityInt

data class TestEntityBack(
    override val id: Int,
    val value: String,
    val indirectId: Int = 0,
    val indirectValue: String = ""
) : EntityBackInt

data class ExtraParams(val test: String)
data class ExtraCollectionParams(val test: String)

private class TestRepository : EntityRepository<Int, TestEntityBack>()
{
    val items = mutableListOf<TestEntityBack>()

    fun add(entities: List<TestEntityBack>)
    {
        items.addAll(entities)
        val updates: List<EntityUpdated<Int, TestEntityBack>> =
            entities.map { EntityUpdated(id = it.id, operation = UpdateOperation.Insert) }
        tryEmitUpdated(updates)
    }

    fun update(entity: TestEntityBack)
    {
        val index = items.indexOfFirst { it.id == entity.id }
        if (index == -1)
        {
            items.add(entity)
        }
        else
        {
            items[index] = entity
        }

        val update: EntityUpdated<Int, TestEntityBack> =
            EntityUpdated(id = entity.id, operation = UpdateOperation.Update)
        val updates = listOf(update)
        tryEmitUpdated(updates)
    }

    override suspend fun get(id: Int): TestEntityBack? = items.firstOrNull { it.id == id }

    override suspend fun get(ids: List<Int>): List<TestEntityBack> = items.filter { ids.contains(it.id) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FlowEntityCommonTest
{
    @Test
    fun exposesLibraryName()
    {
        assertEquals("FlowEntity", FlowEntity.name)
    }

    @Test
    fun singleFlowIsCollectableDirectlyAndDisposesAfterLastCollector() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        collection.singleFetchCallback = { TestEntity(id = it.id ?: 0, value = "one") }

        val single = collection.createSingle(id = 1)
        val directFlow: Flow<TestEntity?> = single
        val firstCollectorValues = mutableListOf<TestEntity?>()
        val secondCollectorValues = mutableListOf<TestEntity?>()

        advanceUntilIdle()

        val firstJob = launch { directFlow.collect { firstCollectorValues.add(it) } }
        val secondJob = launch { single.collect { secondCollectorValues.add(it) } }

        advanceUntilIdle()

        assertEquals(2, single.collectors.value)
        val expectedEntity = TestEntity(id = 1, value = "one")
        assertEquals(expectedEntity, firstCollectorValues.last())
        assertEquals(expectedEntity, secondCollectorValues.last())

        firstJob.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(1, single.collectors.value)
        assertFalse(single.disposed)

        secondJob.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(0, single.collectors.value)
        assertTrue(single.disposed)
        var failed = false
        try
        {
            single.collect { }
        }
        catch (_: IllegalStateException)
        {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun singletonFlowCanBeCollectedAgainAfterLastCollector() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        collection.singleFetchCallback = { TestEntity(id = it.id ?: 0, value = "one") }

        val single = collection.createSingle(id = 1)
        single.singleton = true
        val firstValues = mutableListOf<TestEntity?>()
        val firstJob = launch { single.collect { firstValues.add(it) } }

        advanceUntilIdle()
        firstJob.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(0, single.collectors.value)
        assertFalse(single.disposed)

        val secondValues = mutableListOf<TestEntity?>()
        val secondJob = launch { single.collect { secondValues.add(it) } }

        advanceUntilIdle()

        assertEquals(1, single.collectors.value)
        val expectedEntity = TestEntity(id = 1, value = "one")
        assertEquals(expectedEntity, firstValues.last())
        assertEquals(expectedEntity, secondValues.last())

        secondJob.cancelAndJoin()

        val array = collection.createKeyArray(ids = listOf(2)) {
            it.ids.map { TestEntity(id = it, value = "array-$it") }
        }
        array.singleton = true
        val arrayFirstValues = mutableListOf<List<TestEntity>>()
        val arrayFirstJob = launch { array.collect { arrayFirstValues.add(it) } }

        advanceUntilIdle()
        arrayFirstJob.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(0, array.collectors.value)
        assertFalse(array.disposed)

        val arraySecondValues = mutableListOf<List<TestEntity>>()
        val arraySecondJob = launch { array.collect { arraySecondValues.add(it) } }

        advanceUntilIdle()

        assertEquals(1, array.collectors.value)
        val expectedArray = listOf(TestEntity(id = 2, value = "array-2"))
        assertEquals(expectedArray, arrayFirstValues.last())
        assertEquals(expectedArray, arraySecondValues.last())

        arraySecondJob.cancelAndJoin()
    }

    @Test
    fun arrayKeyFlowUsesSharedCacheAndFetchesOnlyMissingIds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        var requestedIds = listOf<Int>()

        val cachedEntity = TestEntity(id = 1, value = "cached")
        collection.update(entity = cachedEntity)

        val ids = listOf(1, 2)
        val array = collection.createKeyArray(ids = ids) {
            requestedIds = it.ids
            it.ids.map { TestEntity(id = it, value = "fetched-$it") }
        }
        val values = mutableListOf<List<TestEntity>>()
        val job = launch { array.collect { values.add(it) } }

        advanceUntilIdle()

        val expectedRequestedIds = listOf(2)
        assertEquals(expectedRequestedIds, requestedIds)
        val expectedEntities = listOf(
            TestEntity(id = 1, value = "cached"),
            TestEntity(id = 2, value = "fetched-2")
        )
        assertEquals(
            expectedEntities,
            values.last()
        )

        job.cancelAndJoin()
    }

    @Test
    fun commitUpdatesSingleFlowAndArrayFlow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        collection.singleFetchCallback = { TestEntity(id = it.id ?: 0, value = "single-${it.id}") }
        collection.arrayFetchCallback = { it.ids.map { TestEntity(id = it, value = "array-$it") } }

        val initialEntity = TestEntity(id = 1, value = "one")
        val initial = listOf(initialEntity)
        val array = collection.createKeyArray(initial = initial)
        val single = collection.createSingle(id = 1)
        val arrayValues = mutableListOf<List<TestEntity>>()
        val singleValues = mutableListOf<TestEntity?>()
        val arrayJob = launch { array.collect { arrayValues.add(it) } }
        val singleJob = launch { single.collect { singleValues.add(it) } }

        advanceUntilIdle()

        val updatedEntity = TestEntity(id = 1, value = "updated")
        collection.commit(updatedEntity, UpdateOperation.Update)
        advanceUntilIdle()

        assertEquals("updated", singleValues.last()?.value)
        assertEquals("updated", arrayValues.last()[0].value)

        arrayJob.cancelAndJoin()
        singleJob.cancelAndJoin()
    }

    @Test
    fun singleFlowUsesLatestRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        val single = collection.createSingle(id = 1) {
            if (it.id == 1)
            {
                delay(1000)
                TestEntity(id = 1, value = "old")
            }
            else
            {
                TestEntity(id = 2, value = "new")
            }
        }
        val values = mutableListOf<TestEntity?>()
        val job = launch { single.collect { values.add(it) } }

        runCurrent()
        single.id = 2
        runCurrent()
        advanceUntilIdle()

        val expectedEntity = TestEntity(id = 2, value = "new")
        assertEquals(expectedEntity, values.last())

        job.cancelAndJoin()
    }

    @Test
    fun arrayKeyFlowUsesLatestRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        val array = collection.createKeyArray(ids = listOf(1)) { params ->
            if (params.ids == listOf(1))
            {
                delay(1000)
                listOf(TestEntity(id = 1, value = "old"))
            }
            else
            {
                listOf(TestEntity(id = 2, value = "new"))
            }
        }
        val values = mutableListOf<List<TestEntity>>()
        val job = launch { array.collect { values.add(it) } }

        runCurrent()
        array.ids = listOf(2)
        runCurrent()
        advanceUntilIdle()

        val expectedEntities = listOf(TestEntity(id = 2, value = "new"))
        assertEquals(expectedEntities, values.last())

        job.cancelAndJoin()
    }

    @Test
    fun pagerFlowUsesLatestRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        val pager = collection.createPager(perPage = 2) {
            if (it.resetCache)
            {
                listOf(TestEntity(id = 2, value = "new"))
            }
            else
            {
                delay(1000)
                listOf(TestEntity(id = 1, value = "old"))
            }
        }
        val values = mutableListOf<List<TestEntity>>()
        val job = launch { pager.collect { values.add(it) } }

        runCurrent()
        pager.refresh(resetCache = true)
        runCurrent()
        advanceUntilIdle()

        val expectedEntities = listOf(TestEntity(id = 2, value = "new"))
        assertEquals(expectedEntities, values.last())

        job.cancelAndJoin()
    }

    @Test
    fun pagerFlowAppendsPages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = EntityFlowCollection.createInt<TestEntity>(dispatcher)
        val pager = collection.createPager(perPage = 2) {
            if (it.page == 0)
            {
                val pageEntities = listOf(
                    TestEntity(id = 1, value = "one"),
                    TestEntity(id = 2, value = "two")
                )
                pageEntities
            }
            else
            {
                val entity = TestEntity(id = 3, value = "three")
                val pageEntities = listOf(entity)
                pageEntities
            }
        }
        val values = mutableListOf<List<TestEntity>>()
        val job = launch { pager.collect { values.add(it) } }

        advanceUntilIdle()
        pager.next()
        advanceUntilIdle()

        assertEquals(PAGER_END, pager.page)
        val expectedIds = listOf(1, 2, 3)
        assertEquals(expectedIds, values.last().map { it.id })

        job.cancelAndJoin()
    }

    @Test
    fun repositoryUpdatesFlowCollectionBack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = TestRepository()
        val entities = listOf(
            TestEntityBack(id = 1, value = "one"),
            TestEntityBack(id = 2, value = "two")
        )
        repository.add(entities)

        val collection = EntityFlowCollectionExtraBack<Int, TestEntity, TestEntityBack, ExtraCollectionParams>(
            dispatcher = dispatcher,
            collectionExtra = ExtraCollectionParams(test = "extra"),
            map = { TestEntity(id = it.id, value = it.value, indirectId = it.indirectId, indirectValue = it.indirectValue) }
        )
        collection.repository = repository

        val single = collection.createSingle(id = 1)
        val ids = listOf(1, 2)
        val array = collection.createKeyArray(ids = ids)
        val singleValues = mutableListOf<TestEntity?>()
        val arrayValues = mutableListOf<List<TestEntity>>()
        val singleJob = launch { single.collect { singleValues.add(it) } }
        val arrayJob = launch { array.collect { arrayValues.add(it) } }

        advanceUntilIdle()

        val updatedEntity = TestEntityBack(id = 1, value = "one-new")
        repository.update(updatedEntity)
        advanceUntilIdle()

        assertEquals("one-new", singleValues.last()?.value)
        assertEquals("one-new", arrayValues.last()[0].value)

        singleJob.cancelAndJoin()
        arrayJob.cancelAndJoin()
    }
}
