package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

class FlowObservable<T> internal constructor(
    private val flow: Flow<T>,
    private val scope: CoroutineScope
) : SwiftObservable
{
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
