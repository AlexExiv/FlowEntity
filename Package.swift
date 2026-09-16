// swift-tools-version: 5.9

import PackageDescription

let flowEntityVersion = "0.1.6"
let flowEntityChecksum = "5b5a6d986102889306d2b8096a8e9f84482148fa3269452af6cb0477be876bd4"

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
