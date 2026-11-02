// swift-tools-version: 5.9

import PackageDescription

let flowEntityVersion = "0.1.7"
let flowEntityChecksum = "5e7e1d37d6671ec30a790b7a5582d8bcf05dd3dea569c3ab1ac5eacdf8ed95b9"

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
            url: "https://repo1.maven.org/maven2/io/github/alexexiv/flowentity/\(flowEntityVersion)/flowentity-\(flowEntityVersion)-ios-xcframework.zip",
            checksum: flowEntityChecksum
        ),
        .target(
            name: "FlowEntityCombine",
            dependencies: ["FlowEntity"]
        )
    ]
)
