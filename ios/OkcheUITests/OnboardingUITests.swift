import XCTest

/// 첫 실행 → 아이 등록 → 탭 화면 흐름. 앱은 `-ui-testing`이면 빈 메모리 저장소로 뜬다.
@MainActor
final class OnboardingUITests: XCTestCase {
    private func launch(keepSettings: Bool = false, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"] + (keepSettings ? ["-keep-settings"] : []) + extra
        app.launch()
        return app
    }

    func testRegisterGoesToTabs() {
        let app = launch()
        app.buttons["아이 등록하고 시작하기"].tap()
        XCTAssertTrue(app.staticTexts["새 아기씨 모시기"].waitForExistence(timeout: 5))

        // 이름이 없으면 '모시기'는 흐리고 못 누름 + 남은 것 안내.
        XCTAssertFalse(app.buttons["모시기"].firstMatch.isEnabled)
        XCTAssertTrue(app.descendants(matching: .any)["이름을 넣어 주세요"].firstMatch.waitForExistence(timeout: 3))

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
        reveal(diabetes, in: app)
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

    /// 칸을 고치면 그 칸 오류만 사라지고, 생일은 고른 뒤 취소하면 그대로·지우기면 안 고름. 시간 고르기 이름.
    func testErrorsClearAndBirthCanBeCleared() {
        let app = launch()
        app.buttons["아이 등록하고 시작하기"].tap()
        let weight = app.textFields["체중 (kg)"]
        XCTAssertTrue(weight.waitForExistence(timeout: 5))
        weight.tap()
        weight.typeText("0")
        XCTAssertTrue(app.descendants(matching: .any)["이름을 넣어 주세요"].firstMatch.waitForExistence(timeout: 3)) // 남은 것 안내
        app.textFields["이름"].tap()
        app.textFields["이름"].typeText("콩")
        XCTAssertFalse(app.staticTexts["이름을 넣어 주세요"].exists)
        XCTAssertTrue(app.staticTexts["0보다 크고 100 이하인 숫자로 넣어 주세요"].exists)
        app.buttons["완료"].firstMatch.tap()

        let birth = app.buttons["생일"]
        reveal(birth, in: app)
        birth.tap()
        XCTAssertTrue(app.buttons["고르기"].waitForExistence(timeout: 3))
        app.buttons["고르기"].tap()
        XCTAssertNotEqual(birth.value as? String, "안 고름")
        birth.tap()
        app.buttons["취소"].tap()
        XCTAssertNotEqual(birth.value as? String, "안 고름")
        birth.tap()
        app.buttons["지우기"].tap()
        XCTAssertEqual(birth.value as? String, "안 고름")

        let diabetes = app.switches["당뇨가 있어요"].firstMatch
        reveal(diabetes, in: app)
        diabetes.tap()
        let first = app.buttons["첫 번째 시간"]
        XCTAssertTrue(first.waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["시간 선택기"].exists)
        first.tap()
        XCTAssertTrue(app.buttons["고르기"].waitForExistence(timeout: 3))
        app.buttons["취소"].tap()
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
        let unknown = app.switches["생일을 몰라요 (나이를 어림잡아 넣을게요)"].firstMatch
        reveal(unknown, in: app)
        unknown.tap()
        XCTAssertTrue(app.textFields["나이 (대략)"].waitForExistence(timeout: 3))
    }

    /// 더보기 아이 관리 → 아이 정보 고치기 → 인슐린 설정 → 저장 (6-2).
    func testEditPetFromMore() {
        let app = launch()
        app.buttons["아이 등록하고 시작하기"].tap()
        let name = app.textFields["이름"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("콩이")
        app.buttons["모시기"].firstMatch.tap()
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()

        app.buttons["콩이 아기씨"].tap()
        let editName = app.textFields["이름"]
        XCTAssertTrue(editName.waitForExistence(timeout: 5))
        editName.tap()
        editName.typeText("이")
        app.buttons["완료"].firstMatch.tap()

        // 당뇨를 켜고 인슐린 없이 저장하면 오류
        let diabetes = app.switches["당뇨가 있어요"].firstMatch
        reveal(diabetes, in: app)
        diabetes.tap()
        let save = app.buttons["저장하기"].firstMatch
        XCTAssertFalse(save.isEnabled) // 인슐린 설정 전엔 흐리게
        XCTAssertTrue(app.staticTexts["인슐린 시간과 양을 먼저 설정해 주세요"].waitForExistence(timeout: 3))

        let setupLink = app.buttons["인슐린 이름·양·시간 설정"]
        while !setupLink.isHittable { app.swipeDown() }
        setupLink.tap()
        for nth in ["첫 번째", "두 번째"] {
            let units = app.textFields["\(nth) 양 (단위)"]
            XCTAssertTrue(units.waitForExistence(timeout: 3))
            reveal(units, in: app)
            units.tap()
            units.typeText("2")
            app.buttons["완료"].firstMatch.tap() // 자판이 다음 칸을 가리지 않게
        }
        let setupSave = app.buttons["저장하기"].firstMatch
        while !setupSave.isHittable { app.swipeUp() }
        setupSave.tap()

        // 아이 정보로 돌아오면 설정 오류는 사라지고, 저장하면 더보기 줄 이름이 바뀐다.
        XCTAssertTrue(save.waitForExistence(timeout: 3))
        XCTAssertFalse(app.staticTexts["인슐린 시간과 양을 먼저 설정해 주세요"].exists)
        while !save.isHittable { app.swipeUp() }
        save.tap()
        XCTAssertTrue(app.buttons["콩이이 아기씨"].waitForExistence(timeout: 5))
    }

    /// 낙관 → 아이 바꾸기 시트 → 새로 모심(바로 지금 보는 아이) → 다른 아이 고르기, 실록 제목, 더보기 휴양 스위치 (6-3).
    func testSwitchPets() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        let seal = app.buttons["지금 보는 아이 콩이 아기씨 · 아이 바꾸기"]
        XCTAssertTrue(seal.waitForExistence(timeout: 5))

        seal.tap()
        XCTAssertTrue(app.staticTexts["어느 아이를 볼까요?"].waitForExistence(timeout: 3))
        register("보리", in: app, from: app.buttons["+ 새 아기씨 모시기"])
        XCTAssertTrue(app.buttons["지금 보는 아이 보리 아기씨 · 아이 바꾸기"].waitForExistence(timeout: 5))

        app.buttons["지금 보는 아이 보리 아기씨 · 아이 바꾸기"].tap()
        let kong = app.buttons["콩이 아기씨, 고양이"]
        XCTAssertTrue(kong.waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["보리 아기씨, 고양이"].isSelected)
        kong.tap()
        XCTAssertTrue(seal.waitForExistence(timeout: 3))

        app.tabBars.buttons["실록"].tap()
        XCTAssertTrue(app.staticTexts["콩이실록"].waitForExistence(timeout: 3))

        app.tabBars.buttons["더보기"].tap()
        let rest = app.switches["콩이 아기씨 휴양 중 · 관리 쉬기"]
        XCTAssertTrue(rest.waitForExistence(timeout: 3))
        rest.switches.firstMatch.tap()
        XCTAssertTrue(app.buttons["콩이 아기씨 · 휴양 중"].waitForExistence(timeout: 3))
    }

    /// 먼저 둘러보기(아이 없음): 낙관 자리와 더보기에 '+ 새 아기씨 모시기' (6-3).
    func testNoPetShowsRegisterButtons() {
        let app = launch()
        app.buttons["먼저 둘러볼게요"].tap()
        XCTAssertTrue(app.buttons["+ 새 아기씨 모시기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["문안"].tap()
        XCTAssertTrue(app.staticTexts["아직 모신 아이가 없어요. 아기씨를 모시면 여기에서 날마다 안부를 여쭤요."].waitForExistence(timeout: 3))
        XCTAssertEqual(app.buttons.matching(identifier: "+ 새 아기씨 모시기").count, 2) // 머리 알약 + 점선 버튼
        app.tabBars.buttons["더보기"].tap()
        XCTAssertTrue(app.staticTexts["아직 모신 아이가 없어요."].waitForExistence(timeout: 3))
        register("콩이", in: app, from: app.buttons["+ 새 아기씨 모시기"])
        XCTAssertTrue(app.buttons["콩이 아기씨"].waitForExistence(timeout: 5))
    }

    /// 아이 정보 → 추억으로 간직(문안에 추억 화면) → 추억에서 되돌리기 → 지우기(더보기로) (6-4).
    func testKeepRestoreAndDelete() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        app.buttons["콩이 아기씨"].tap()

        tapBelow(app.buttons["추억으로 간직하기"], in: app)
        XCTAssertTrue(app.staticTexts["콩이의 기록을 추억으로 간직할게요"].waitForExistence(timeout: 3))
        app.buttons["간직하기"].tap()
        let memorial = app.staticTexts.containing(NSPredicate(format: "label CONTAINS '추억으로 간직 중'")).firstMatch
        XCTAssertTrue(memorial.waitForExistence(timeout: 5))
        XCTAssertTrue(app.tabBars.buttons["문안"].isSelected)
        XCTAssertTrue(app.staticTexts["함께한 날"].exists || app.otherElements.containing(NSPredicate(format: "label BEGINSWITH '처음 기록'")).firstMatch.exists)

        app.tabBars.buttons["더보기"].tap()
        let keptRow = app.buttons["콩이 · 추억으로 간직 중"]
        XCTAssertTrue(keptRow.waitForExistence(timeout: 3))
        XCTAssertFalse(app.switches["콩이 휴양 중 · 관리 쉬기"].exists) // 추억 아이는 휴양 스위치 없음
        keptRow.tap()
        tapBelow(app.buttons["추억에서 되돌리기"], in: app)
        XCTAssertTrue(app.staticTexts["콩이를 다시 모실까요?"].waitForExistence(timeout: 3))
        app.buttons["다시 모시기"].tap()
        XCTAssertTrue(app.buttons["추억으로 간직하기"].waitForExistence(timeout: 3))

        tapBelow(app.buttons["이 아이 기록 지우기"], in: app)
        XCTAssertTrue(app.staticTexts["콩이의 기록을 지울까요?"].waitForExistence(timeout: 3))
        app.buttons["지우기"].tap()
        XCTAssertTrue(app.staticTexts["아직 모신 아이가 없어요."].waitForExistence(timeout: 5))
    }

    /// 누군지 몰라요로 바로 기록 → 방금 남긴 기록 → 기록 목록에서 누구였는지 정하기 → 지우기 (7-1).
    func testQuickRecordAssignAndDelete() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        let unknown = app.buttons["누군지 몰라요"]
        XCTAssertTrue(unknown.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["콩이"].isSelected) // 지금 보는 아이가 먼저 골라져 있다
        unknown.tap()
        app.buttons["구토 사료 · 바로 기록"].tap()
        XCTAssertTrue(app.staticTexts["아뢰었사옵니다"].waitForExistence(timeout: 3))

        tapBelow(app.buttons["오늘 일기 전체"], in: app)
        XCTAssertTrue(app.staticTexts["누군지 모름 · 구토"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["구토 1번"].exists)
        app.buttons["누구였는지 정하기"].tap()
        XCTAssertTrue(app.staticTexts["누구였나요?"].waitForExistence(timeout: 3))
        app.buttons["콩이"].firstMatch.tap()
        app.buttons["정하기"].tap()
        XCTAssertTrue(app.staticTexts["콩이 · 구토"].waitForExistence(timeout: 3))

        app.buttons.matching(NSPredicate(format: "label ENDSWITH '콩이 구토 기록 지우기'")).firstMatch.tap()
        XCTAssertTrue(app.staticTexts["이 기록을 지울까요?"].waitForExistence(timeout: 3))
        app.buttons["지우기"].tap()
        XCTAssertTrue(app.staticTexts["이날 남긴 기록이 없어요."].waitForExistence(timeout: 3))
        app.buttons["뒤로"].tap()
        XCTAssertTrue(unknown.waitForExistence(timeout: 3))
        XCTAssertTrue(unknown.isSelected) // 목록을 다녀와도 '누군지 몰라요' 그대로
        XCTAssertFalse(app.staticTexts["방금 남긴 기록"].exists) // 오늘 기록이 없으면 숨김
    }

    /// ⋯ → 구토 시트 → 붉은색(의서 레이어, 시트 한 번에 한 번) → 이어 하기 → 기록하기 → 연필로 고치기 (7-2).
    func testDetailSheetAlertAndEdit() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        let more = app.buttons["구토 자세히 남기기"]
        XCTAssertTrue(more.waitForExistence(timeout: 5))
        more.tap()
        XCTAssertTrue(app.staticTexts["콩이 · 구토"].waitForExistence(timeout: 3))
        app.buttons["사료 알갱이"].tap()
        app.buttons["헤어볼"].tap()
        app.buttons["붉은색 (피)"].tap()
        XCTAssertTrue(app.staticTexts["의서의 '지금 바로 병원에' 목록이에요."].waitForExistence(timeout: 3))
        app.buttons["기록 이어 하기"].tap()
        tapBelow(app.buttons["3번 이상"], in: app)
        XCTAssertFalse(app.staticTexts["의서의 '지금 바로 병원에' 목록이에요."].waitForExistence(timeout: 1)) // 한 번만
        tapBelow(app.buttons["기록하기"], in: app)
        let line = app.staticTexts.containing(NSPredicate(format: "label CONTAINS '사료 · 헤어볼 · 붉은색 · 3번 이상'")).firstMatch
        XCTAssertTrue(line.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["아뢰었사옵니다"].exists)

        app.buttons.matching(NSPredicate(format: "label ENDSWITH '콩이 구토 기록 고치기'")).firstMatch.tap()
        XCTAssertTrue(app.staticTexts["콩이 · 구토"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["헤어볼"].isSelected) // 값이 채워진 채
        app.buttons["헤어볼"].tap()
        app.buttons["이물질 (끈·비닐 등)"].tap() // 고치기 시트에서도 처음 한 번 레이어
        XCTAssertTrue(app.buttons["기록 이어 하기"].waitForExistence(timeout: 3))
        app.buttons["기록 이어 하기"].tap()
        XCTAssertTrue(app.buttons["이물질 (끈·비닐 등)"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["이물질 (끈·비닐 등)"].isSelected) // 레이어를 닫아도 고른 값 그대로
        tapBelow(app.buttons["기록하기"], in: app)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '사료 · 이물질 · 붉은색 · 3번 이상'")).firstMatch.waitForExistence(timeout: 5))
    }

    /// 수라 ⋯ → 끼니 없음(지금 먹은 것·오늘 남긴 수라) → 끼니 시간 정하기 → 끼니 칸 남김 → 급식기로 바꾸기 (7-3).
    func testSuraNoPlanThenPlanThenFeeder() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        let more = app.buttons["수라 자세히 남기기"]
        XCTAssertTrue(more.waitForExistence(timeout: 5))
        more.tap()
        XCTAssertTrue(app.staticTexts["끼니 시간을 정해 둘까요?"].waitForExistence(timeout: 3))
        app.buttons["지금 남김"].tap()
        XCTAssertTrue(app.staticTexts["오늘 남긴 수라"].waitForExistence(timeout: 3))
        app.buttons["괜찮아요"].tap()
        XCTAssertFalse(app.staticTexts["끼니 시간을 정해 둘까요?"].waitForExistence(timeout: 1)) // 다시 안 뜸

        tapBelow(app.buttons["끼니 시간 정하기 ›"], in: app)
        XCTAssertTrue(app.buttons["아침 시간"].waitForExistence(timeout: 3))
        tapBelow(app.buttons["저장하기"], in: app)
        XCTAssertTrue(app.buttons["끼니 시간 바꾸기"].waitForExistence(timeout: 3))
        // 오늘 정한 끼니는 아직 지나지 않은 것만 칸이 생긴다(18시 뒤엔 없음) — 있으면 남김까지.
        let some = app.buttons["남김"].firstMatch
        if some.waitForExistence(timeout: 2) {
            some.tap()
            XCTAssertTrue(app.textFields.matching(NSPredicate(format: "label ENDSWITH '남은 양 (그램, 선택)'")).firstMatch.waitForExistence(timeout: 3))
        } else {
            XCTAssertTrue(app.staticTexts["오늘은 남은 끼니가 없어요. 내일부터 끼니마다 남겨요."].exists)
        }

        app.buttons["급식기 (수라간)"].tap()
        XCTAssertTrue(app.buttons["급식기 시간 바꾸기"].waitForExistence(timeout: 3))
    }

    /// 기록 목록의 수라 연필 → 수라 화면 → 뒤로 → 기록 목록으로 돌아옴(전체 리뷰).
    func testMealPencilOpensSuraAndBackToLog() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        let all = app.buttons["수라 다 먹음 · 바로 기록"]
        XCTAssertTrue(all.waitForExistence(timeout: 5))
        all.tap()
        tapBelow(app.buttons["오늘 일기 전체"], in: app)
        let pencil = app.buttons.matching(NSPredicate(format: "label ENDSWITH '콩이 수라 기록 고치기'")).firstMatch
        XCTAssertTrue(pencil.waitForExistence(timeout: 3))
        pencil.tap()
        XCTAssertTrue(app.staticTexts["지금 먹은 것"].waitForExistence(timeout: 5))
        app.buttons["뒤로"].firstMatch.tap()
        XCTAssertTrue(app.buttons["전날"].waitForExistence(timeout: 3)) // 기록 목록
    }

    /// 문안: 기록 없음 안내 → '기록하기'로 일기 → 바로 기록 → 문안 오늘 기록 줄(소피 → 고치기 시트, 수라 → 수라 화면) (7-0).
    func testMainTodayRecords() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["문안"].waitForExistence(timeout: 5))
        app.tabBars.buttons["문안"].tap()
        XCTAssertTrue(app.staticTexts["콩이 아기씨, 오늘도 강녕하시옵소서"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["아직 오늘 남긴 기록이 없어요."].exists)
        tapBelow(app.buttons["기록하기"], in: app)
        let urine = app.buttons["소피 보통 · 바로 기록"]
        XCTAssertTrue(urine.waitForExistence(timeout: 3)) // 일기 탭
        urine.tap()
        app.buttons["수라 다 먹음 · 바로 기록"].tap()
        app.tabBars.buttons["문안"].tap()
        let urineRow = app.buttons.matching(NSPredicate(format: "label ENDSWITH '소피 보통 · 고치기'")).firstMatch
        tapBelow(urineRow, in: app)
        XCTAssertTrue(app.staticTexts["콩이 · 소피"].waitForExistence(timeout: 3))
        app.buttons["닫기"].firstMatch.tap()
        tapBelow(app.buttons.matching(NSPredicate(format: "label ENDSWITH '수라 다 먹음 · 고치기'")).firstMatch, in: app)
        XCTAssertTrue(app.staticTexts["지금 먹은 것"].waitForExistence(timeout: 5))
        app.buttons["뒤로"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["콩이 아기씨, 오늘도 강녕하시옵소서"].waitForExistence(timeout: 3))
    }

