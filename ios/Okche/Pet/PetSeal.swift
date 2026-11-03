import SwiftData
import SwiftUI

/// 호칭·나이 표기·지금 보는 아이(plan.md 6-3). 안드로이드 `PetSeal.kt`와 같은 규칙.
enum PetTitle {
    /// 생일부터 오늘까지 꽉 찬 해. 2/29생은 다음 해 2/28에 1살(기기 달력).
    private static func fullYears(from birth: Date, to today: Date, _ calendar: Calendar) -> Int {
        var years = calendar.dateComponents([.year], from: birth, to: today).year ?? 0
        if let next = calendar.date(byAdding: .year, value: years + 1, to: birth), next <= today { years += 1 }
        return years
    }

    private static func birth(_ birthDate: String?, _ calendar: Calendar) -> Date? {
        birthDate.flatMap { PetRegistration.date($0, calendar) }
    }

    /// 생일(어림 포함)부터 1살 생일 전날까지 '아기씨', 1살 생일부터 '마마'. 생일이 없으면 '아기씨'.
    static func honorific(_ birthDate: String?, today: Date = .now, calendar: Calendar = .okcheDevice) -> String {
        guard let birth = birth(birthDate, calendar) else { return "아기씨" }
        return fullYears(from: birth, to: calendar.startOfDay(for: today), calendar) >= 1 ? "마마" : "아기씨"
    }

    /// '콩이 마마'
    static func name(_ pet: Pet, today: Date = .now, calendar: Calendar = .okcheDevice) -> String {
        "\(pet.name) \(honorific(pet.birthDate, today: today, calendar: calendar))"
    }

    /// 만 나이 '7살', 1살 미만은 만 개월 '3개월'(1개월 미만 '0개월'). 생일이 없으면 nil.
    static func age(_ birthDate: String?, today: Date = .now, calendar: Calendar = .okcheDevice) -> String? {
        guard let birth = birth(birthDate, calendar) else { return nil }
        let day = calendar.startOfDay(for: today)
        let years = fullYears(from: birth, to: day, calendar)
        if years >= 1 { return "\(years)살" }
        var months = calendar.dateComponents([.month], from: birth, to: day).month ?? 0
        if let next = calendar.date(byAdding: .month, value: months + 1, to: birth), next <= day { months += 1 }
        return "\(max(months, 0))개월"
    }

    /// 지금 보는 아이: 이 기기만의 설정 `lastPetId`, 없거나 지운 아이면 가장 먼저 모신 아이. `pets`는 지운 줄 뺀 `createdAt` 순.
    static func current(_ pets: [Pet], lastPetId: String) -> Pet? {
        pets.first { $0.id.uuidString == lastPetId } ?? pets.first
    }
}
