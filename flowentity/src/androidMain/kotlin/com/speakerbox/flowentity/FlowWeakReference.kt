package com.speakerbox.flowentity

internal actual class FlowWeakReference<T: Any> actual constructor(value: T)
{
    private val reference = java.lang.ref.WeakReference(value)

    actual fun get(): T? = reference.get()
}
