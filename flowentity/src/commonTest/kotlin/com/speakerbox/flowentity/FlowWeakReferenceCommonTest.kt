package com.speakerbox.flowentity

import kotlin.test.Test
import kotlin.test.assertSame

class FlowWeakReferenceCommonTest
{
    @Test
    fun weakReferenceReturnsLiveValue()
    {
        val value = Any()
        val reference = FlowWeakReference(value)

        assertSame(value, reference.get())
    }
}
