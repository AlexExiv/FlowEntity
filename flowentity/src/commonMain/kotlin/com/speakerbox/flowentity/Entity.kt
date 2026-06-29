package com.speakerbox.flowentity

interface Entity<out Id: Any>
{
    val id: Id
}

typealias EntityInt = Entity<Int>
typealias EntityLong = Entity<Long>
typealias EntityString = Entity<String>

interface EntityBack<out Id: Any>
{
    val id: Id
}

typealias EntityBackInt = EntityBack<Int>
typealias EntityBackLong = EntityBack<Long>
typealias EntityBackString = EntityBack<String>

interface EntityFactory<Id: Any, in Source: EntityBack<Id>, out Dest: Entity<Id>>
{
    fun map(entity: Source): Dest
}
