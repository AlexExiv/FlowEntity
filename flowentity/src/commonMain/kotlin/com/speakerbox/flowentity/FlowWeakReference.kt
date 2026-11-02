package com.speakerbox.flowentity

internal expect class FlowWeakReference<T: Any>(value: T)
{
    fun get(): T?
}
