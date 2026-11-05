import SwiftData
import SwiftUI

/// 기별 · 알림 설정(보드 `Notify`, 8-4c): 돌봄·기쁜 날 스위치, 밤 알림 쉬기(시작~끝). 바꾸면 바로 저장 — 저장하면 알림을 다시 건다.
/// 인슐린·체중·기쁜 날·작년 오늘은 스위치만 저장(그 알림은 각 단계). 어진첩 체형 사진은 11-3에서.
struct NotifySettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query private var settings: [Setting]
    @State private var editing: QuietEnd?
    @State private var failed = false

    private enum QuietEnd: String, Identifiable { case start, end; var id: String { rawValue } }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(title: "기별", note: "알림 설정", icon: "chevron.left", iconLabel: "뒤로") { dismiss() }
            if let setting = settings.first(where: { $0.id == .setting }) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        section("돌봄") {
                            row("탕약 시간", "약마다 정한 시간에", setting, \.notify.medTime)
                            divider
                            row("인슐린 시간", "당뇨 아이 · 인슐린 설정에서 정한 시간에", setting, \.notify.insulinTime)
                            divider
                            row("약방 · 남은 약", "\(setting.lowMedDays)일분 남았을 때", setting, \.notify.lowMed)
                            divider
                            row("옥체 · 체중 재기", Self.weekly(setting.weightReminder), setting, \.notify.weighIn)
                        }
                        section("기쁜 날") {
                            row("생일 · 가족 된 날 축하", nil, setting, \.notify.goodDay)
                            divider
                            row("작년 오늘의 사진", nil, setting, \.notify.lastYearToday)
                        }
                        section("조용한 시간") {
                            row("밤에는 알림 쉬기", "탕약·인슐린 알림은 이 시간에도 울려요", setting, \.quietHours.enabled)
                            // 끄면 시간 칸은 흐리게(값은 남김).
                            HStack(spacing: 8) {
                                timeButton("시작", setting.quietHours.start) { editing = .start }
                                Text("~").okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).accessibilityHidden(true)
                                timeButton("끝", setting.quietHours.end) { editing = .end }
                            }
                            .disabled(!setting.quietHours.enabled)
                            .opacity(setting.quietHours.enabled ? 1 : 0.45)
                            .padding(.bottom, 14)
                        }
                        if failed { OkcheFieldError(message: "저장하지 못했어요. 다시 해 주세요.") }
                        Text("알림은 앱을 마지막으로 연 날부터 3일치만 걸어 둬요. 3일 동안 열지 않으면 멈추고, 다시 열면 이어져요.")
                            .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    }
                    .padding(.horizontal, OkcheSpacing.space4)
                    .padding(.top, 4).padding(.bottom, OkcheSpacing.space6)
                }
                .sheet(item: $editing) { end in
                    TimeSheet(time: Binding(get: { Self.date(end == .start ? setting.quietHours.start : setting.quietHours.end) },
                                            set: { picked in save(setting) { s in
                                                if end == .start { s.quietHours.start = Self.hhmm(picked) } else { s.quietHours.end = Self.hhmm(picked) }
                                            } }))
                }
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).okcheText(OkcheType.label).foregroundStyle(OkcheColor.inkMuted)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 12).padding(.bottom, 4)
            content()
        }
        .padding(.horizontal, 14)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .accessibilityElement(children: .contain)
    }

    private var divider: some View { Rectangle().fill(OkcheColor.line).frame(height: 1) }

    /// 스위치 줄: 이름 + 회색 설명, 바꾸면 바로 저장.
    private func row(_ title: String, _ detail: String?, _ setting: Setting, _ key: WritableKeyPath<Setting, Bool>) -> some View {
        Toggle(isOn: Binding(get: { setting[keyPath: key] }, set: { on in save(setting) { $0[keyPath: key] = on } })) {
            VStack(alignment: .leading, spacing: 2) {
                // '약방 · 남은 약': 점 뒤는 작은 회색(보드).
                let parts = title.components(separatedBy: " · ")
                (Text(parts[0]) + Text(parts.count > 1 ? " · " + parts[1...].joined(separator: " · ") : "")
                    .font(.system(size: OkcheType.body.size * 0.85)).foregroundColor(OkcheColor.inkMuted))
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                if let detail { Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted) }
            }
        }
        .tint(OkcheColor.namsaek)
        .accessibilityLabel(title)
        .accessibilityHint(detail ?? "")
        .padding(.vertical, 6)
        .frame(minHeight: 52)
    }

    private func timeButton(_ label: String, _ time: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(MedSchedules.clock(time)).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .okcheFieldBox()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityValue(MedSchedules.clock(time))
    }

    private func save(_ setting: Setting, _ change: (inout Setting) -> Void) {
        var s = setting
        change(&s)
        s.updatedAt = .now
        do {
            try context.save()
            failed = false
        } catch {
            context.rollback()
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    /// '매주 일요일 오전 10시'.
    static func weekly(_ r: WeightReminder) -> String {
        let day = MedSchedules.weekdayNames[max(0, min(6, r.weekday - 1))]
        // 정각이면 '오전 10시'(보드).
        let clock = MedSchedules.clock(r.time)
        return "매주 \(day)요일 \(clock.hasSuffix(":00") ? String(clock.dropLast(3)) + "시" : clock)"
    }

    static func date(_ hhmm: String) -> Date {
        let parts = hhmm.split(separator: ":").compactMap { Int($0) }
        return Calendar.current.date(bySettingHour: parts.first ?? 0, minute: parts.count > 1 ? parts[1] : 0, second: 0, of: .now) ?? .now
    }

    static func hhmm(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }
}
