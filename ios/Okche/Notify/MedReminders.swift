import SwiftData
import SwiftUI
import UserNotifications

/// 탕약 알림 걸기(8-4b): 계산(`MedNotify.alerts`)한 목록으로 휴대폰 알림을 모두 지우고 다시 건다.
@MainActor
enum MedReminders {
    static let prefixes = ["med.", "low.", "insulin."]

    /// 앱을 열 때·저장할 때마다 부른다. 허락이 없으면 휴대폰이 받지 않을 뿐 그대로 둔다.
    static func reschedule(in context: ModelContext, now: Date = .now) async {
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard let alerts = try? alerts(in: context, delivered: [.authorized, .provisional, .ephemeral].contains(status), now: now) else { return }
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { id in prefixes.contains { id.hasPrefix($0) } }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        for request in alerts.map(request) { try? await center.add(request) }
    }

    /// 남은 약 알림을 먼저(몇 개 안 됨) 넣고, 탕약·인슐린은 합쳐 남은 자리(이어짐 알림 하나 빼고 64개)만큼 이른 것부터.
    /// `delivered`: 휴대폰 알림이 허락돼 걸어 둔 남은 약 알림이 실제로 떴을 때만 보낸 것으로 친다.
    static func alerts(in context: ModelContext, delivered: Bool = true, now: Date = .now) throws -> [MedNotify.Alert] {
        let setting = try Setting.loadOrCreate(in: context)
        let schedules = try context.fetch(FetchDescriptor<MedSchedule>(predicate: #Predicate { $0.deletedAt == nil }))
        let all = try context.fetch(FetchDescriptor<Record>(predicate: #Predicate { $0.deletedAt == nil }))
        let records = all.filter { $0.kind == RecordKind.med.rawValue }
        let meds = try context.fetch(FetchDescriptor<Medication>())
        let pets = try context.fetch(FetchDescriptor<Pet>())
        let rests = try context.fetch(FetchDescriptor<RestPeriod>())
        let low = MedNotify.lowMedAlerts(meds: meds, schedules: schedules, records: records, on: setting.notify.lowMed, days: setting.lowMedDays,
                                         quiet: setting.quietHours, sent: LowMedLog.sent(now: now, delivered: delivered), now: now)
        LowMedLog.armed(low)
        let insulin = MedNotify.insulinAlerts(try context.fetch(FetchDescriptor<InsulinPlan>()),
                                              records: all.filter { $0.kind == RecordKind.insulin.rawValue }, pets: pets, rests: rests,
                                              insulinTimeOn: setting.notify.insulinTime, quiet: setting.quietHours, now: now)
        return low + MedNotify.alerts(schedules, records: records, pets: pets, rests: rests, meds: meds, medTimeOn: setting.notify.medTime,
                                      extra: insulin, limit: MedNotify.limit - low.count, now: now)
    }

    static func request(_ alert: MedNotify.Alert) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.body
        content.sound = .default
        if let petId = alert.petId { content.userInfo = [alert.kind.isInsulin ? "insulinPetId" : "petId": petId.uuidString] }
        if let medId = alert.medId { content.userInfo = ["medId": medId.uuidString] }
        // 시간대를 함께 넣어 휴대폰 시간대가 바뀌어도 같은 순간에 울림(안드로이드와 같음).
        let parts = Calendar.current.dateComponents([.timeZone, .year, .month, .day, .hour, .minute], from: alert.fireAt)
        return UNNotificationRequest(identifier: alert.id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false))
    }

    /// 허락 안내 창(`NotifyAsk`)을 띄울 때: 알림 켠 약을 저장했고 휴대폰이 아직 묻지 않았을 때.
    static func shouldAsk() async -> Bool {
        #if DEBUG
        // 화면 테스트는 `-notify-ask`일 때만(다른 테스트가 창에 막히지 않게).
        if ProcessInfo.processInfo.arguments.contains("-ui-testing"), !ProcessInfo.processInfo.arguments.contains("-notify-ask") { return false }
        #endif
        // 한 번만 묻는다('나중에'여도 휴대폰 상태는 그대로라 기억해 둠 — 안드로이드와 같음).
        guard !UserDefaults.standard.bool(forKey: askedKey) else { return false }
        return await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .notDetermined
    }

    static let askedKey = "notifyAsked"
    static func markAsked() { UserDefaults.standard.set(true, forKey: askedKey) }

    static func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }
}

/// 허락 안내 창(보드 `NotifyAsk`, 8-4b·9-3): 알림 켠 약·인슐린을 처음 저장한 뒤. '계속하기'면 휴대폰이 묻고, 어느 쪽이든 `onDone`.
struct NotifyAskDialog: View {
    let title: String
    let onDone: () -> Void
    @State private var requesting = false

