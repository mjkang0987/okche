import SwiftUI
import UIKit

extension OkcheFontFamily {
    /// 앱에 넣은 글꼴 파일 이름. 없으면 시스템 글꼴로 그린다.
    var fontName: String? {
        switch self {
        case .display: "GowunBatang-Bold"
        case .sans: nil // 디자인 README: 앱에서는 시스템 글꼴을 써도 됨
        }
    }
}

/// `OkcheType` 글자 스타일. 크기·줄 높이는 시스템 글자 크기 설정을 따라 커진다.
private struct OkcheTextModifier: ViewModifier {
    let style: OkcheTextStyle
    @ScaledMetric private var size: CGFloat
    @ScaledMetric private var lineHeight: CGFloat

    init(_ style: OkcheTextStyle) {
        self.style = style
        _size = ScaledMetric(wrappedValue: style.size, relativeTo: .body)
        _lineHeight = ScaledMetric(wrappedValue: style.lineHeight, relativeTo: .body)
    }

    func body(content: Content) -> some View {
        let uiFont = style.family.fontName.flatMap { UIFont(name: $0, size: size) }
            ?? .systemFont(ofSize: size, weight: style.weight.uiWeight)
        content
            .font(Font(uiFont))
            .lineSpacing(max(0, lineHeight - uiFont.lineHeight))
    }
}

private extension Font.Weight {
    var uiWeight: UIFont.Weight {
        switch self {
        case .bold: .bold
        case .medium: .medium
        default: .regular
        }
    }
}

extension View {
    func okcheText(_ style: OkcheTextStyle) -> some View {
        modifier(OkcheTextModifier(style))
    }
}
