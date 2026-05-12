// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "JosuelmmCapacitorBackgroundGeolocation",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "JosuelmmCapacitorBackgroundGeolocation",
            targets: ["BackgroundGeolocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "MAURBackgroundGeolocation",
            path: "ios/common/BackgroundGeolocation",
            publicHeadersPath: ".",
            cSettings: [
                .headerSearchPath("."),
                .headerSearchPath("INTULocationManager"),
                .headerSearchPath("SQLQueryBuilder/ext"),
                .headerSearchPath("SQLQueryBuilder/sql")
            ],
            linkerSettings: [
                .linkedLibrary("sqlite3"),
                .linkedFramework("CoreLocation"),
                .linkedFramework("CoreMotion"),
                .linkedFramework("UIKit")
            ]
        ),
        .target(
            name: "BackgroundGeolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                "MAURBackgroundGeolocation"
            ],
            path: "ios/Sources/BackgroundGeolocationPlugin"),
        .testTarget(
            name: "BackgroundGeolocationPluginTests",
            dependencies: ["BackgroundGeolocationPlugin"],
            path: "ios/Tests/BackgroundGeolocationPluginTests")
    ]
)
