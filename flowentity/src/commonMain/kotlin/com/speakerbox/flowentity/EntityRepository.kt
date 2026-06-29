package com.speakerbox.flowentity

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class UpdateOperation
{
    None,
    Insert,
    Update,
    Delete,
    Clear
}

data class EntityUpdated<Id: Any, EB: EntityBack<Id>>(
    val id: Id,
    val entity: EB? = null,
    val operation: UpdateOperation = UpdateOperation.None
)

interface EntityRepositoryInterface<Id: Any, EB: EntityBack<Id>>
{
    val updates: SharedFlow<List<EntityUpdated<Id, EB>>>

    suspend fun get(id: Id): EB?
    suspend fun get(ids: List<Id>): List<EB>
}

interface EntityAllRepositoryInterface<Id: Any, EB: EntityBack<Id>> : EntityRepositoryInterface<Id, EB>
{
    suspend fun fetchAll(): List<EB>
}

abstract class EntityRepository<Id: Any, EB: EntityBack<Id>> : EntityRepositoryInterface<Id, EB>
{
    private val _updates = MutableSharedFlow<List<EntityUpdated<Id, EB>>>(extraBufferCapacity = 64)

    override val updates: SharedFlow<List<EntityUpdated<Id, EB>>> = _updates.asSharedFlow()

    protected suspend fun emitUpdated(updates: List<EntityUpdated<Id, EB>>)
    {
        _updates.emit(updates)
    }

    protected fun tryEmitUpdated(updates: List<EntityUpdated<Id, EB>>)
    {
        _updates.tryEmit(updates)
    }
}
