import SwiftUI

// 입력 화면 공용 컴포넌트(6-1에서 사용자 승인). 네이티브 요소를 디자인 값으로 감싼다.

/// 입력칸 테두리: 52 높이, 흰 바탕, 진한 선 1.5, 모서리 8. 오류면 연지색 선.
struct OkcheFieldBox: ViewModifier {
    var isError = false
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 52

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .frame(minHeight: height)
            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusSm))
            .overlay(
                RoundedRectangle(cornerRadius: OkcheRadius.radiusSm)
                    .strokeBorder(isError ? OkcheColor.yeonji : OkcheColor.lineStrong, lineWidth: 1.5)
            )
    }
}

extension View {
    func okcheFieldBox(isError: Bool = false) -> some View { modifier(OkcheFieldBox(isError: isError)) }
}

/// 칸 위 이름표(14, 중간 굵기, 흐린 글자). `note`는 '(선택)'처럼 보통 굵기로 붙는 말.
struct OkcheFieldLabel: View {
    let title: String
    var note: String?

    var body: some View {
        (Text(title) + Text(note.map { " \($0)" } ?? "").fontWeight(.regular))
            .okcheText(OkcheType.label)
            .foregroundStyle(OkcheColor.inkMuted)
    }
}

/// 칸 아래 오류 글자. 화면 읽기 프로그램이 바로 읽도록 알린다.
struct OkcheFieldError: View {
    let message: String?

    var body: some View {
        if let message {
            Text(message)
                .okcheText(OkcheType.label, weight: .regular)
                .foregroundStyle(OkcheColor.yeonji)
                .onAppear { AccessibilityNotification.Announcement(message).post() }
        }
    }
}

/// ① 입력칸: 이름표 + 글자 칸(+ 끝에 단위 글자) + 오류.
struct OkcheTextField: View {
    let label: String
    var note: String?
    @Binding var text: String
    var placeholder = ""
    var unit: String?
    var keyboard: UIKeyboardType = .default
    var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            OkcheFieldLabel(title: label, note: note).accessibilityHidden(true)
            HStack(spacing: 8) {
                TextField(placeholder, text: $text)
                    .okcheText(OkcheType.body)
                    .foregroundStyle(OkcheColor.ink)
                    .keyboardType(keyboard)
                    .accessibilityLabel(label)
                    .accessibilityValue(unit.map { text.isEmpty ? "" : "\(text) \($0)" } ?? text)
                if let unit {
                    Text(unit).okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted).accessibilityHidden(true)
                }
            }
            .okcheFieldBox(isError: error != nil)
            OkcheFieldError(message: error)
        }
    }
}

/// ② 고르기 칩: 40 높이 알약 중 하나만. `selection`이 nil이면 아무것도 안 고른 상태.
struct OkcheChoiceChips<Value: Hashable>: View {
    let options: [(value: Value, title: String)]
    @Binding var selection: Value?
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 40

    var body: some View {
        // 큰 글자에서 한 줄에 안 들어가면 세로로 쌓는다.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { chips }
            VStack(alignment: .leading, spacing: 8) { chips }
        }
    }

    private var chips: some View {
        ForEach(options, id: \.value) { option in
            let on = option.value == selection
            Button { selection = option.value } label: {
                Text(option.title)
                    .okcheText(OkcheType.body, weight: on ? .bold : .regular)
                    .fixedSize()
                    .padding(.horizontal, 14)
                    .frame(minHeight: height)
                    .foregroundStyle(on ? OkcheColor.paper : OkcheColor.ink)
                    .background(on ? OkcheColor.namsaek : OkcheColor.paper, in: Capsule())
                    .overlay(Capsule().strokeBorder(on ? Color.clear : OkcheColor.lineStrong, lineWidth: 1.5))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(on ? .isSelected : [])
        }
    }
}

/// ③ 고르기 칸: 52 높이 카드를 같은 너비로 나란히, 하나만.
struct OkcheChoiceCards<Value: Hashable>: View {
    let options: [(value: Value, title: String)]
    @Binding var selection: Value
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 52

    var body: some View {
        HStack(spacing: 8) {
            ForEach(options, id: \.value) { option in
                let on = option.value == selection
                Button { selection = option.value } label: {
                    Text(option.title)
                        .okcheText(OkcheType.body, weight: on ? .bold : .regular)
                        .frame(maxWidth: .infinity, minHeight: height)
                        .foregroundStyle(on ? OkcheColor.namsaekDeep : OkcheColor.ink)
                        .background(on ? OkcheColor.namsaekSoft : OkcheColor.paper,
                                    in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                        .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                            .strokeBorder(on ? OkcheColor.namsaek : OkcheColor.line, lineWidth: 1.5))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }
}

/// ④ 스위치 줄: 제목 + 설명 + 스위치. 줄 전체가 하나의 스위치.
struct OkcheSwitchRow: View {
    let title: String
    var detail: String?
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                if let detail {
                    Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
            }
        }
        .tint(OkcheColor.namsaek)
        // 화면 읽기: 이름은 제목만, 설명은 뒤에 힌트로.
        .accessibilityLabel(title)
        .accessibilityHint(detail ?? "")
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(minHeight: 52)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd).strokeBorder(OkcheColor.line, lineWidth: 1.5))
    }
}

/// ⑤ 큰 버튼: 52 높이 남색, 흰 18 굵은 글자.
struct OkchePrimaryButtonStyle: ButtonStyle {
    @ScaledMetric(relativeTo: .title3) private var height: CGFloat = 52

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .okcheText(OkcheType.heading)
            .foregroundStyle(OkcheColor.paper)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(OkcheColor.namsaek, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

extension ButtonStyle where Self == OkchePrimaryButtonStyle {
    static var okchePrimary: OkchePrimaryButtonStyle { .init() }
}
