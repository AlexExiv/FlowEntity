package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal fun <T> Flow<T>.launchInNow(scope: CoroutineScope): Job
{
    return scope.launch(start = CoroutineStart.UNDISPATCHED) {
        collect { }
    }
}
