import XCTest

final class ImportPickerTests: XCTestCase {
    private func scrollToHittable(_ app: XCUIApplication, element: XCUIElement, attempts: Int = 12) {
        for _ in 0..<attempts {
            if element.isHittable { return }
            app.swipeUp()
        }
    }

    func testCaseCopiesFiltersAndAutomaticNavigation() {
        let app = XCUIApplication(); app.launchArguments += ["--synthetic-case"]; app.launch()
        XCTAssertTrue(app.navigationBars["Case"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["sysdiagnose_synthetic_ui.tar.gz"].exists)
        let copyReport = app.buttons["copyCaseReport"]
        scrollToHittable(app, element: copyReport)
        copyReport.tap()
        XCTAssertTrue(copyReport.label.contains("Copied"))
        let copyAll = app.buttons["copyAllPayloads"]
        scrollToHittable(app, element: copyAll, attempts: 6)
        copyAll.tap()
        XCTAssertTrue(app.buttons["copyAllPayloads"].label.contains("Copied"))
        let filter = app.buttons["campaignFilter"]
        scrollToHittable(app, element: filter)
        filter.tap()
        app.buttons["DarkSword (1)"].tap()
        XCTAssertFalse(app.staticTexts["pegasus-synthetic.invalid"].exists)
        let copyPayload = app.buttons["copyPayload-synthetic-darksword"]
        scrollToHittable(app, element: copyPayload)
        copyPayload.tap()
        XCTAssertTrue(copyPayload.label.contains("Copied"))
    }

    func testSyntheticCasePreparesZipForSharing() {
        let app = XCUIApplication(); app.launchArguments += ["--synthetic-case"]; app.launch()
        XCTAssertTrue(app.navigationBars["Case"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["sysdiagnose_synthetic_ui.tar.gz"].exists)

        let caseReport = app.buttons["copyCaseReport"]
        scrollToHittable(app, element: caseReport)
        caseReport.tap()
        XCTAssertTrue(caseReport.label.contains("Copied"))

        let prepareExport = app.buttons["Prepare export"]
        scrollToHittable(app, element: prepareExport)
        prepareExport.tap()
        let shareReport = app.buttons["Share report ZIP"]
        let shareText = app.staticTexts["Share report ZIP"]
        XCTAssertTrue((shareReport.waitForExistence(timeout: 10) || shareText.waitForExistence(timeout: 10)))
    }

    func testSyntheticCaseCampaignFilterShowsMatchedPayloads() {
        let app = XCUIApplication(); app.launchArguments += ["--synthetic-case"]; app.launch()
        XCTAssertTrue(app.navigationBars["Case"].waitForExistence(timeout: 5))

        let filter = app.buttons["campaignFilter"]
        scrollToHittable(app, element: filter)
        filter.tap()

        let darkSword = app.buttons["DarkSword (1)"]
        XCTAssertTrue(darkSword.waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["Pegasus (1)"].exists)
        darkSword.tap()
        XCTAssertFalse(app.staticTexts["pegasus-synthetic.invalid"].waitForExistence(timeout: 1))

        let copyPayload = app.buttons["copyPayload-synthetic-darksword"]
        scrollToHittable(app, element: copyPayload)
        XCTAssertTrue(app.staticTexts["darksword-synthetic.invalid"].exists)
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
        XCTAssertTrue(settings.otherElements["AccessibilitySettingsControllerView"].waitForExistence(timeout: 15), settings.debugDescription)
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
