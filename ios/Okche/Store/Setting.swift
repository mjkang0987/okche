import Foundation
import SwiftData

enum CelebrateTone: String, Codable, CaseIterable {
    case joseon, plain
}

/// 밤 알림 쉬기. 이 시간의 알림은 보내지 않고 버린다.
struct QuietHours: Codable, Hashable {
    var enabled: Bool
    /// `HH:mm`
    var start: String
    var end: String
}

/// 알림 종류별 켜짐.
struct NotifySettings: Codable, Hashable {
    var medTime: Bool
    var insulinTime: Bool
    var lowMed: Bool
    var weighIn: Bool
    var goodDay: Bool
    var lastYearToday: Bool
}

/// 체중 재기 알림.
struct WeightReminder: Codable, Hashable {
    /// ISO 요일, 월=1 … 일=7
    var weekday: Int
    /// `HH:mm`
    var time: String
}

/// 함께 쓰는 설정 (10장). 고정 ID 한 줄이라 두 기기 백업을 합쳐도 하나.
/// 이 기기만의 설정(백업 대상 등)은 여기 두지 않고 UserDefaults에.
@Model
final class Setting {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var quietHours: QuietHours
    var notify: NotifySettings
    var weightReminder: WeightReminder
    /// 남은 약 알림 기준 일수
    var lowMedDays: Int
    var celebrateTone: CelebrateTone

    // shortcut: 밤 알림 쉬기·알림 켜짐·축하 문구의 처음 값은 문서에 없어 임시로 정함, 설정 화면 만들 때 디자인과 맞춘다.
    init(now: Date = .now,
         quietHours: QuietHours = QuietHours(enabled: false, start: "22:00", end: "07:00"),
         notify: NotifySettings = NotifySettings(medTime: true, insulinTime: true, lowMed: true, weighIn: true,
                                                 goodDay: true, lastYearToday: true),
         weightReminder: WeightReminder = WeightReminder(weekday: 7, time: "10:00"),
         lowMedDays: Int = 3, celebrateTone: CelebrateTone = .joseon) {
        id = .setting
        createdAt = now
        updatedAt = now
        self.quietHours = quietHours
        self.notify = notify
        self.weightReminder = weightReminder
        self.lowMedDays = lowMedDays
        self.celebrateTone = celebrateTone
    }
}
