package com.speakerbox.flowentity.cache

import com.speakerbox.flowentity.EntityAllRepositoryInterface
import com.speakerbox.flowentity.EntityBack

interface RepositoryCacheAllSourceInterface<Id: Any, EB: EntityBack<Id>> :
    EntityAllRepositoryInterface<Id, EB>

interface RepositoryCacheAllStorageInterface<Id: Any, EB: EntityBack<Id>> :
    RepositoryCacheAllSourceInterface<Id, EB> {
    suspend fun save(entities: List<EB>): List<EB>
    suspend fun rewriteAll(entities: List<EB>): List<EB>
}