    /// 더보기 '사료 · 처방식' → 사료 바꾸기 시트(이름 없으면 알림) → 바꾸기 → 아이 정보 '지금 먹는 사료' 줄 (7-4a).
    func testFoodChangeFromMore() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["사료 · 처방식"], in: app)
        XCTAssertTrue(app.staticTexts["콩이 · 사료 바꾸기"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["바꾸기"].isEnabled) // 덜 채우면 흐리게 + 남은 것
        XCTAssertTrue(app.staticTexts["사료 이름을 넣으면 바꿀 수 있어요"].waitForExistence(timeout: 3))
        let field = app.textFields["사료 이름"]
        field.tap()
        field.typeText("가수분해 처방식")
        app.buttons["바꾸기"].tap()
        XCTAssertFalse(app.staticTexts["콩이 · 사료 바꾸기"].waitForExistence(timeout: 2)) // 닫힘
        app.buttons["콩이 아기씨"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '가수분해 처방식 · '")).firstMatch.waitForExistence(timeout: 5))
    }

    /// 수라 '+ 다과 · 간식 남기기' → 새 다과 들이고 고르기 → 기록 → 일기 목록 값 → 더보기 다과 목록 → 고치기 → 목록에서 빼기 (7-4b).
    func testSnackRecordAndList() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        let more = app.buttons["수라 자세히 남기기"]
        XCTAssertTrue(more.waitForExistence(timeout: 5))
        more.tap()
        tapBelow(app.buttons["+ 다과 · 간식 남기기"], in: app)
        XCTAssertTrue(app.buttons["+ 새 다과 들이기"].waitForExistence(timeout: 3)) // 다과가 없으면 들이기만
        XCTAssertFalse(app.buttons["기록하기"].isEnabled) // 덜 채우면 흐리게 + 남은 것
        XCTAssertTrue(app.staticTexts["어떤 다과를 줬는지 고르면 기록할 수 있어요"].waitForExistence(timeout: 3))
        app.buttons["+ 새 다과 들이기"].tap()
        let name = app.textFields["간식 이름"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("연어 퓨레")
        tapBelow(app.buttons["다과에 들이고 고르기"], in: app)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH '연어 퓨레'")).firstMatch.waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH '연어 퓨레'")).firstMatch.isSelected) // 바로 골라짐
        let amount = app.textFields["얼마나 줬나요? (선택)"]
        amount.tap()
        amount.typeText("반 개")
        tapBelow(app.buttons["기록하기"], in: app)
        // 수라 화면에 방금 남긴 다과가 보임
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '다과 연어 퓨레 · 반 개' AND label CONTAINS '아뢰었사옵니다'")).firstMatch.waitForExistence(timeout: 3))
        app.buttons["뒤로"].firstMatch.tap()
        tapBelow(app.buttons["오늘 일기 전체"], in: app)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '연어 퓨레 · 반 개'")).firstMatch.waitForExistence(timeout: 3))
        app.buttons["뒤로"].firstMatch.tap() // 기록 목록엔 탭 막대가 없음

        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 3))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["다과 · 간식 목록"], in: app)
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH '연어 퓨레'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        XCTAssertTrue(row.label.contains("이번 달 1번"))
        row.tap()
        XCTAssertTrue(app.staticTexts["다과 고치기"].waitForExistence(timeout: 3))
        tapBelow(app.buttons["목록에서 빼기"], in: app)
        XCTAssertTrue(app.staticTexts["연어 퓨레를 목록에서 뺄까요?"].waitForExistence(timeout: 3))
        app.buttons["빼기"].tap()
        XCTAssertFalse(row.waitForExistence(timeout: 2))
    }

    /// 더보기 → 약방(약 없음) → 들이기(오류 → 저장) → 줄 → 고치기 → 약방에서 빼기 확인 (8-1).
    func testYakbangAddEditRemove() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["약방 · 약 목록"], in: app)
        XCTAssertTrue(app.staticTexts["아직 들인 약이 없어요."].waitForExistence(timeout: 3))
        app.buttons["+ 약 들이기"].tap()
        XCTAssertTrue(app.staticTexts["약방에 약 들이기"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["약방에 들이기"].isEnabled) // 덜 채우면 흐리게 + 남은 것
        XCTAssertTrue(app.staticTexts["약 이름을 넣어 주세요"].waitForExistence(timeout: 3))
        let name = app.textFields["약 이름"]
        name.tap()
        name.typeText("위장약 시럽")
        app.buttons["물약"].tap()
        let stock = app.textFields["지금 남은 양 (선택)"]
        tapBelow(stock, in: app)
        stock.typeText("120")
        app.buttons["약방에 들이기"].tap()
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH '위장약 시럽'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        XCTAssertTrue(row.label.contains("물약 · 1ml씩 · 남은 120ml"))
        row.tap()
        XCTAssertTrue(app.staticTexts["약 고치기"].waitForExistence(timeout: 3))
        tapBelow(app.buttons["약방에서 빼기"], in: app)
        XCTAssertTrue(app.staticTexts["위장약 시럽을 약방에서 뺄까요?"].waitForExistence(timeout: 3))
        app.buttons["빼기"].tap()
        XCTAssertTrue(app.staticTexts["아직 들인 약이 없어요."].waitForExistence(timeout: 3))
    }

    /// 더보기 → 탕약(약 없음) → 약 추가(오류 → 새 약 들이고 고르기 → 며칠마다) → 저장 → 카드 (8-2a).
    func testTangyakAddMed() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["탕약 · 약 알림"], in: app)
        XCTAssertTrue(app.staticTexts["아직 먹이는 약이 없어요. 약을 추가하면 일기 탕약 줄과 알림에 나와요."].waitForExistence(timeout: 3))
        app.buttons["+ 약 추가"].tap()
        XCTAssertFalse(app.buttons["저장하기"].isEnabled) // 약을 고르기 전엔 흐리게 + 남은 것
        XCTAssertTrue(app.descendants(matching: .any)["약을 골라 주세요"].firstMatch.waitForExistence(timeout: 3))
        tapBelow(app.buttons["+ 약방에 새 약 들이기"], in: app)
        let name = app.textFields["약 이름"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("프레드니솔론")
        app.buttons["약방에 들이고 고르기"].tap()
        let picked = app.buttons.matching(NSPredicate(format: "label BEGINSWITH '프레드니솔론'")).firstMatch
        XCTAssertTrue(picked.waitForExistence(timeout: 3))
        XCTAssertTrue(picked.isSelected) // 들이면 골라짐
        tapBelow(app.buttons["며칠마다"], in: app)
        tapBelow(app.buttons["저장하기"], in: app)
        XCTAssertTrue(app.staticTexts["먹고 있는 약"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '콩이 · 이틀에 한 번 · 1알'")).firstMatch.exists)
        // 카드 → 고치기(매일로) → 다시 카드 → 이 약 그만 먹이기 (8-2b)
        app.buttons.matching(NSPredicate(format: "label ENDSWITH '· 고치기'")).firstMatch.tap()
        XCTAssertTrue(app.staticTexts["아이나 약을 바꾸려면 이 약을 그만 먹이고 새로 추가해 주세요."].waitForExistence(timeout: 3))
        tapBelow(app.buttons["매일"], in: app)
        tapBelow(app.buttons["저장하기"], in: app)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '콩이 · 하루 1번 · 1알'")).firstMatch.waitForExistence(timeout: 3))
        app.buttons.matching(NSPredicate(format: "label ENDSWITH '· 고치기'")).firstMatch.tap()
        tapBelow(app.buttons["이 약 그만 먹이기"], in: app)
        XCTAssertTrue(app.staticTexts["프레드니솔론을 그만 먹일까요?"].waitForExistence(timeout: 3))
        app.buttons["그만 먹이기"].tap()
        XCTAssertTrue(app.staticTexts["아직 먹이는 약이 없어요. 약을 추가하면 일기 탕약 줄과 알림에 나와요."].waitForExistence(timeout: 3))
    }

    /// 9-1: 당뇨 아이 혈당 줄 — 숫자 → 남기기 → (이번 인슐린 기록 없음) 언제 쟀나요? → 그만두기면 값 남음 → 맞고 난 뒤 → 알림·방금 남긴 기록.
    func testGlucoseLeaveAsk() {
        let app = launch()
        registerDiabetic(app)
        let field = app.textFields["혈당 수치 (mg/dL)"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["혈당 남기기"].isEnabled) // 빈칸이면 흐림
        field.tap()
        field.typeText("186")
        shot(app, "ios-glucose-row")
        app.buttons["혈당 남기기"].tap()
        XCTAssertTrue(app.staticTexts["혈당 186 · 언제 쟀나요?"].waitForExistence(timeout: 3))
        shot(app, "ios-glucose-ask")
        app.buttons["그만두기"].tap()
        XCTAssertEqual(field.value as? String, "186")
        app.buttons["혈당 남기기"].tap()
        XCTAssertTrue(app.buttons["맞고 난 뒤"].waitForExistence(timeout: 3))
        app.buttons["맞고 난 뒤"].tap()
        XCTAssertTrue(app.staticTexts["혈당 186 · 맞고 난 뒤"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '혈당 186 · 맞고 난 뒤'")).count >= 1)
        shot(app, "ios-glucose-saved")
        // ⋯ 혈당 시트: 증상을 고르면 저혈당 안내.
        app.buttons["혈당 자세히 남기기"].tap()
        XCTAssertTrue(app.staticTexts["콩이 · 혈당"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons.containing(NSPredicate(format: "label BEGINSWITH '인슐린 맞는 아이가'")).firstMatch.exists)
        app.descendants(matching: .any)["비틀거리거나 떨어요"].firstMatch.tap()
        XCTAssertTrue(app.buttons.containing(NSPredicate(format: "label BEGINSWITH '인슐린 맞는 아이가'")).firstMatch.waitForExistence(timeout: 3))
        shot(app, "ios-glucose-sheet")
    }

    /// 보드 대조용 화면 사진(테스트 결과에 남김).
    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    /// 9-2: 인슐린 줄 — 맞음 → 처리한 상태·알림 → 되돌리기(지우기 확인) → 버튼 돌아옴, ⋯ → 시트 → 기록하기 → 처리한 상태.
    func testInsulinGiveUndoAndSheet() {
        let app = launch()
        registerDiabetic(app)
        let give = app.buttons.matching(NSPredicate(format: "label ENDSWITH '단위 맞음 · 바로 기록'")).firstMatch
        XCTAssertTrue(give.waitForExistence(timeout: 5))
        reveal(give, in: app)
        shot(app, "ios-insulin-row")
        give.tap()
        let done = app.buttons.matching(NSPredicate(format: "label ENDSWITH '단위 맞음 · 눌러서 고치기'")).firstMatch
        XCTAssertTrue(done.waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH '인슐린 2단위 맞음 · '")).firstMatch.exists)
        shot(app, "ios-insulin-done")
        app.buttons["되돌리기"].tap()
        XCTAssertTrue(app.staticTexts["이 기록을 지울까요?"].waitForExistence(timeout: 3))
        app.buttons["지우기"].tap()
        XCTAssertTrue(give.waitForExistence(timeout: 3))
        app.buttons["인슐린 자세히 남기기"].tap()
        XCTAssertTrue(app.staticTexts["콩이 · 인슐린"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '정해 둔 양 2단위'")).firstMatch.exists)
        shot(app, "ios-insulin-sheet")
        app.buttons["기록하기"].tap()
        XCTAssertTrue(done.waitForExistence(timeout: 3))
    }

    /// 당뇨 아이 등록(인슐린 칸마다 2단위).
    private func registerDiabetic(_ app: XCUIApplication) {
        app.buttons["아이 등록하고 시작하기"].tap()
        let name = app.textFields["이름"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("콩이")
        let diabetes = app.switches["당뇨가 있어요"].firstMatch
        reveal(diabetes, in: app)
        diabetes.tap()
        let units = app.textFields.matching(NSPredicate(format: "label ENDSWITH '양 (단위)'"))
        XCTAssertTrue(units.firstMatch.waitForExistence(timeout: 3))
        for i in 0..<units.count {
            let field = units.element(boundBy: i)
            reveal(field, in: app)
            field.tap()
            field.typeText("2")
            if app.buttons["완료"].exists { app.buttons["완료"].tap() }
        }
        tapBelow(app.buttons["모시기"].firstMatch, in: app)
    }

    /// 8-4c: 더보기 → 기별, 스위치를 바꾸면 바로 저장(나갔다 와도 그대로).
    func testNotifySettingsSaves() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["기별 · 알림 설정"], in: app)
        let med = app.switches["탕약 시간"]
        XCTAssertTrue(med.waitForExistence(timeout: 3))
        XCTAssertEqual(med.value as? String, "1")
        med.switches.firstMatch.tap()
        XCTAssertEqual(med.value as? String, "0")
        app.buttons["뒤로"].firstMatch.tap()
        tapBelow(app.buttons["기별 · 알림 설정"], in: app)
        XCTAssertEqual(app.switches["탕약 시간"].value as? String, "0")
    }

    /// 8-4b: 알림 켠 약을 처음 저장하면 허락 안내 창 — '나중에'여도 약은 저장되고 탕약 목록으로.
    func testNotifyAskOnFirstSave() {
        let app = launch(extra: ["-notify-ask"])
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["탕약 · 약 알림"], in: app)
        app.buttons["+ 약 추가"].tap()
        tapBelow(app.buttons["+ 약방에 새 약 들이기"], in: app)
        let name = app.textFields["약 이름"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("프레드니솔론")
        app.buttons["약방에 들이고 고르기"].tap()
        tapBelow(app.buttons["저장하기"], in: app)
        XCTAssertTrue(app.staticTexts["약 먹일 시간을 알려 드릴까요?"].waitForExistence(timeout: 3))
        app.buttons["나중에"].tap()
        XCTAssertTrue(app.staticTexts["먹고 있는 약"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["프레드니솔론"].exists)
        // 아직 허락 안 함 → 탕약 맨 위 알림 꺼짐 띠(8-4c), 다음 탕약 띠는 숨김.
        XCTAssertTrue(app.staticTexts["휴대폰 알림이 꺼져 있어요"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["설정 열기"].exists)
        XCTAssertFalse(app.staticTexts["다음 탕약"].exists)
    }

    /// 몇 시간마다 약(처음 먹일 때 = 지금)을 하나 추가하고 일기 탭으로.
    private func addHoursMedAndOpenRecord(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.buttons["더보기"].waitForExistence(timeout: 5))
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["탕약 · 약 알림"], in: app)
        app.buttons["+ 약 추가"].tap()
        tapBelow(app.buttons["+ 약방에 새 약 들이기"], in: app)
        let name = app.textFields["약 이름"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("프레드니솔론")
        app.buttons["약방에 들이고 고르기"].tap()
        tapBelow(app.buttons["몇 시간마다"], in: app)
        tapBelow(app.buttons["저장하기"], in: app)
        XCTAssertTrue(app.staticTexts["먹고 있는 약"].waitForExistence(timeout: 3))
        app.buttons["뒤로"].firstMatch.tap() // 더보기 안 화면엔 탭 막대가 없음
        XCTAssertTrue(app.tabBars.buttons["일기"].waitForExistence(timeout: 3))
        app.tabBars.buttons["일기"].tap()
    }

    /// 8-3a: 일기 탕약 버튼 → 기록 알림 → '되돌리기' = 지우기 확인 창 → 지움 → '기록을 지웠어요 · 되돌리기' → 되돌리면 기록이 돌아옴.
    func testRecordMedGiveUndo() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        addHoursMedAndOpenRecord(app)
        let give = app.buttons.matching(NSPredicate(format: "label BEGINSWITH '탕약 프레드니솔론' AND label ENDSWITH '바로 기록'")).firstMatch
        XCTAssertTrue(give.waitForExistence(timeout: 3))
        give.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH '프레드니솔론 줌 · '")).firstMatch.waitForExistence(timeout: 3))
        let line = app.staticTexts.containing(NSPredicate(format: "label CONTAINS '콩이 · 탕약 프레드니솔론 1알'")).firstMatch
        XCTAssertTrue(line.exists)
        app.buttons["되돌리기"].tap()
        XCTAssertTrue(app.staticTexts["이 기록을 지울까요?"].waitForExistence(timeout: 3)) // 확인 창 한 번
        app.buttons["지우기"].tap()
        XCTAssertTrue(app.staticTexts["기록을 지웠어요"].waitForExistence(timeout: 3)) // 지운 뒤 알림
        XCTAssertTrue(line.waitForNonExistence(timeout: 5))
        app.buttons["되돌리기"].tap()
        XCTAssertTrue(line.waitForExistence(timeout: 5)) // 되돌리면 기록이 돌아옴
    }

    /// 8-3b: ⋯ 오늘 먹일 약 시트 → 줬어요 → 기록하기, 예정에 없던 약 → 기록하기, 연필 → 먹고 토함으로 고치기.
    func testMedSheetExtraEdit() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        addHoursMedAndOpenRecord(app)
        tapBelow(app.buttons["탕약 자세히 남기기"], in: app)
        XCTAssertTrue(app.staticTexts["다음 차례"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["기록하기"].isEnabled) // 고른 게 없으면 흐리게 + 안내
        XCTAssertTrue(app.staticTexts["먹인 약을 하나 이상 고르면 기록할 수 있어요"].waitForExistence(timeout: 3))
        app.buttons["줬어요"].firstMatch.tap()
        app.buttons["기록하기"].tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '콩이 · 탕약 프레드니솔론 1알'")).firstMatch.waitForExistence(timeout: 3))
        tapBelow(app.buttons["탕약 자세히 남기기"], in: app)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label ENDSWITH '에 기록함'")).firstMatch.waitForExistence(timeout: 3))
        app.buttons["+ 예정에 없던 약 남기기"].tap()
        XCTAssertTrue(app.staticTexts["정해 둔 시간 밖에 준 약이라 탕약 횟수(1/3)에는 들어가지 않아요. 약방 남은 양에서는 빠져요."].waitForExistence(timeout: 3))
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH '프레드니솔론'")).firstMatch.tap()
        let saves = app.buttons.matching(identifier: "기록하기")
        saves.element(boundBy: saves.count - 1).tap() // 위에 뜬 시트의 것
        XCTAssertTrue(app.buttons["탕약 자세히 남기기"].waitForExistence(timeout: 3)) // 두 시트 다 닫힘
        let edit = app.buttons.matching(NSPredicate(format: "label ENDSWITH '콩이 탕약 기록 고치기'")).firstMatch
        tapBelow(edit, in: app)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH '콩이 · 탕약 기록'")).firstMatch.waitForExistence(timeout: 3))
        app.buttons["먹고 토함"].tap()
        app.buttons["기록하기"].tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS '탕약 프레드니솔론 먹고 토함'")).firstMatch.waitForExistence(timeout: 3))
    }

    /// 8-3c: 문안 '예정' 줄 → 오늘 먹일 약 시트, 탕약 화면 '다음 탕약' 띠 '줬어요' → 기록 알림.
    func testMainPendingAndNextBand() {
        let app = launch()
        register("콩이", in: app, from: app.buttons["아이 등록하고 시작하기"])
        addHoursMedAndOpenRecord(app)
        app.tabBars.buttons["문안"].tap()
        let pending = app.buttons.matching(NSPredicate(format: "label ENDSWITH '오늘 먹일 약 보기'")).firstMatch
        tapBelow(pending, in: app)
        XCTAssertTrue(app.staticTexts["다음 차례"].waitForExistence(timeout: 3))
        app.buttons["닫기"].firstMatch.tap()
        app.tabBars.buttons["더보기"].tap()
        tapBelow(app.buttons["탕약 · 약 알림"], in: app)
        let band = app.buttons.matching(NSPredicate(format: "label CONTAINS '콩이 프레드니솔론 1알 줬어요'")).firstMatch
        XCTAssertTrue(band.waitForExistence(timeout: 3))
        band.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH '프레드니솔론 줌 · '")).firstMatch.waitForExistence(timeout: 3))
    }

    /// 아래쪽 버튼이 보일 때까지 밀어 올리고 누른다(최대 15번).
    private func tapBelow(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 5))
        reveal(element, in: app)
        element.tap()
    }

    /// 아래 고정 버튼 칸에 가리지 않을 만큼 밀어 올린다 — 가려져도 '누를 수 있음'으로 나와서 고정 칸이 대신 눌림.
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        let limit = app.windows.firstMatch.frame.maxY - 220
        for _ in 0..<15 where !element.isHittable || element.frame.maxY > limit { app.swipeUp() }
    }

    /// 아이 등록 화면을 열어 이름만 넣고 모신다.
    private func register(_ name: String, in app: XCUIApplication, from button: XCUIElement) {
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
        let field = app.textFields["이름"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(name)
        app.buttons["모시기"].firstMatch.tap()
    }
}
