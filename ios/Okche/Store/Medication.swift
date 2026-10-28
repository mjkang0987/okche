import Foundation
import SwiftData

enum MedCategory: String, Codable, CaseIterable {
    case medicine, supplement
}

enum MedForm: String, Codable, CaseIterable {
    case pill, powder, liquid, topical, injection
}

/// '반 알'은 `pill` 0.5.
enum DoseUnit: String, Codable, CaseIterable {
    case pill, ml, pack, drop
}

enum MedFrequency: String, Codable, CaseIterable {
    case daily, everyNDays, weekdays, everyNHours, asNeeded
}

enum HowTo: String, Codable, CaseIterable {
    case plain, inFood, inTreat, syringe
}

enum Syringe: String, Codable, CaseIterable {
    case u40, u100, unknown
}

/// 인슐린 맞는 시간과 양.
struct InsulinSlot: Codable, Hashable {
    /// `HH:mm`
    var time: String
    var units: Double
}

/// 약방의 약 (4장). 남은 양은 줄여 저장하지 않고 기록으로 계산한다.
@Model
final class Medication {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    /// 지워도 남긴다(지난 실록 글이 빈칸이 되지 않게).
    var name: String
    /// 6자까지. 없으면 `name`.
    var shortName: String?
    var category: MedCategory
    var form: MedForm
    var doseAmount: Double
    var unit: DoseUnit
    var stockAmount: Double?
    var stockCountedAt: Date?
    var photoId: UUID?
    var memo: String?

    init(id: UUID = UUID(), now: Date = .now, name: String, shortName: String? = nil, category: MedCategory, form: MedForm,
         doseAmount: Double, unit: DoseUnit, stockAmount: Double? = nil, stockCountedAt: Date? = nil,
         photoId: UUID? = nil, memo: String? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.name = name
        self.shortName = shortName
        self.category = category
        self.form = form
        self.doseAmount = doseAmount
        self.unit = unit
        self.stockAmount = stockAmount
        self.stockCountedAt = stockCountedAt
        self.photoId = photoId
        self.memo = memo
    }
}

/// 약 주기 (5장). 횟수·시간·양을 바꾸면 고치지 않고 새 줄(같은 `seriesId`)을 만든다.
@Model
final class MedSchedule {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var petId: UUID
    var medicationId: UUID
    /// 처음 만든 줄은 자기 `id`, 바꿔 만든 줄은 옛 줄 값.
    var seriesId: UUID
    var amount: Double
    var unit: DoseUnit
    var frequency: MedFrequency
    /// `HH:mm` — 매일·며칠마다·요일
    var times: [String]
    /// 며칠마다(2 이상)
    var everyDays: Int?
    /// 며칠마다의 첫 먹는 날 `YYYY-MM-DD`
    var startDate: String?
    /// ISO 요일, 월=1 … 일=7
    var weekdays: [Int]
    /// 몇 시간마다
    var everyHours: Int?
    var firstAt: Date?
    /// 몇 시간마다에서 늦게 줬을 때 준 시각부터 다시 세기(참) / 처음 정한 시간대로(거짓).
    var shiftOnLate: Bool
    var howTo: HowTo?
    var reminder: Bool
    /// 0, 5, 10, 15, 30, 60
    var leadMinutes: Int
    var activeFrom: Date
    /// 이 시각은 포함하지 않음.
    var activeUntil: Date?

    init(id: UUID = UUID(), now: Date = .now, petId: UUID, medicationId: UUID, seriesId: UUID? = nil,
         amount: Double, unit: DoseUnit, frequency: MedFrequency, times: [String] = [], everyDays: Int? = nil,
         startDate: String? = nil, weekdays: [Int] = [], everyHours: Int? = nil, firstAt: Date? = nil,
         shiftOnLate: Bool = true, howTo: HowTo? = nil, reminder: Bool = true, leadMinutes: Int = 10,
         activeFrom: Date, activeUntil: Date? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.petId = petId
        self.medicationId = medicationId
        self.seriesId = seriesId ?? id
        self.amount = amount
        self.unit = unit
        self.frequency = frequency
        self.times = times
        self.everyDays = everyDays
        self.startDate = startDate
        self.weekdays = weekdays
        self.everyHours = everyHours
        self.firstAt = firstAt
        self.shiftOnLate = shiftOnLate
        self.howTo = howTo
        self.reminder = reminder
        self.leadMinutes = leadMinutes
        self.activeFrom = activeFrom
        self.activeUntil = activeUntil
    }
}

/// 인슐린 계획 (7장) — 당뇨 아이만. 시간·양을 바꾸면 새 줄.
@Model
final class InsulinPlan {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var petId: UUID
    var name: String?
    var syringe: Syringe
    var slots: [InsulinSlot]
    var ringDuringQuiet: Bool
    var leadMinutes: Int
    var activeFrom: Date
    var activeUntil: Date?

    init(id: UUID = UUID(), now: Date = .now, petId: UUID, name: String? = nil, syringe: Syringe = .unknown,
         slots: [InsulinSlot], ringDuringQuiet: Bool = false, leadMinutes: Int = 10, activeFrom: Date, activeUntil: Date? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.petId = petId
        self.name = name
        self.syringe = syringe
        self.slots = slots
        self.ringDuringQuiet = ringDuringQuiet
        self.leadMinutes = leadMinutes
        self.activeFrom = activeFrom
        self.activeUntil = activeUntil
    }
}
