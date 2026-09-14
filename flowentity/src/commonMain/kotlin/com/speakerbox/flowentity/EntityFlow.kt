package com.speakerbox.flowentity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalObjCRefinement::class)
abstract class EntityFlow<Id: Any, E: Entity<Id>, EL>(
    protected val holder: EntityFlowCollectionExtra<Id, E, *>
) : Flow<EL>, SwiftObservable
{
    enum class Loading
    {
        None,
        FirstLoading,
        Loading;

        val isLoading: Boolean get() = this == FirstLoading || this == Loading
    }

    private val _loading = MutableStateFlow(Loading.None)
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 64)
    private val _errorState = MutableStateFlow<Throwable?>(null)
    private val _collectors = MutableStateFlow(0)

    protected val scope = CoroutineScope(SupervisorJob() + holder.dispatcher)
    protected val uuid = Uuid.random().toString()

    private val collectorsMutex = Mutex()

    @HiddenFromObjC
    internal val loadingInternal: StateFlow<Loading> = _loading.asStateFlow()

    @HiddenFromObjC
    internal val errorsInternal: SharedFlow<Throwable> = _errors.asSharedFlow()

    @HiddenFromObjC
    internal val errorStateInternal: StateFlow<Throwable?> = _errorState.asStateFlow()

    @HiddenFromObjC
    internal val collectorsInternal: StateFlow<Int> = _collectors.asStateFlow()

    val loading: ObservableStateFlow<Loading> = loadingInternal.toObservable(scope = scope)
    val errors: ObservableSharedFlow<Throwable> = errorsInternal.toObservable(scope = scope)
    val errorState: ObservableStateFlow<Throwable?> = errorStateInternal.toObservable(scope = scope)
    val collectors: ObservableStateFlow<Int> = collectorsInternal.toObservable(scope = scope)

    var disposed = false
        private set

    var singleton = false

    init
    {
        holder.add(this)
    }

    open fun update(source: String, entity: E)
    {
    }

    open fun update(source: String, entities: Map<Id, E>)
    {
    }

    open fun update(entity: E, operation: UpdateOperation)
    {
        update(entities = mapOf(entity.id to entity), operation = operation)
    }

    open fun update(entities: Map<Id, E>, operation: UpdateOperation)
    {
    }

    open fun update(entities: Map<Id, E>, operations: Map<Id, UpdateOperation>)
    {
    }

    open fun delete(ids: Set<Id>)
    {
    }

    open fun clear()
    {
    }

    open fun refreshData(resetCache: Boolean, data: Any?)
    {
    }

    open fun dispose()
    {
        if (disposed)
            return

        disposed = true
        scope.cancel()
        holder.remove(this)

        EntityCollectionConfig.log("EntityFlow has been disposed. UUID - $uuid")
    }

    fun watch(onValue: (EL) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        ObservableFlow(this, scope).watch(onValue = onValue, onError = onError)

    override fun watchAny(onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): FlowSubscription =
        watch(onValue = onValue, onError = onError)

    protected suspend fun attachCollector()
    {
        collectorsMutex.withLock {
            if (disposed)
                throw IllegalStateException("Trying to collect EntityFlow that has been disposed already. Maybe you forgot to make it singleton?")

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

    internal fun updateLoading(loading: Loading)
    {
        _loading.value = loading
    }

    internal fun updateLoading(loading: Loading, error: Throwable?)
    {
        _loading.value = loading

        if (error != null)
            _errors.tryEmit(error)

        _errorState.value = error
    }
}
