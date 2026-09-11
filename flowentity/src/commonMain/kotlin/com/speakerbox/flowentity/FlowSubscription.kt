package com.speakerbox.flowentity

import kotlinx.coroutines.Job

interface FlowSubscription
{
    fun cancel()
}

internal class FlowJobSubscription(
    private val job: Job
) : FlowSubscription
{
    override fun cancel()
    {
        job.cancel()
    }
}
