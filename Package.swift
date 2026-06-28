// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GachlabCapacitorBackgroundGeolocation",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "GachlabCapacitorBackgroundGeolocation",
            targets: ["BackgroundGeolocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "BackgroundGeolocationCore",
            path: "ios/Sources/BackgroundGeolocationCore",
            linkerSettings: [
                .linkedLibrary("sqlite3"),
                .linkedFramework("CoreLocation"),
                .linkedFramework("CoreMotion"),
                .linkedFramework("UIKit"),
                .linkedFramework("Network"),
                .linkedFramework("UserNotifications")
            ]
        ),
        .target(
            name: "BackgroundGeolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                "BackgroundGeolocationCore"
            ],
            path: "ios/Sources/BackgroundGeolocationPlugin"),
        .testTarget(
            name: "BackgroundGeolocationPluginTests",
            dependencies: ["BackgroundGeolocationPlugin", "BackgroundGeolocationCore"],
            path: "ios/Tests/BackgroundGeolocationPluginTests")
    ]
)
