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

/// 칸 아래 오류 글자. 화면 읽기 알림은 저장하는 쪽이 한 번에 한다(여러 칸이 따로 알리면 겹침).
struct OkcheFieldError: View {
    let message: String?

    var body: some View {
        if let message {
            Text(message)
                .okcheText(OkcheType.label, weight: .regular)
                .foregroundStyle(OkcheColor.yeonji)
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
    /// 칸 아래 설명처럼 화면 읽기에서 칸과 함께 읽을 말.
    var hint: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            OkcheFieldLabel(title: label, note: note).accessibilityHidden(true)
            HStack(spacing: 8) {
                // 보기 글은 흐린 글자 색(시스템 기본 회색은 대비가 모자람)
                TextField(text: $text, prompt: Text(placeholder).foregroundStyle(OkcheColor.inkMuted)) { Text(label) }
                    .okcheText(OkcheType.body)
                    .frame(maxWidth: .infinity, maxHeight: .infinity) // 테두리 안 어디를 눌러도 칸이 잡히게
                    .foregroundStyle(OkcheColor.ink)
                    .keyboardType(keyboard)
                    .accessibilityLabel([label, note].compactMap { $0 }.joined(separator: " "))
                    .accessibilityValue(unit.map { text.isEmpty ? "" : "\(text) \($0)" } ?? text)
                    .accessibilityHint(hint ?? "")
                if let unit {
                    Text(unit).okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted).accessibilityHidden(true)
                }
            }
            .okcheFieldBox(isError: error != nil)
            OkcheFieldError(message: error)
        }
    }
}

/// ② 고르기 칩: 40 높이 알약. 하나만 고르기(`selection`) 또는 여러 개 고르기(`isOn`·`onTap`, 7-2 자세히 시트).
/// 하나만 고르기에서 `selection`이 nil이면 아무것도 안 고른 상태, `allowsNone`이면 고른 칩을 다시 눌러 안 고름으로 되돌린다('(선택)' 칸).
/// 줄에 안 들어가면 다음 줄로 넘긴다(디자인 flex-wrap).
struct OkcheChoiceChips<Value: Hashable>: View {
    let options: [(value: Value, title: String)]
    private let isOn: (Value) -> Bool
    private let onTap: (Value) -> Void
    /// 여러 개 고르기면 화면 읽기에 '여러 개 고를 수 있어요', 아니고 풀 수 있으면 고른 칩에 '선택 해제' 동작.
    private let multiple: Bool
    private let deselectable: Bool
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 40

    init(options: [(value: Value, title: String)], selection: Binding<Value?>, allowsNone: Bool = true) {
        self.options = options
        multiple = false
        deselectable = allowsNone
        isOn = { $0 == selection.wrappedValue }
        onTap = { value in selection.wrappedValue = value == selection.wrappedValue && allowsNone ? nil : value }
    }

    /// 여러 개 고르기처럼 고름·누름을 부르는 쪽이 정할 때(고른 칩을 다시 누르면 푸는 것으로 본다).
    init(options: [(value: Value, title: String)], multiple: Bool = false, isOn: @escaping (Value) -> Bool, onTap: @escaping (Value) -> Void) {
        self.options = options
        self.multiple = multiple
        deselectable = true
        self.isOn = isOn
        self.onTap = onTap
    }

    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(options, id: \.value) { option in
                let on = isOn(option.value)
                Button { onTap(option.value) } label: {
                    Text(option.title)
                        .okcheText(OkcheType.body, weight: on ? .bold : .regular)
                        .multilineTextAlignment(.center)
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
                .accessibilityHint(multiple ? "여러 개 고를 수 있어요" : "")
                .accessibilityActions {
                    if on && deselectable && !multiple { Button("선택 해제") { onTap(option.value) } }
                }
            }
        }
    }
}

/// 줄에 안 들어가면 다음 줄로 넘기는 배치(칩). 한 줄보다 넓은 것은 줄 폭으로 줄인다.
struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let width = rows.map { $0.width }.max() ?? 0
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for (index, size) in row.items {
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var items: [(Int, CGSize)] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var row = Row()
        for (index, subview) in subviews.enumerated() {
            var size = subview.sizeThatFits(.unspecified)
            if size.width > width { size = subview.sizeThatFits(ProposedViewSize(width: width, height: nil)) }
            if !row.items.isEmpty && row.width + spacing + size.width > width {
                rows.append(row)
                row = Row()
            }
            row.width += (row.items.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.items.append((index, size))
        }
        if !row.items.isEmpty { rows.append(row) }
        return rows
    }
}

