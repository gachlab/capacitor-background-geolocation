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
            name: "BackgroundGeolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/BackgroundGeolocationPlugin"),
        .testTarget(
            name: "BackgroundGeolocationPluginTests",
            dependencies: ["BackgroundGeolocationPlugin"],
            path: "ios/Tests/BackgroundGeolocationPluginTests")
    ]
)
