// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "TriageCore", platforms: [.macOS(.v13), .iOS(.v17)], products: [.library(name: "TriageCore", targets: ["TriageCore"])], targets: [.systemLibrary(name: "CZlib"), .target(name: "TriageCore", dependencies: ["CZlib"]), .testTarget(name: "TriageCoreTests", dependencies: ["TriageCore"])])
