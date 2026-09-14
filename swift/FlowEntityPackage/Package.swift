// swift-tools-version: 5.9

import PackageDescription

let flowEntityVersion = "0.1.3"
let flowEntityChecksum = "85b53fca15fecbef414022de0bd893fbbd596400144216d061a45994586f5ee7"

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
