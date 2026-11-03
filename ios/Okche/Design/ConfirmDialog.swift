import SwiftUI

/// 확인 창(빼기·지우기·간직 등, 디자인 `MemoryKeep`·`PetDelete`·`MemoryRestore`): 어두운 바탕 위 가운데 카드.
/// 제목(되돌릴 수 없는 일은 연지색), 안내 글·점 목록, 덧붙일 칸, '그냥 둘게요'와 확인 버튼.
struct OkcheConfirmDialog<Extra: View>: View {
    let title: String
    var destructive = false
    var message: String?
    var bullets: [String] = []
    let confirm: String
    var confirmLook: OkchePrimaryButtonStyle.Look = .filled
    let onCancel: () -> Void
    let onConfirm: () -> Void
    @ViewBuilder var extra: Extra
    /// 창이 뜨면 화면 읽기 초점을 제목으로.
    @AccessibilityFocusState private var titleFocused: Bool

    var body: some View {
        ZStack {
            OkcheColor.ink.opacity(0.45).ignoresSafeArea()
                .onTapGesture(perform: onCancel)
                .accessibilityHidden(true)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(title).okcheText(OkcheType.heading)
                        .foregroundStyle(destructive ? OkcheColor.yeonji : OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($titleFocused)
                    if let message {
                        Text(message).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    }
                    if !bullets.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(bullets, id: \.self) { line in
                                HStack(alignment: .firstTextBaseline, spacing: 10) {
                                    Circle().fill(OkcheColor.lineStrong).frame(width: 6, height: 6).accessibilityHidden(true)
                                    Text(line).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                                }
                            }
                        }
                    }
                    extra
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { buttons }
                        VStack(spacing: 8) { buttons }
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 20)
                .background(OkcheColor.hanji, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                .padding(.horizontal, 24)
                .frame(minHeight: 0)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)
        }
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onCancel) // 두 손가락 Z로 닫기
        .onAppear { titleFocused = true }
    }

    @ViewBuilder private var buttons: some View {
        Button("그냥 둘게요", action: onCancel).buttonStyle(.okchePrimary(.outlined)).keyboardShortcut(.cancelAction)
        Button(confirm, action: onConfirm).buttonStyle(.okchePrimary(confirmLook))
    }
}

extension OkcheConfirmDialog where Extra == EmptyView {
    init(title: String, destructive: Bool = false, message: String? = nil, bullets: [String] = [], confirm: String,
         confirmLook: OkchePrimaryButtonStyle.Look = .filled, onCancel: @escaping () -> Void, onConfirm: @escaping () -> Void) {
        self.init(title: title, destructive: destructive, message: message, bullets: bullets, confirm: confirm,
                  confirmLook: confirmLook, onCancel: onCancel, onConfirm: onConfirm) { EmptyView() }
    }
}

/// 하단 탭(6-4: 간직하면 문안, 지우면 더보기로 옮겨 간다).
enum AppTab: Hashable { case record, main, sillok, album, more }

private struct OpenTabKey: EnvironmentKey {
    static let defaultValue: @MainActor @Sendable (AppTab) -> Void = { _ in }
}

extension EnvironmentValues {
    /// 다른 탭으로 옮겨 가기(`RootTabView`가 넣어 줌).
    var openTab: @MainActor @Sendable (AppTab) -> Void {
        get { self[OpenTabKey.self] }
        set { self[OpenTabKey.self] = newValue }
    }
}
