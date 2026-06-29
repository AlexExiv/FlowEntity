package com.speakerbox.flowentity

fun <Id: Any, E: Entity<Id>> List<E>.toEntitiesMap(): Map<Id, E>
{
    val map = mutableMapOf<Id, E>()
    forEach { map[it.id] = it }
    return map
}

fun <Id: Any, EB: EntityBack<Id>> List<EB>.toEntitiesBackMap(): Map<Id, EB>
{
    val map = mutableMapOf<Id, EB>()
    forEach { map[it.id] = it }
    return map
}

fun <Id: Any, E: Entity<Id>> MutableList<E>.appendNotExistEntity(entity: E)
{
    if (firstOrNull { it.id == entity.id } == null)
        add(entity)
}

fun <Id: Any, E: Entity<Id>> MutableList<E>.appendOrReplaceEntity(entities: List<E>)
{
    entities.forEach { entity ->
        val index = indexOfFirst { it.id == entity.id }
        if (index == -1)
            add(entity)
        else
            this[index] = entity
    }
}

fun <Id: Any, E: Entity<Id>> MutableList<E>.removeEntity(entity: E)
{
    removeEntityById(entity.id)
}

fun <Id: Any, E: Entity<Id>> MutableList<E>.removeEntityById(id: Id)
{
    val index = indexOfFirst { it.id == id }
    if (index != -1)
        removeAt(index)
}

fun <Id: Any> MutableList<Id>.appendNotExistId(id: Id)
{
    if (firstOrNull { it == id } == null)
        add(id)
}

fun <Id: Any, E: Entity<Id>> MutableList<E>.findEntityById(id: Id) = firstOrNull { it.id == id }
