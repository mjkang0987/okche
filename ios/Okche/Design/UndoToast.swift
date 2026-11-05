import SwiftUI

/// 탭 바 위 되돌리기 알림(보드 `RecordMedGiven`, 2026-11-04 승인): 남색 띠에 글자 + 금색 '되돌리기'. 몇 초 뒤 사라지고 화면 읽기로도 알린다.
/// 9단계 인슐린 건너뜀 알림도 이걸 쓴다.
struct UndoToast: View {
    let message: String
    let onUndo: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text(message).okcheText(OkcheType.body, weight: .regular).foregroundStyle(OkcheColor.hanji)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onUndo) {
                Text("되돌리기").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.geumLight)
                    .padding(.horizontal, 12).frame(minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 16).padding(.trailing, 6).padding(.vertical, 4)
        .frame(minHeight: 52)
        .background(OkcheColor.namsaekDeep, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        .shadow(color: OkcheColor.namsaekDeep.opacity(0.25), radius: 8, y: 4)
        .padding(.horizontal, 16).padding(.bottom, 12)
        .accessibilityElement(children: .contain)
    }
}

/// 알림 하나: 같은 글자가 다시 와도 새 알림으로(읽기·시간 다시).
struct UndoToastItem: Equatable {
    let id = UUID()
    let text: String
    /// '되돌리기' — 참이면 알림을 닫음, 거짓이면 남김(확인 창을 띄웠거나 되돌리지 못함).
    let undo: @MainActor () -> Bool

    static func == (a: Self, b: Self) -> Bool { a.id == b.id }

    /// 지우기·빼기 뒤 알림(2026-11-05 사용자 — 확인 창 → 지움 → '지웠어요 · 되돌리기'). 되돌리지 못하면 같은 알림에 실패 글자로 다시.
    @MainActor static func removed(_ text: String, show: @escaping @MainActor @Sendable (UndoToastItem?) -> Void, restore: @escaping () throws -> Void) -> UndoToastItem {
        UndoToastItem(text: text) {
            do {
                try restore()
                AccessibilityNotification.Announcement("되돌렸어요").post()
                return true
            } catch {
                show(.removed("되돌리지 못했어요. 다시 해 주세요.", show: show, restore: restore))
                return false
            }
        }
    }
}

private struct ShowUndoKey: EnvironmentKey {
    static let defaultValue: @MainActor @Sendable (UndoToastItem?) -> Void = { _ in }
}

extension EnvironmentValues {
    /// 탭 바 위 되돌리기 알림 띄우기(nil이면 닫기) — 탭마다 `undoToastHost()`가 받는다.
    var showUndo: @MainActor @Sendable (UndoToastItem?) -> Void {
        get { self[ShowUndoKey.self] }
        set { self[ShowUndoKey.self] = newValue }
    }
}

extension View {
    /// 이 탭(안에 쌓인 화면·시트까지)의 되돌리기 알림 자리: 화면 아래(탭 바 위), 내용을 가리지 않게 그만큼 아래 여백을 늘린다.
    /// 5초 뒤 저절로 닫히고, VoiceOver·스위치 제어가 켜져 있으면 닫지 않는다(되돌리기에 닿을 시간 — WCAG 2.2.1, 닫기는 두 손가락 문지르기).
    /// 다른 탭으로 가면 닫힌다.
    func undoToastHost() -> some View {
        modifier(UndoToastHost())
    }
}

private struct UndoToastHost: ViewModifier {
    @State private var item: UndoToastItem?
    /// 탭을 다녀와 다시 보일 때 같은 알림을 또 읽지 않게.
    @State private var announced: UUID?

    func body(content: Content) -> some View {
        content
            .environment(\.showUndo) { item = $0 }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if let current = item {
                    UndoToast(message: current.text) { if current.undo(), item?.id == current.id { item = nil } }
                        .accessibilityAction(.escape) { item = nil } // 화면 읽기 중엔 저절로 안 닫히니 두 손가락 문지르기로 닫기
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .task(id: current.id) {
                            if announced != current.id {
                                announced = current.id
                                var speech = AttributedString("\(current.text). 되돌리려면 되돌리기")
                                speech.accessibilitySpeechAnnouncementPriority = .high // 누른 버튼이 바뀌며 옮기는 초점 읽기에 묻히지 않게
                                AccessibilityNotification.Announcement(speech).post()
                            }
                            guard !UIAccessibility.isVoiceOverRunning, !UIAccessibility.isSwitchControlRunning else { return }
                            try? await Task.sleep(for: .seconds(5))
                            if !Task.isCancelled, item?.id == current.id { item = nil }
                        }
                }
            }
            .onDisappear { item = nil } // 다른 탭에 갔다 오면 옛 되돌리기를 남기지 않음
            .animation(.easeOut(duration: 0.2), value: item)
    }
}
