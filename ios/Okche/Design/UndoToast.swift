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
}

extension View {
    /// 화면 아래(탭 바 위)에 되돌리기 알림 — 내용을 가리지 않게 그만큼 아래 여백을 늘린다. `item`이 nil이면 숨김.
    /// 5초 뒤 저절로 닫히고, VoiceOver·스위치 제어가 켜져 있으면 닫지 않는다(되돌리기에 닿을 시간 — WCAG 2.2.1). 닫는 건 부르는 쪽(`onUndo` 성공 때).
    func undoToast(_ item: Binding<UndoToastItem?>, onUndo: @escaping () -> Void) -> some View {
        modifier(UndoToastHost(item: item, onUndo: onUndo))
    }
}

private struct UndoToastHost: ViewModifier {
    @Binding var item: UndoToastItem?
    let onUndo: () -> Void
    /// 탭을 다녀와 다시 보일 때 같은 알림을 또 읽지 않게.
    @State private var announced: UUID?

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if let current = item {
                    UndoToast(message: current.text, onUndo: onUndo)
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
            .animation(.easeOut(duration: 0.2), value: item)
    }
}
