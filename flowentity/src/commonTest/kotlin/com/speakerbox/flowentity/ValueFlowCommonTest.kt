package com.speakerbox.flowentity

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ValueFlowCommonTest
{
    @Test
    fun valueFlowFetchesAndCanBeCollectedDirectly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = ValueFlowCollection.create(dispatcher)
        var calls = 0

        val value = collection.createValue {
            calls += 1
            10
        }
        value.singleton = true

        val values = mutableListOf<Int?>()
        val job = launch { value.collect { values.add(it) } }

        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(10, values.last())
        assertEquals(ValueFlowExtra.State.Ready, value.state.value)
        assertEquals(ValueFlowExtra.Loading.None, value.loading.value)

        job.cancelAndJoin()
        collection.close()
    }

    @Test
    fun valueFlowRefreshUsesLatestRequestAndResetCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = ValueFlowCollection.create(dispatcher)
        val value = collection.createValueExtra<String, String>(
            extra = "old",
            start = true
        ) {
            if (it.extra == "old")
            {
                delay(1000)
                "old-result"
            }
            else
            {
                "new-result"
            }
        }
        value.singleton = true

        val values = mutableListOf<String?>()
        val job = launch { value.collect { values.add(it) } }

        runCurrent()
        value.refresh(resetCache = true, extra = "new")
        runCurrent()
        advanceUntilIdle()

        assertEquals("new-result", values.last())
        assertEquals(ValueFlowExtra.State.Ready, value.state.value)

        job.cancelAndJoin()
        collection.close()
    }

    @Test
    fun valueFlowRefreshOnStateFlowSkipsCurrentValue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val filter = MutableStateFlow("one")
        val collection = ValueFlowCollection.create(dispatcher)
        val calls = mutableListOf<String>()
        val value = collection.createValueExtra<String, String>(
            extra = filter.value,
            start = true
        ) {
            calls.add(it.extra!!)
            it.extra
        }
        value.singleton = true

        val job = launch { value.collect { } }
        value.refreshOn(source = filter, resetCache = true)
        advanceUntilIdle()

        assertEquals(listOf("one"), calls)

        filter.value = "two"
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), calls)
        assertEquals("two", value.value)

        job.cancelAndJoin()
        collection.close()
    }

    @Test
    fun valueFlowCollectionRefreshesAllValuesOnGlobalStateChange() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = MutableStateFlow<String?>("first")
        val collection = ValueFlowCollectionExtra(
            dispatcher = dispatcher,
            collectionExtra = context.value
        )
        val calls = mutableListOf<String?>()
        val first = collection.createValue(start = true) {
            calls.add(it.collectionExtra)
            it.collectionExtra
        }
        val second = collection.createValue(start = true) {
            calls.add(it.collectionExtra)
            it.collectionExtra
        }
        first.singleton = true
        second.singleton = true

        val firstJob = launch { first.collect { } }
        val secondJob = launch { second.collect { } }
        collection.refreshOn(source = context, resetCache = true)
        advanceUntilIdle()

        assertEquals(listOf<String?>("first", "first"), calls)

        context.value = "second"
        advanceUntilIdle()

        assertEquals(listOf<String?>("first", "first", "second", "second"), calls)
        assertEquals("second", first.value)
        assertEquals("second", second.value)

        firstJob.cancelAndJoin()
        secondJob.cancelAndJoin()
        collection.close()
    }

    @Test
    fun valueFlowPublishesErrorsAndKeepsValueWhenCacheIsNotReset() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = ValueFlowCollection.create(dispatcher)
        val exception = IllegalStateException("failed")
        var shouldFail = false
        val value = collection.createValue(start = true) {
            if (shouldFail)
                throw exception

            10
        }
        value.singleton = true

        val values = mutableListOf<Int?>()
        val errors = mutableListOf<Throwable>()
        val valueJob = launch { value.collect { values.add(it) } }
        val errorJob = launch { value.errors.collect { errors.add(it) } }

        advanceUntilIdle()
        shouldFail = true
        value.refresh(resetCache = false)
        advanceUntilIdle()

        assertEquals(10, value.value)
        assertSame(exception, errors.last())
        assertSame(exception, value.errorState.value)
        assertTrue(values.contains(10))

        valueJob.cancelAndJoin()
        errorJob.cancelAndJoin()
        collection.close()
    }

    @Test
    fun valueFlowDisposesAfterLastCollectorUnlessSingleton() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collection = ValueFlowCollection.create(dispatcher)
        val value = collection.createValue(start = false) { 10 }

        val job = launch { value.collect { } }
        advanceUntilIdle()
        job.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(0, value.collectors.value)
        assertTrue(value.disposed)

        val singleton = collection.createValue(start = false) { 20 }
        singleton.singleton = true
        val singletonJob = launch { singleton.collect { } }
        advanceUntilIdle()
        singletonJob.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(0, singleton.collectors.value)
        assertFalse(singleton.disposed)
        collection.close()
    }
}
