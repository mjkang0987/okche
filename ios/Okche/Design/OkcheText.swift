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
    let weight: Font.Weight
    @ScaledMetric private var size: CGFloat
    @ScaledMetric private var lineHeight: CGFloat

    init(_ style: OkcheTextStyle, weight: Font.Weight?) {
        self.style = style
        self.weight = weight ?? style.weight
        // 큰 글자는 시스템 제목처럼 덜 커지게, 크기에 맞는 시스템 글자 단계를 기준으로 키운다.
        let relative: Font.TextStyle = switch style.size {
        case 28...: .largeTitle
        case 22..<28: .title
        case 18..<22: .title3
        case ..<14: .caption
        default: .body
        }
        _size = ScaledMetric(wrappedValue: style.size, relativeTo: relative)
        _lineHeight = ScaledMetric(wrappedValue: style.lineHeight, relativeTo: relative)
    }

    func body(content: Content) -> some View {
        let uiFont = style.family.fontName.flatMap { UIFont(name: $0, size: size) }
            ?? .systemFont(ofSize: size, weight: weight.uiWeight)
        content
            .font(Font(uiFont))
            .lineSpacing(max(0, lineHeight - uiFont.lineHeight))
    }
}

private extension Font.Weight {
    var uiWeight: UIFont.Weight {
        switch self {
        case .black: .black
        case .heavy: .heavy
        case .bold: .bold
        case .semibold: .semibold
        case .medium: .medium
        case .light: .light
        case .thin: .thin
        case .ultraLight: .ultraLight
        default: .regular
        }
    }
}

extension View {
    /// `weight`는 토큰과 굵기만 다를 때(예: 쉬운 말은 label 크기에 보통 굵기).
    func okcheText(_ style: OkcheTextStyle, weight: Font.Weight? = nil) -> some View {
        modifier(OkcheTextModifier(style, weight: weight))
    }
}
