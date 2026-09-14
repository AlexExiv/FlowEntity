import FlowEntity

public typealias EntityInt = Entity
public typealias EntityLong = Entity
public typealias EntityString = Entity

public typealias EntityBackInt = EntityBack
public typealias EntityBackLong = EntityBack
public typealias EntityBackString = EntityBack

public typealias SingleFlow<Id: AnyObject, Entity: AnyObject> =
    SingleFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

public typealias SingleFlowExtraInt<Entity: AnyObject, Extra: AnyObject> =
    SingleFlowExtra<KotlinInt, Entity, Extra>
public typealias SingleFlowInt<Entity: AnyObject> =
    SingleFlow<KotlinInt, Entity>

public typealias SingleFlowExtraLong<Entity: AnyObject, Extra: AnyObject> =
    SingleFlowExtra<KotlinLong, Entity, Extra>
public typealias SingleFlowLong<Entity: AnyObject> =
    SingleFlow<KotlinLong, Entity>

public typealias SingleFlowExtraString<Entity: AnyObject, Extra: AnyObject> =
    SingleFlowExtra<NSString, Entity, Extra>
public typealias SingleFlowString<Entity: AnyObject> =
    SingleFlow<NSString, Entity>

public typealias ArrayFlow<Id: AnyObject, Entity: AnyObject> =
    ArrayFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

public typealias ArrayFlowExtraInt<Entity: AnyObject, Extra: AnyObject> =
    ArrayFlowExtra<KotlinInt, Entity, Extra>
public typealias ArrayFlowInt<Entity: AnyObject> =
    ArrayFlow<KotlinInt, Entity>

public typealias ArrayFlowExtraLong<Entity: AnyObject, Extra: AnyObject> =
    ArrayFlowExtra<KotlinLong, Entity, Extra>
public typealias ArrayFlowLong<Entity: AnyObject> =
    ArrayFlow<KotlinLong, Entity>

public typealias ArrayFlowExtraString<Entity: AnyObject, Extra: AnyObject> =
    ArrayFlowExtra<NSString, Entity, Extra>
public typealias ArrayFlowString<Entity: AnyObject> =
    ArrayFlow<NSString, Entity>

public typealias ArrayKeyFlow<Id: AnyObject, Entity: AnyObject> =
    ArrayKeyFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

public typealias ArrayKeyFlowExtraInt<Entity: AnyObject, Extra: AnyObject> =
    ArrayKeyFlowExtra<KotlinInt, Entity, Extra>
public typealias ArrayKeyFlowInt<Entity: AnyObject> =
    ArrayKeyFlow<KotlinInt, Entity>

public typealias ArrayKeyFlowExtraLong<Entity: AnyObject, Extra: AnyObject> =
    ArrayKeyFlowExtra<KotlinLong, Entity, Extra>
public typealias ArrayKeyFlowLong<Entity: AnyObject> =
    ArrayKeyFlow<KotlinLong, Entity>

public typealias ArrayKeyFlowExtraString<Entity: AnyObject, Extra: AnyObject> =
    ArrayKeyFlowExtra<NSString, Entity, Extra>
public typealias ArrayKeyFlowString<Entity: AnyObject> =
    ArrayKeyFlow<NSString, Entity>

public typealias PagerFlow<Id: AnyObject, Entity: AnyObject> =
    PagerFlowExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

public typealias PagerFlowExtraInt<Entity: AnyObject, Extra: AnyObject> =
    PagerFlowExtra<KotlinInt, Entity, Extra>
public typealias PagerFlowInt<Entity: AnyObject> =
    PagerFlow<KotlinInt, Entity>

public typealias PagerFlowExtraLong<Entity: AnyObject, Extra: AnyObject> =
    PagerFlowExtra<KotlinLong, Entity, Extra>
public typealias PagerFlowLong<Entity: AnyObject> =
    PagerFlow<KotlinLong, Entity>

public typealias PagerFlowExtraString<Entity: AnyObject, Extra: AnyObject> =
    PagerFlowExtra<NSString, Entity, Extra>
public typealias PagerFlowString<Entity: AnyObject> =
    PagerFlow<NSString, Entity>

public typealias EntityFlowCollection<Id: AnyObject, Entity: AnyObject> =
    EntityFlowCollectionExtra<Id, Entity, EntityCollectionExtraParamsEmpty>

public typealias EntityFlowCollectionExtraInt<Entity: AnyObject, CollectionExtra: AnyObject> =
    EntityFlowCollectionExtra<KotlinInt, Entity, CollectionExtra>
public typealias EntityFlowCollectionInt<Entity: AnyObject> =
    EntityFlowCollection<KotlinInt, Entity>

public typealias EntityFlowCollectionExtraLong<Entity: AnyObject, CollectionExtra: AnyObject> =
    EntityFlowCollectionExtra<KotlinLong, Entity, CollectionExtra>
public typealias EntityFlowCollectionLong<Entity: AnyObject> =
    EntityFlowCollection<KotlinLong, Entity>

public typealias EntityFlowCollectionExtraString<Entity: AnyObject, CollectionExtra: AnyObject> =
    EntityFlowCollectionExtra<NSString, Entity, CollectionExtra>
public typealias EntityFlowCollectionString<Entity: AnyObject> =
    EntityFlowCollection<NSString, Entity>

public typealias EntityFlowCollectionBack<Id: AnyObject, Entity: AnyObject, EntityBack: AnyObject> =
    EntityFlowCollectionExtraBack<Id, Entity, EntityBack, EntityCollectionExtraParamsEmpty>

public typealias EntityFlowCollectionExtraBackInt<Entity: AnyObject, EntityBack: AnyObject, CollectionExtra: AnyObject> =
    EntityFlowCollectionExtraBack<KotlinInt, Entity, EntityBack, CollectionExtra>
public typealias EntityFlowCollectionBackInt<Entity: AnyObject, EntityBack: AnyObject> =
    EntityFlowCollectionBack<KotlinInt, Entity, EntityBack>

public typealias EntityFlowCollectionExtraBackLong<Entity: AnyObject, EntityBack: AnyObject, CollectionExtra: AnyObject> =
    EntityFlowCollectionExtraBack<KotlinLong, Entity, EntityBack, CollectionExtra>
public typealias EntityFlowCollectionBackLong<Entity: AnyObject, EntityBack: AnyObject> =
    EntityFlowCollectionBack<KotlinLong, Entity, EntityBack>

public typealias EntityFlowCollectionExtraBackString<Entity: AnyObject, EntityBack: AnyObject, CollectionExtra: AnyObject> =
    EntityFlowCollectionExtraBack<NSString, Entity, EntityBack, CollectionExtra>
public typealias EntityFlowCollectionBackString<Entity: AnyObject, EntityBack: AnyObject> =
    EntityFlowCollectionBack<NSString, Entity, EntityBack>
