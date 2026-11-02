# FlowEntity

`ValueFlow` is intended for requests that return a value without an entity
identifier, such as counts, filters and aggregates.

```kotlin
val values = ValueFlowCollectionExtra<ProductContext>(
    collectionExtra = context.value
)

val count = values.createValueExtra<Int, ProductFilter>(
    extra = filter.value
) {
    api.getProductCount(
        context = it.collectionExtra,
        filter = it.extra
    )
}

count.refreshOn(source = filter, resetCache = true)
values.refreshOn(source = context, resetCache = true)
```

The same flow can be consumed from Swift through the `SwiftObservable`
publisher adapters in `FlowEntityCombine`.
