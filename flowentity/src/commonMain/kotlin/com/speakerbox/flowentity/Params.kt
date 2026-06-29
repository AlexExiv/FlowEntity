package com.speakerbox.flowentity

data class EntityCollectionExtraParamsEmpty(val unused: Int = 0)

data class SingleParams<Id: Any, E: Entity<Id>, Extra, CollectionExtra>(
    val refreshing: Boolean = false,
    val resetCache: Boolean = false,
    val first: Boolean = false,
    val id: Id? = null,
    val last: E? = null,
    val extra: Extra? = null,
    val collectionExtra: CollectionExtra? = null
)

data class KeyParams<Id: Any, Extra, CollectionExtra>(
    val refreshing: Boolean = false,
    val resetCache: Boolean = false,
    val first: Boolean = false,
    val ids: List<Id>,
    val extra: Extra? = null,
    val collectionExtra: CollectionExtra? = null
)

data class PageParams<Id: Any, Extra, CollectionExtra>(
    val page: Int,
    val perPage: Int,
    val refreshing: Boolean = false,
    val resetCache: Boolean = false,
    val first: Boolean = false,
    val extra: Extra? = null,
    val collectionExtra: CollectionExtra? = null
)

typealias SingleFetchCallback<Id, E, Extra, CollectionExtra> =
    suspend (SingleParams<Id, E, Extra, CollectionExtra>) -> E?

typealias ArrayFetchCallback<Id, E, Extra, CollectionExtra> =
    suspend (KeyParams<Id, Extra, CollectionExtra>) -> List<E>

typealias PageFetchCallback<Id, E, Extra, CollectionExtra> =
    suspend (PageParams<Id, Extra, CollectionExtra>) -> List<E>
