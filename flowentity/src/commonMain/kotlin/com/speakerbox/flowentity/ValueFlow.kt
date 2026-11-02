package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class ValueFlowExtra<Value: Any, Extra> internal constructor(
    collection: ValueFlowCollectionExtra<*>,
    dispatcher: CoroutineDispatcher,
    extra: Extra?
) : Flow<Value?>, SwiftObservable
{
    enum class Loading
    {
        None,
        FirstLoading,
        Loading;

        val isLoading: Boolean get() = this == FirstLoading || this == Loading
    }

    enum class State
    {
        Initializing,
        Ready,
        Empty
    }

    private val _data = MutableStateFlow<Value?>(null)
    private val _state = MutableStateFlow(State.Initializing)
    private val _loading = MutableStateFlow(Loading.None)
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 64)
    private val _errorState = MutableStateFlow<Throwable?>(null)
    private val _collectors = MutableStateFlow(0)

    private val holderReference = FlowWeakReference(collection)
    protected val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val collectorsMutex = Mutex()
    private var sourceJob: Job? = null

    var extra: Extra? = extra
        protected set

    val value: Value? get() = _data.value
    val state: ObservableStateFlow<State> = _state.asStateFlow().toObservable(scope = scope)
    val loading: ObservableStateFlow<Loading> = _loading.asStateFlow().toObservable(scope = scope)
    val errors: ObservableSharedFlow<Throwable> = _errors.asSharedFlow().toObservable(scope = scope)
    val errorState: ObservableStateFlow<Throwable?> = _errorState.asStateFlow().toObservable(scope = scope)
    val collectors: ObservableStateFlow<Int> = _collectors.asStateFlow().toObservable(scope = scope)

    var disposed = false
        private set

    var singleton = false

    open fun refresh(resetCache: Boolean = false, extra: Extra? = null)
    {
    }

    open suspend fun refreshNow(resetCache: Boolean = false, extra: Extra? = null)
    {
        this.extra = extra ?: this.extra
    }

    fun refreshOn(source: Flow<Extra?>, resetCache: Boolean = true): FlowSubscription
    {
        sourceJob?.cancel()

        val job = source
            .onEach {
                this.extra = it
                refresh(resetCache = resetCache, extra = it)
            }
            .launchInNow(scope)

        sourceJob = job
        return FlowJobSubscription(job)
    }

    fun refreshOn(source: StateFlow<Extra?>, resetCache: Boolean = true): FlowSubscription
    {
        val changed = extra != source.value
        extra = source.value

        if (changed)
            refresh(resetCache = resetCache, extra = extra)

        return refreshOn(source = source.drop(1), resetCache = resetCache)
    }

    fun watch(onValue: (Value?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        ObservableFlow(this, scope).watch(onValue = onValue, onError = onError)

    override fun watchAny(onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        watch(onValue = onValue, onError = onError)

    override suspend fun collect(collector: FlowCollector<Value?>)
    {
        attachCollector()

        try
        {
            _data.collect(collector)
        }
        finally
        {
            detachCollector()
        }
    }

    fun dispose()
    {
        if (disposed)
            return

        disposed = true
        sourceJob?.cancel()
        scope.cancel()
        holderReference.get()?.remove(this)
    }

    internal open fun refreshData(resetCache: Boolean, collectionExtra: Any?)
    {
    }

    protected fun publish(value: Value?)
    {
        _data.value = value
    }

    protected fun setState(state: State)
    {
        _state.value = state
    }

    protected fun updateLoading(loading: Loading)
    {
        _loading.value = loading
    }

    protected fun updateLoading(loading: Loading, error: Throwable?)
    {
        _loading.value = loading

        if (error != null)
            _errors.tryEmit(error)

        _errorState.value = error
    }

    protected suspend fun attachCollector()
    {
        collectorsMutex.withLock {
            if (disposed)
                throw IllegalStateException("Trying to collect ValueFlow that has been disposed already. Maybe you forgot to make it singleton?")

            _collectors.value += 1
        }
    }

    protected suspend fun detachCollector()
    {
        var shouldDispose = false

        collectorsMutex.withLock {
            val count = (_collectors.value - 1).coerceAtLeast(0)
            _collectors.value = count
            shouldDispose = count == 0 && !singleton
        }

        if (shouldDispose)
            dispose()
    }
}

typealias ValueFlow<Value> = ValueFlowExtra<Value, EntityCollectionExtraParamsEmpty>

typealias ValueFlowExtraInt<Extra> = ValueFlowExtra<Int, Extra>
typealias ValueFlowInt = ValueFlow<Int>

typealias ValueFlowExtraLong<Extra> = ValueFlowExtra<Long, Extra>
typealias ValueFlowLong = ValueFlow<Long>

typealias ValueFlowExtraString<Extra> = ValueFlowExtra<String, Extra>
typealias ValueFlowString = ValueFlow<String>
