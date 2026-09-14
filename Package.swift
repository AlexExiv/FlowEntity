// swift-tools-version: 5.9

import PackageDescription

let flowEntityVersion = "0.1.5"
let flowEntityChecksum = "c97232cb42152baafe837abb38cc3dd3757b46441449615eae3c7f50001ff57c"

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
