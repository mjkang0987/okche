import SwiftUI
import XCTest
@testable import Okche

final class OkcheTests: XCTestCase {
    /// project.yml의 MARKETING_VERSION이 저장소 맨 위 VERSION과 같은지.
    func testMarketingVersionMatchesVersionFile() throws {
        let versionFile = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("VERSION")
        let expected = try String(contentsOf: versionFile, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let actual = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        XCTAssertEqual(actual, expected)
    }

    /// 앱 기본 언어가 한국어인지(영어면 시스템 버튼·날짜가 영어로 나옴).
    func testDevelopmentLanguageIsKorean() {
        XCTAssertEqual(Bundle.main.developmentLocalization, "ko")
        XCTAssertEqual(Bundle.main.preferredLocalizations.first, "ko")
        // 문안 날짜는 지역을 따로 주지 않으므로 앱 언어로 나와야 한다.
        let day = Calendar(identifier: .gregorian).date(from: DateComponents(year: 2026, month: 10, day: 28))!
        XCTAssertEqual(day.formatted(.dateTime.month().day().weekday(.wide)), "10월 28일 수요일")
    }

    /// 고운바탕이 앱에 들어가 이름으로 찾아지는지(없으면 제목이 시스템 글꼴로 바뀌어도 티가 안 남).
    func testDisplayFontIsBundled() throws {
        let name = try XCTUnwrap(OkcheFontFamily.display.fontName)
        XCTAssertNotNil(UIFont(name: name, size: 28))
        XCTAssertNotNil(Bundle.main.url(forResource: "OFL-GowunBatang", withExtension: "txt"))
    }

    /// 에셋 색(시작 화면 바탕·강조색)이 토큰과 같은지. 에셋 JSON에는 토큰을 쓸 수 없어 값을 따로 적었다.
    func testAssetColorsMatchTokens() {
        assertSame(UIColor(named: "Hanji", in: Bundle.main, compatibleWith: nil), OkcheColor.hanji)
        assertSame(UIColor(named: "AccentColor", in: Bundle.main, compatibleWith: nil), OkcheColor.namsaek)
    }

    private func assertSame(_ asset: UIColor?, _ token: Color, file: StaticString = #filePath, line: UInt = #line) {
        guard let asset else { return XCTFail("에셋 색이 없음", file: file, line: line) }
        let a = rgba(asset), t = rgba(UIColor(token))
        for i in 0..<4 { XCTAssertEqual(a[i], t[i], accuracy: 0.002, file: file, line: line) }
    }

    private func rgba(_ color: UIColor) -> [CGFloat] {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        return [r, g, b, a]
    }
}
