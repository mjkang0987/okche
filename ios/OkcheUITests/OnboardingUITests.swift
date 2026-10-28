import XCTest

/// 첫 실행 → 아이 등록 → 탭 화면 흐름. 앱은 `-ui-testing`이면 빈 메모리 저장소로 뜬다.
@MainActor
final class OnboardingUITests: XCTestCase {
    private func launch(keepSettings: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"] + (keepSettings ? ["-keep-settings"] : [])
        app.launch()
        return app
    }

    func testRegisterGoesToTabs() {
        let app = launch()
        app.buttons["아이 등록하고 시작하기"].tap()
        XCTAssertTrue(app.staticTexts["새 아기씨 모시기"].waitForExistence(timeout: 5))

        // 이름 없이 모시면 오류가 뜨고 그대로 남는다.
        app.buttons["모시기"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["이름을 넣어 주세요"].waitForExistence(timeout: 3))

        let name = app.textFields["이름"]
        name.tap()
        name.typeText("콩이")
        // 당뇨를 켜면 인슐린 칸이 생긴다.
        app.switches["당뇨가 있어요"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["인슐린"].waitForExistence(timeout: 3))
        app.switches["당뇨가 있어요"].firstMatch.tap()

        app.buttons["모시기"].firstMatch.tap()
        XCTAssertTrue(app.tabBars.buttons["일기"].waitForExistence(timeout: 5))
    }

    /// 가장 큰 글자(AX5)에서 당뇨를 켜도 화면이 옆으로 넘치지 않는다(인슐린 줄이 세로로 쌓임).
    func testLargestTextDoesNotOverflow() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        let start = app.buttons["아이 등록하고 시작하기"]
        while !start.isHittable { app.swipeUp() }
        start.tap()
        let diabetes = app.switches["당뇨가 있어요"].firstMatch
        XCTAssertTrue(diabetes.waitForExistence(timeout: 5))
        while !diabetes.isHittable { app.swipeUp() }
        diabetes.tap()
        let units = app.textFields["첫 번째 양 (단위)"]
        XCTAssertTrue(units.waitForExistence(timeout: 3))
        let width = app.windows.firstMatch.frame.width
        for element in [units, app.buttons["첫 번째 시간 빼기"], app.staticTexts["인슐린"]] {
            XCTAssertGreaterThanOrEqual(element.frame.minX, 0, element.debugDescription)
            XCTAssertLessThanOrEqual(element.frame.maxX, width + 0.5, element.debugDescription)
        }
        XCTAssertGreaterThanOrEqual(app.buttons["닫기"].frame.minX, 0)
    }

    func testBrowseFirstSkipsOnboarding() {
        let app = launch()
        app.buttons["먼저 둘러볼게요"].tap()
        XCTAssertTrue(app.tabBars.buttons["일기"].waitForExistence(timeout: 5))
        // 다시 실행해도(아이는 여전히 없음) 시작하기가 다시 뜨지 않는다.
        app.terminate()
        let again = launch(keepSettings: true)
        XCTAssertTrue(again.tabBars.buttons["일기"].waitForExistence(timeout: 5))
        XCTAssertFalse(again.buttons["먼저 둘러볼게요"].exists)
    }

    func testSpeciesSwitchesBreedList() {
        let app = launch()
        app.buttons["아이 등록하고 시작하기"].tap()
        XCTAssertTrue(app.buttons["품종"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["품종"].value as? String, "코리안숏헤어")
        app.buttons["강아지"].tap()
        XCTAssertEqual(app.buttons["품종"].value as? String, "말티즈")
        XCTAssertTrue(app.buttons["누런색"].exists)
        // 생일을 모르면 나이 칸
        app.switches["생일을 몰라요 (나이를 어림잡아 넣을게요)"].firstMatch.tap()
        XCTAssertTrue(app.textFields["나이 (대략)"].waitForExistence(timeout: 3))
    }
}
