import XCTest

final class ImportPickerTests: XCTestCase {
    func testTabsAndBundledIndicatorMetadata() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.tabBars.buttons["Scan"].exists)
        app.tabBars.buttons["Cases"].tap()
        XCTAssertTrue(app.navigationBars["Cases"].exists)
        app.tabBars.buttons["About"].tap()
        let license = app.buttons["license"]
        for _ in 0..<6 where !license.isHittable { app.swipeUp() }
        license.tap()
        XCTAssertTrue(app.navigationBars["MVT License 1.1"].exists)
        app.tabBars.buttons["Scan"].tap()
        let size = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "1.49 MB")).firstMatch
        for _ in 0..<6 where !size.isHittable { app.swipeUp() }
        XCTAssertTrue(size.exists, app.debugDescription)
        XCTAssertTrue(app.staticTexts["indicatorCount"].label.contains("1862"))
        let date = app.descendants(matching: .any).matching(NSPredicate(format: "label MATCHES %@", ".*[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}.*")).firstMatch
        XCTAssertTrue(date.exists, app.debugDescription)
    }
    func testBothImportButtonsPresentPicker() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let archive = app.buttons["importArchive"]
        for _ in 0..<4 where !archive.isHittable { app.swipeUp() }
        XCTAssertTrue(archive.exists)
        XCTAssertFalse(archive.isEnabled)
        app.switches["consent"].switches.firstMatch.tap()
        XCTAssertTrue(NSPredicate(format: "enabled == true").evaluate(with: archive))
        archive.tap()
        let cancel = app.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "Cancelar"])).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10), app.debugDescription)
        cancel.tap()
        let indicators = app.buttons["importIndicators"]
        for _ in 0..<4 where !indicators.isHittable { app.swipeUp() }
        indicators.tap()
        XCTAssertTrue(cancel.waitForExistence(timeout: 10), app.debugDescription)
        cancel.tap()
    }
    func testCollectionGuideExistsOffline() {
        let app = XCUIApplication(); app.launch()
        app.buttons["collectionGuide"].tap()
        XCTAssertTrue(app.navigationBars["Collect diagnostics"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["These instructions work offline"].exists)
    }
}
