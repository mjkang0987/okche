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

extension View {
    /// 화면 아래(탭 바 위)에 되돌리기 알림. `message`가 nil이면 숨김, 5초 뒤 저절로 닫힘(화면 읽기가 켜져 있으면 더 길게).
    func undoToast(_ message: Binding<String?>, onUndo: @escaping () -> Void) -> some View {
        overlay(alignment: .bottom) {
            if let text = message.wrappedValue {
                UndoToast(message: text) {
                    message.wrappedValue = nil
                    onUndo()
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: text) {
                    AccessibilityNotification.Announcement("\(text). 되돌리려면 되돌리기").post()
                    try? await Task.sleep(for: .seconds(UIAccessibility.isVoiceOverRunning ? 10 : 5))
                    if !Task.isCancelled, message.wrappedValue == text { message.wrappedValue = nil }
                }
            }
        }
        .animation(.easeOut(duration: 0.2), value: message.wrappedValue)
    }
}