/// ③ 고르기 칸: 카드를 같은 너비로 나란히, 하나만. 아이콘을 주면 아이콘 위·글자 아래 96 높이(7-2 매화 모양),
/// `isOn`·`onTap`을 주면 다시 눌러 풀기 같은 동작을 부르는 쪽이 정한다.
struct OkcheChoiceCards<Value: Hashable>: View {
    let options: [(value: Value, title: String)]
    private let isOn: (Value) -> Bool
    private let onTap: (Value) -> Void
    private let icons: [Value: String]
    /// 고른 카드를 다시 눌러 풀 수 있으면 화면 읽기에 '선택 해제' 동작.
    private let deselectable: Bool
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 52
    @ScaledMetric(relativeTo: .body) private var iconHeight: CGFloat = 96

    init(options: [(value: Value, title: String)], selection: Binding<Value>) {
        self.options = options
        isOn = { $0 == selection.wrappedValue }
        onTap = { selection.wrappedValue = $0 }
        icons = [:]
        deselectable = false
    }

    init(options: [(value: Value, title: String)], icons: [Value: String] = [:], deselectable: Bool = true,
         isOn: @escaping (Value) -> Bool, onTap: @escaping (Value) -> Void) {
        self.options = options
        self.icons = icons
        self.isOn = isOn
        self.onTap = onTap
        self.deselectable = deselectable
    }

    var body: some View {
        // 큰 글자에서 나란히 안 들어가면 세로로 쌓는다(넘친 칸이 화면을 옆으로 밀지 않게).
        ViewThatFits(in: .horizontal) {
            HStack(spacing: icons.isEmpty ? 8 : 6) { cards }
            VStack(spacing: 6) { cards }
        }
    }