    var body: some View {
        OkcheConfirmDialog(title: title, confirm: "계속하기", cancel: "나중에", onCancel: onDone,
                           onConfirm: {
                               guard !requesting else { return } // 두 번 눌러도 한 번만
                               requesting = true
                               Task { await MedReminders.requestPermission(); onDone() }
                           }) {
            Text("알림을 허락하면 정해 둔 시간에 탕약 알림이 와요. 다음 창에서 '허용'을 눌러 주세요.")
                .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
            Text("나중에 휴대폰 설정에서도 켤 수 있어요.")
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .presentationBackground(.clear)
    }
}

/// 남은 약 알림을 보낸 기록(이 기기, 10장 `lowMedSent`): 건 알림은 울릴 시각이 지나면 보낸 것으로 옮긴다.
@MainActor
enum LowMedLog {
    private static let sentKey = "lowMedSent", armedKey = "lowMedArmed"

    static func sent(now: Date, delivered: Bool = true) -> Set<String> {
        let d = UserDefaults.standard
        var sent = Set(d.stringArray(forKey: sentKey) ?? [])
        let armed = d.dictionary(forKey: armedKey) as? [String: Double] ?? [:]
        // 알림이 꺼져 있던 동안 지난 것은 안 보낸 것 — 켜면 다시 건다.
        for (key, at) in armed where delivered && at <= now.timeIntervalSince1970 { sent.insert(key) }
        d.set(Array(sent), forKey: sentKey)
        return sent
    }

    static func armed(_ alerts: [MedNotify.Alert]) {
        let pairs = alerts.compactMap { a in a.sentKey.map { ($0, a.fireAt.timeIntervalSince1970) } }
        UserDefaults.standard.set(Dictionary(pairs, uniquingKeysWith: { a, _ in a }), forKey: armedKey)
    }
}

/// 알림을 누르면 열 아이(8-4b) — 탭 화면이 오늘 먹일 약 시트를 띄운다. 남은 약 알림은 약방의 그 약(8-4c).
/// 인슐린 알림은 일기 탭에 그 아이를 골라 둔다(9-3 — 인슐린 줄이 보임).
@MainActor @Observable
final class NotifyRouter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotifyRouter()
    var openPetId: UUID?
    var openMedId: UUID?
    var insulinPetId: UUID?

    /// 앱이 앞에 있어도 알림을 보여 준다.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let pet = (info["petId"] as? String).flatMap(UUID.init(uuidString:))
        let med = (info["medId"] as? String).flatMap(UUID.init(uuidString:))
        let insulin = (info["insulinPetId"] as? String).flatMap(UUID.init(uuidString:))
        await MainActor.run {
            NotifyRouter.shared.openPetId = pet
            NotifyRouter.shared.openMedId = med
            NotifyRouter.shared.insulinPetId = insulin
        }
    }
}

final class OkcheAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // 앱이 꺼진 채 알림을 눌러 열릴 때도 받도록 시작할 때 맡긴다.
        MainActor.assumeIsolated { UNUserNotificationCenter.current().delegate = NotifyRouter.shared }
        return true
    }
}
