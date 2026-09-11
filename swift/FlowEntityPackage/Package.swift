// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "FlowEntityPackage",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "FlowEntityCombine",
            targets: ["FlowEntityCombine"]
        )
    ],
    targets: [
        .binaryTarget(
            name: "FlowEntity",
            path: "FlowEntity.xcframework"
        ),
        .target(
            name: "FlowEntityCombine",
            dependencies: ["FlowEntity"]
        )
    ]
)
