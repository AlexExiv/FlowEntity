package com.speakerbox.flowentity

const val PAGER_END = -9999

open class PagerFlowExtra<Id: Any, E: Entity<Id>, Extra>(
    holder: EntityFlowCollectionExtra<Id, E, *>,
    perPage: Int,
    extra: Extra? = null
) : ArrayFlowExtra<Id, E, Extra>(holder, perPage, extra)
{
    open fun next()
    {
    }

    protected open fun append(entities: List<E>): List<E>
    {
        if (perPage == ARRAY_PER_PAGE)
        {
            page = PAGER_END
            return entities
        }

        val newEntities = _entities.toMutableList()
        newEntities.appendOrReplaceEntity(entities)
        page = if (entities.size >= perPage) page + 1 else PAGER_END
        return newEntities
    }
}

typealias PagerFlow<Id, Entity> =
    PagerFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

typealias PagerFlowExtraInt<Entity, Extra> = PagerFlowExtra<Int, Entity, Extra>
typealias PagerFlowInt<Entity> = PagerFlow<Int, Entity>

typealias PagerFlowExtraLong<Entity, Extra> = PagerFlowExtra<Long, Entity, Extra>
typealias PagerFlowLong<Entity> = PagerFlow<Long, Entity>

typealias PagerFlowExtraString<Entity, Extra> = PagerFlowExtra<String, Entity, Extra>
typealias PagerFlowString<Entity> = PagerFlow<String, Entity>
