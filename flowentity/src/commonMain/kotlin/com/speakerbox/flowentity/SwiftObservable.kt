package com.speakerbox.flowentity

interface SwiftObservable
{
    fun watchAny(onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription
}
