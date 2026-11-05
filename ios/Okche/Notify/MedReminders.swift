import SwiftData
import SwiftUI
import UserNotifications

/// 탕약 알림 걸기(8-4b): 계산(`MedNotify.alerts`)한 목록으로 휴대폰 알림을 모두 지우고 다시 건다.
@MainActor
enum MedReminders {
    static let prefix = "med."

    /// 앱을 열 때·저장할 때마다 부른다. 허락이 없으면 휴대폰이 받지 않을 뿐 그대로 둔다.
    static func reschedule(in context: ModelContext, now: Date = .now) async {
        guard let alerts = try? alerts(in: context, now: now) else { return }
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        for request in alerts.map(request) { try? await center.add(request) }
    }

    static func alerts(in context: ModelContext, now: Date = .now) throws -> [MedNotify.Alert] {
        let setting = try Setting.loadOrCreate(in: context)
        return MedNotify.alerts(try context.fetch(FetchDescriptor<MedSchedule>(predicate: #Predicate { $0.deletedAt == nil })),
                                records: try context.fetch(FetchDescriptor<Record>(predicate: #Predicate { $0.deletedAt == nil }))
                                    .filter { $0.kind == RecordKind.med.rawValue },
                                pets: try context.fetch(FetchDescriptor<Pet>()), rests: try context.fetch(FetchDescriptor<RestPeriod>()),
                                meds: try context.fetch(FetchDescriptor<Medication>()), medTimeOn: setting.notify.medTime, now: now)
    }

    static func request(_ alert: MedNotify.Alert) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.body
        content.sound = .default
        if let petId = alert.petId { content.userInfo = ["petId": petId.uuidString] }
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: alert.fireAt)
        return UNNotificationRequest(identifier: alert.id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false))
    }

    /// 허락 안내 창(`NotifyAsk`)을 띄울 때: 알림 켠 약을 저장했고 휴대폰이 아직 묻지 않았을 때.
    static func shouldAsk() async -> Bool {
        #if DEBUG
        // 화면 테스트는 `-notify-ask`일 때만(다른 테스트가 창에 막히지 않게).
        if ProcessInfo.processInfo.arguments.contains("-ui-testing"), !ProcessInfo.processInfo.arguments.contains("-notify-ask") { return false }
        #endif
        return await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .notDetermined
    }

    static func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }
}

/// 알림을 누르면 열 아이(8-4b) — 탭 화면이 오늘 먹일 약 시트를 띄운다.
@MainActor @Observable
final class NotifyRouter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotifyRouter()
    var openPetId: UUID?

    /// 앱이 앞에 있어도 알림을 보여 준다.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let id = (response.notification.request.content.userInfo["petId"] as? String).flatMap(UUID.init(uuidString:))
        await MainActor.run { NotifyRouter.shared.openPetId = id }
    }
}

final class OkcheAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // 앱이 꺼진 채 알림을 눌러 열릴 때도 받도록 시작할 때 맡긴다.
        MainActor.assumeIsolated { UNUserNotificationCenter.current().delegate = NotifyRouter.shared }
        return true
    }
}
