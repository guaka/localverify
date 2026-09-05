import XCTest

final class ImportPickerTests: XCTestCase {
    func testCaseCopiesFiltersAndAutomaticNavigation() {
        let app = XCUIApplication(); app.launchArguments += ["--synthetic-case"]; app.launch()
        XCTAssertTrue(app.navigationBars["Case"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["sysdiagnose_synthetic_ui.tar.gz"].exists)
        let copyReport = app.buttons["copyCaseReport"]
        for _ in 0..<6 where !copyReport.isHittable { app.swipeUp() }
        copyReport.tap()
        XCTAssertTrue(copyReport.label.contains("Copied"))
        let copyAll = app.buttons["copyAllPayloads"]
        for _ in 0..<4 where !copyAll.isHittable { app.swipeUp() }
        copyAll.tap()
        XCTAssertTrue(app.buttons["copyAllPayloads"].label.contains("Copied"))
        let filter = app.buttons["campaignFilter"]
        for _ in 0..<6 where !filter.isHittable { app.swipeUp() }
        filter.tap()
        app.buttons["DarkSword (1)"].tap()
        XCTAssertFalse(app.staticTexts["pegasus-synthetic.invalid"].exists)
        let copyPayload = app.buttons["copyPayload-synthetic-darksword"]
        for _ in 0..<6 where !copyPayload.isHittable { app.swipeUp() }
        copyPayload.tap()
        XCTAssertTrue(copyPayload.label.contains("Copied"))
    }
    func testLargeProgressPanel() {
        let app = XCUIApplication(); app.launchArguments += ["--synthetic-progress"]; app.launch()
        XCTAssertTrue(app.staticTexts["Analyzing diagnostics"].exists)
        XCTAssertTrue(app.staticTexts["Checking definitions 1240/2336"].exists)
        let panel = app.otherElements["importStatus"]
        XCTAssertTrue(panel.exists)
        XCTAssertGreaterThan(panel.frame.height, 200)
        XCTAssertTrue(app.buttons["Cancel"].isHittable)
    }

    func testAssistiveTouchShortcutOpensSettings() throws {
        guard #available(iOS 26.0, *) else { throw XCTSkip("AssistiveTouch deep link requires iOS 26") }
        let app = XCUIApplication(); app.launch()
        app.buttons["collectionGuide"].tap()
        let shortcut = app.buttons["openAssistiveTouchSettings"]
        for _ in 0..<5 where !shortcut.isHittable { app.swipeUp() }
        shortcut.tap()
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        XCTAssertTrue(settings.wait(for: .runningForeground, timeout: 10), app.debugDescription)
        #if targetEnvironment(simulator)
        // The simulator omits Touch/AssistiveTouch and lands on Accessibility instead.
        XCTAssertTrue(settings.otherElements["AccessibilitySettingsControllerView"].waitForExistence(timeout: 5), settings.debugDescription)
        #else
        XCTAssertTrue(settings.navigationBars["AssistiveTouch"].waitForExistence(timeout: 5), settings.debugDescription)
        #endif
        // Navigation only: never toggle accessibility or analytics settings in a test.
        app.activate()
    }

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
        app.tabBars.buttons["Indicators"].tap()
        let size = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "2.33 MB")).firstMatch
        for _ in 0..<6 where !size.isHittable { app.swipeUp() }
        XCTAssertTrue(size.exists, app.debugDescription)
        XCTAssertTrue(app.staticTexts["indicatorCount"].label.contains("2336"))
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
        XCTAssertTrue(archive.isEnabled)
        archive.tap()
        let cancel = app.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "Cancelar"])).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10), app.debugDescription)
        cancel.tap()
        app.tabBars.buttons["Indicators"].tap()
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
        if #available(iOS 26.0, *) {
            let shortcut = app.buttons["openAssistiveTouchSettings"]
            for _ in 0..<5 where !shortcut.isHittable { app.swipeUp() }
            XCTAssertTrue(shortcut.isHittable)
        }
    }
}
