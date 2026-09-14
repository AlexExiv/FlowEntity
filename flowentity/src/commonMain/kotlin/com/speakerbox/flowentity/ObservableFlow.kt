package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

open class ObservableFlow<T>(
    private val flow: Flow<T>,
    private val scope: CoroutineScope
) : Flow<T>, SwiftObservable
{
    override suspend fun collect(collector: FlowCollector<T>)
    {
        flow.collect(collector)
    }

    fun watch(onValue: (T) -> Unit, onError: (Throwable) -> Unit): FlowSubscription
    {
        val job = flow
            .catch { onError(it) }
            .onEach { onValue(it) }
            .launchInNow(scope)

        return FlowJobSubscription(job)
    }

    override fun watchAny(onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        watch(onValue = onValue, onError = onError)
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class ObservableSharedFlow<T>(
    private val flow: SharedFlow<T>,
    private val scope: CoroutineScope
) : SharedFlow<T>, SwiftObservable
{
    override val replayCache: List<T> get() = flow.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing
    {
        flow.collect(collector)
    }

    fun watch(onValue: (T) -> Unit, onError: (Throwable) -> Unit): FlowSubscription
    {
        val job = flow
            .onEach { onValue(it) }
            .launchInNow(scope)

        return FlowJobSubscription(job)
    }

    override fun watchAny(onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        watch(onValue = onValue, onError = onError)
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class ObservableStateFlow<T>(
    private val flow: StateFlow<T>,
    private val scope: CoroutineScope
) : StateFlow<T>, SwiftObservable
{
    override val replayCache: List<T> get() = flow.replayCache

    override val value: T get() = flow.value

    override suspend fun collect(collector: FlowCollector<T>): Nothing
    {
        flow.collect(collector)
    }

    fun watch(onValue: (T) -> Unit, onError: (Throwable) -> Unit): FlowSubscription
    {
        val job = flow
            .onEach { onValue(it) }
            .launchInNow(scope)

        return FlowJobSubscription(job)
    }

    override fun watchAny(onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        watch(onValue = onValue, onError = onError)
}

fun <T> Flow<T>.toObservable(scope: CoroutineScope): ObservableFlow<T> =
    ObservableFlow(flow = this, scope = scope)

fun <T> StateFlow<T>.toObservable(scope: CoroutineScope): ObservableStateFlow<T> =
    ObservableStateFlow(flow = this, scope = scope)

fun <T> SharedFlow<T>.toObservable(scope: CoroutineScope): ObservableSharedFlow<T> =
    ObservableSharedFlow(flow = this, scope = scope)