    private var cards: some View {
        ForEach(options, id: \.value) { option in
                let on = isOn(option.value)
                Button { onTap(option.value) } label: {
                    VStack(spacing: 8) {
                        if let icon = icons[option.value] {
                            Image(systemName: icon).font(.title2).foregroundStyle(OkcheColor.namsaekDeep).accessibilityHidden(true)
                        }
                        Text(option.title)
                            .okcheText(icons.isEmpty ? OkcheType.body : OkcheType.label, weight: on ? .bold : .regular)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.horizontal, 2)
                    .frame(maxWidth: .infinity, minHeight: icons.isEmpty ? height : iconHeight)
                    .foregroundStyle(on ? OkcheColor.namsaekDeep : OkcheColor.ink)
                    .background(on ? OkcheColor.namsaekSoft : OkcheColor.paper,
                                in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                        .strokeBorder(on ? OkcheColor.namsaek : OkcheColor.line, lineWidth: 1.5))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
                .accessibilityActions {
                    if on && deselectable { Button("선택 해제") { onTap(option.value) } }
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

/// ⑤ 큰 버튼: 52 높이 남색, 흰 18 굵은 글자. 모양 옵션(6-3): 흰 바탕 테두리(`outlined`)·점선(`dashed`) 52, 남색 알약 40(`pill`),
/// 지우기처럼 되돌릴 수 없는 일은 연지색 테두리·굵은 글자(`danger`, 6-4).
struct OkchePrimaryButtonStyle: ButtonStyle {
    /// `emphasis`: 남색 테두리·굵은 남색 글자(둘 중 권하는 답 — '다 바꿨어요', 7-4a).
    enum Look { case filled, outlined, dashed, pill, danger, emphasis }
    var look: Look = .filled
    @ScaledMetric(relativeTo: .title3) private var height: CGFloat = 52
    @ScaledMetric(relativeTo: .subheadline) private var pillHeight: CGFloat = 40

    func makeBody(configuration: Configuration) -> some View {
        Group {
            switch look {
            case .filled:
                configuration.label
                    .okcheText(OkcheType.heading)
                    .foregroundStyle(OkcheColor.paper)
                    .padding(.horizontal, OkcheSpacing.space4)
                    .frame(maxWidth: .infinity, minHeight: height)
                    .background(OkcheColor.namsaek, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            case .outlined, .dashed, .danger, .emphasis:
                configuration.label
                    .okcheText(OkcheType.body, weight: look == .danger || look == .emphasis ? .bold : nil)
                    .foregroundStyle(look == .danger ? OkcheColor.yeonji : look == .emphasis ? OkcheColor.namsaekDeep : OkcheColor.ink)
                    .padding(.horizontal, OkcheSpacing.space3)
                    .frame(maxWidth: .infinity, minHeight: height)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                        .strokeBorder(look == .danger ? OkcheColor.yeonji : look == .emphasis ? OkcheColor.namsaek : OkcheColor.lineStrong,
                                      style: StrokeStyle(lineWidth: 1.5, dash: look == .dashed ? [6, 4] : [])))
            case .pill:
                configuration.label
                    .okcheText(OkcheType.label, weight: .bold)
                    .foregroundStyle(OkcheColor.paper)
                    .padding(.horizontal, 14)
                    .frame(minHeight: pillHeight)
                    .background(OkcheColor.namsaek, in: Capsule())
                    .frame(minHeight: 44) // 보이는 높이는 40, 누르는 영역은 44
                    .contentShape(Rectangle())
            }
        }
        .multilineTextAlignment(.center)
        .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

extension ButtonStyle where Self == OkchePrimaryButtonStyle {
    static var okchePrimary: OkchePrimaryButtonStyle { .init() }
    static func okchePrimary(_ look: OkchePrimaryButtonStyle.Look) -> OkchePrimaryButtonStyle { .init(look: look) }
}

/// 고르는 칸(셀렉트): 이름표 + 펼침 메뉴. `inline`이면 이름표 왼쪽·칸 오른쪽 150(큰 글자에선 아래로 — 인슐린·탕약 '알림 보낼 때'),
/// 아니면 이름표 위·칸 꽉 차게(탕약 '먹이는 방법').
struct OkcheMenuPicker<Value: Hashable>: View {
    let label: String
    var note: String?
    let options: [(value: Value, title: String)]
    @Binding var selection: Value
    var inline = true

    var body: some View {
        let title = options.first { $0.value == selection }?.title ?? ""
        if inline {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) { labelText; Spacer(minLength: 0); menu(title).frame(width: 150) }
                VStack(alignment: .leading, spacing: 6) { labelText; menu(title) }
            }
            .padding(.leading, 16)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                OkcheFieldLabel(title: label, note: note).accessibilityHidden(true)
                menu(title)
            }
        }
    }

    private var labelText: some View {
        Text(label).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).accessibilityHidden(true)
    }

    private func menu(_ title: String) -> some View {
        Menu {
            Picker(label, selection: $selection) {
                ForEach(options, id: \.value) { Text($0.title).tag($0.value) }
            }
        } label: {
            HStack {
                Text(title).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                Spacer(minLength: 8)
                Image(systemName: "chevron.down").font(.body.weight(.medium)).foregroundStyle(OkcheColor.inkMuted)
            }
            .okcheFieldBox()
        }
        .accessibilityLabel([label, note].compactMap { $0 }.joined(separator: " "))
        .accessibilityValue(title)
    }
}

// MARK: 아래 고정 주 버튼(2026-11-05 사용자 — 꼭 눌러야 하는 버튼은 아래 고정, 덜 채우면 흐리게)

extension View {
    /// 화면·시트 아래에 붙은 주 버튼 칸: 위 선 + 한지 바탕, 내용이 많아도 늘 보인다. `show`가 거짓이면 칸 없음.
    func okcheFooter<Footer: View>(show: Bool = true, @ViewBuilder _ footer: () -> Footer) -> some View {
        let content = footer()
        return safeAreaInset(edge: .bottom, spacing: 0) {
            if show {
                VStack(alignment: .leading, spacing: 8) { content }
                    .padding(.horizontal, OkcheSpacing.space4).padding(.top, 10).padding(.bottom, 8)
                    .background(OkcheColor.hanji)
                    .overlay(alignment: .top) { OkcheColor.line.frame(height: 1) }
            }
        }
    }

    /// 주 버튼에 붙여 덜 채웠으면(`missing`이 있으면) 흐리게 + 못 누름 + 바로 위에 남은 것 한 줄.
    /// 공용 버튼 모양은 못 누를 때도 같은 색이라 여기서 흐리게 하고, 화면 읽기는 버튼을 바로 짚어도 이유를 듣게.
    /// `onShow`가 있으면 남은 것 줄을 누르면 그 칸으로(긴 화면 — 위쪽 칸이 문제일 때 찾게).
    func okcheMissing(_ missing: String?, onShow: (() -> Void)? = nil) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let missing {
                if let onShow {
                    Button(action: onShow) {
                        Text("\(missing) ›").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, minHeight: 32, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(missing)
                    .accessibilityHint("그 칸으로 가기")
                } else {
                    Text(missing).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
            }
            disabled(missing != nil).opacity(missing == nil ? 1 : 0.6).accessibilityHint(missing ?? "")
        }
    }
}

/// 칸 오류 글자 앞에 칸 이름 — 아래 줄은 어느 칸인지 말해야 해서(이미 들어 있으면 그대로).
func okcheNamed(_ label: String, _ message: String) -> String {
    message.contains(label) ? message : "\(label) · \(message)"
}
