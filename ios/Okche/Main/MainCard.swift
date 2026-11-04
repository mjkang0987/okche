import Foundation

/// 문안 탭 규칙(plan.md 7-0): 표시 알약·아이 카드 줄·오늘 기록. 안드로이드 `MainCard.kt`와 같은 규칙.
enum MainCard {
    /// 표시 알약: '오늘 생일'(어림잡은 생일은 안 띄움) > '가족 된 날'(그날이 아닌 해). 2/29는 평년엔 2/28. `day`는 6시 기준 오늘.
    static func pill(birthDate: String?, birthEstimated: Bool, familyDate: String?, day: String) -> String? {
        if !birthEstimated, sameMonthDay(birthDate, day) { return "오늘 생일" }
        if sameMonthDay(familyDate, day), familyDate?.prefix(4) != day.prefix(4) { return "가족 된 날" }
        return nil
    }

    private static func sameMonthDay(_ date: String?, _ day: String) -> Bool {
        guard let date, date.count == 10, day.count == 10 else { return false }
        let md = date.suffix(5), today = day.suffix(5)
        if md == today { return true }
        guard md == "02-29", today == "02-28", let year = Int(day.prefix(4)) else { return false }
        return !(year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
    }

    /// '고양이 · 코리안숏헤어 · 7살' — 품종이 비었거나 생일이 없으면 그 칸을 뺀다.
    static func detail(_ pet: Pet, today: Date = .now) -> String {
        ([pet.species == .cat ? "고양이" : "강아지", pet.breed.isEmpty ? nil : pet.breed, PetTitle.age(pet.birthDate, today: today)]
            .compactMap { $0 }).joined(separator: " · ")
    }

    /// 머리 날짜 '10월 25일 일요일'(6시 기준 `day`).
    static func dayTitle(_ day: String) -> String {
        let calendar = Calendar(identifier: .gregorian)
        guard let date = PetRegistration.date(day, calendar) else { return day }
        let c = calendar.dateComponents([.month, .day, .weekday], from: date)
        let weekday = ["일", "월", "화", "수", "목", "금", "토"][(c.weekday ?? 1) - 1]
        return "\(c.month!)월 \(c.day!)일 \(weekday)요일"
    }

    /// 오늘 기록에 넣는 종류(다과·탕약·혈당·인슐린·옥체는 그 단계에서).
    static let kinds = Set([RecordKind.vomit, .stool, .urine, .meal].map(\.rawValue))

    /// 오늘 기록: 그 아이의 그날 지우지 않은 기록, 이른 것 위.
    static func todayRecords(_ records: [Record], petId: UUID, day: String) -> [Record] {
        records.filter { $0.petId == petId && $0.day == day && $0.deletedAt == nil && kinds.contains($0.kind) }
            .sorted { $0.occurredAt < $1.occurredAt }
    }

    /// 줄의 쉬운 말(보드 `Main` — 구토는 없음).
    static func plainWord(_ kind: String) -> String? {
        ["stool": "변", "urine": "소변", "meal": "식사"][kind]
    }
}
