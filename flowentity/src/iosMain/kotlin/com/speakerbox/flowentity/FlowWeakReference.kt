package com.speakerbox.flowentity

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual class FlowWeakReference<T: Any> actual constructor(value: T)
{
    private val reference = kotlin.native.ref.WeakReference(value)

    actual fun get(): T? = reference.get()
}
