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
