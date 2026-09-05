import XCTest

final class ImportPickerTests: XCTestCase {
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
