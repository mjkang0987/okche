import SwiftData
import XCTest
@testable import Okche

/// 약 주기·탕약 규칙(plan.md 8-2a). 안드로이드 MedSchedulesTest와 같은 경우.
@MainActor
final class MedSchedulesTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        Calendar.okcheDevice.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
    private func schedule(_ freq: MedFrequency, times: [String] = ["08:00"], every: Int? = nil, start: String? = nil, weekdays: [Int] = [],
                          hours: Int? = nil, firstAt: Date? = nil, amount: Double = 1, pet: UUID = UUID(), med: UUID = UUID(),
                          from: Date = Date(timeIntervalSince1970: 0)) -> MedSchedule {
        MedSchedule(petId: pet, medicationId: med, amount: amount, unit: .pill, frequency: freq, times: times, everyDays: every, startDate: start,
                    weekdays: weekdays, everyHours: hours, firstAt: firstAt, activeFrom: from)
    }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testTexts() {
        XCTAssertEqual(MedSchedules.amountText(1, .pill), "1알")
        XCTAssertEqual(MedSchedules.amountText(0.5, .pack), "반 포")
        XCTAssertEqual(MedSchedules.amountText(0.5, .ml), "0.5ml")
        XCTAssertEqual(MedSchedules.frequencyText(schedule(.daily, times: ["08:00", "21:00"])), "하루 2번")
        XCTAssertEqual(MedSchedules.frequencyText(schedule(.everyNDays, every: 2)), "이틀에 한 번")
        XCTAssertEqual(MedSchedules.frequencyText(schedule(.everyNDays, every: 5)), "5일에 한 번")
        XCTAssertEqual(MedSchedules.frequencyText(schedule(.weekdays, weekdays: [4, 1])), "월·목")
        XCTAssertEqual(MedSchedules.frequencyText(schedule(.everyNHours, hours: 8)), "8시간마다")
        let s = schedule(.daily)
        s.howTo = .inFood
        XCTAssertEqual(MedSchedules.cardDetail(s, petName: "콩이"), "콩이 · 하루 1번 · 1알 · 사료에 섞어서")
        s.howTo = .plain
        XCTAssertEqual(MedSchedules.cardDetail(s, petName: "콩이"), "콩이 · 하루 1번 · 1알")
        XCTAssertEqual(MedSchedules.clock("21:05"), "오후 9:05")
        XCTAssertEqual(MedSchedules.clock("00:30"), "오전 12:30")
    }

    func testDoseDaysAndNextDue() {
        let every2 = schedule(.everyNDays, times: ["21:00"], every: 2, start: "2026-10-27")
        XCTAssertTrue(MedSchedules.isDoseDay(every2, day: "2026-10-27"))
        XCTAssertFalse(MedSchedules.isDoseDay(every2, day: "2026-10-28"))
        XCTAssertTrue(MedSchedules.isDoseDay(every2, day: "2026-10-29"))
        XCTAssertFalse(MedSchedules.isDoseDay(every2, day: "2026-10-25")) // 시작 전
        XCTAssertEqual(MedSchedules.doseDayText(every2, today: "2026-10-27"), "오늘 먹는 날 · 다음 10/29")
        XCTAssertEqual(MedSchedules.doseDayText(every2, today: "2026-10-28"), "다음 10/29")
        XCTAssertEqual(MedSchedules.nextDue(every2, now: at(2026, 10, 27, 22)), at(2026, 10, 29, 21))
        let weekly = schedule(.weekdays, weekdays: [1, 4]) // 월·목 — 2026-10-26이 월요일
        XCTAssertTrue(MedSchedules.isDoseDay(weekly, day: "2026-10-26"))
        XCTAssertFalse(MedSchedules.isDoseDay(weekly, day: "2026-10-27"))
        XCTAssertTrue(MedSchedules.isDoseDay(weekly, day: "2026-10-29"))
        // 06:00 전 시간은 다음 날 새벽(1-2)
        XCTAssertEqual(MedSchedules.occurrence(day: "2026-10-27", time: "02:00"), at(2026, 10, 28, 2))
        let daily = schedule(.daily, times: ["21:00", "08:00"])
        XCTAssertEqual(MedSchedules.nextDue(daily, now: at(2026, 10, 27, 9)), at(2026, 10, 27, 21))
        XCTAssertEqual(MedSchedules.nextDue(daily, now: at(2026, 10, 27, 22)), at(2026, 10, 28, 8))
        let hours = schedule(.everyNHours, times: [], hours: 8, firstAt: at(2026, 10, 27, 8))
        XCTAssertEqual(MedSchedules.nextDue(hours, now: at(2026, 10, 27, 7)), at(2026, 10, 27, 8))
        XCTAssertEqual(MedSchedules.nextDue(hours, now: at(2026, 10, 27, 9)), at(2026, 10, 27, 16))
        XCTAssertNil(MedSchedules.nextDue(schedule(.asNeeded, times: [])))
        XCTAssertEqual(MedSchedules.hoursPreview(firstAt: at(2026, 10, 27, 8), everyHours: 36).count, 4)
    }

    func testCardsOrderAndDaysLeft() throws {
        let kong = Pet(name: "콩이", species: .cat, breed: "", furColor: "")
        kong.createdAt = at(2026, 1, 1, 9)
        let bori = Pet(name: "보리", species: .dog, breed: "", furColor: "")
        bori.createdAt = at(2026, 1, 2, 9)
        let dubu = Pet(name: "두부", species: .cat, breed: "", furColor: "", memorializedAt: .now)
        let now = at(2026, 10, 27, 9)
        let a = schedule(.daily, times: ["21:00"], pet: kong.id)
        let b = schedule(.daily, times: ["21:00"], pet: bori.id)
        let c = schedule(.daily, times: ["12:00"], pet: bori.id)
        let need = schedule(.asNeeded, times: [], pet: kong.id)
        let memorial = schedule(.daily, pet: dubu.id)
        let closed = schedule(.daily, pet: kong.id)
        closed.activeUntil = now
        let order = MedSchedules.cards([need, a, b, c, memorial, closed], pets: [kong, bori, dubu], now: now).map(\.schedule.id)
        XCTAssertEqual(order, [c.id, a.id, b.id, need.id]) // 다음 시간 순, 같으면 모신 순, 필요할 때만 맨 뒤, 추억·닫힌 줄 빼고

        var f = MedRules.Form()
        f.name = "유산균"
        f.form = .powder
        f.stock = "30"
        let med = try MedRules.add(f, in: context)
        let twice = schedule(.daily, times: ["08:00", "21:00"], pet: kong.id, med: med.id) // 하루 2포
        let half = schedule(.daily, times: ["08:00"], amount: 0.5, pet: bori.id, med: med.id) // 하루 반 포
        twice.unit = .pack
        half.unit = .pack
        XCTAssertEqual(MedSchedules.daysLeft(med, schedules: [twice, half], records: []), 12) // 30 ÷ 2.5 = 12
        XCTAssertEqual(MedSchedules.stockText(med, schedules: [twice, half], records: []), "남은 약 12일분")
        XCTAssertEqual(MedSchedules.stockText(med, schedules: [], records: []), "남은 30포")
        let every3 = schedule(.everyNDays, times: ["08:00"], every: 3, pet: kong.id, med: med.id)
        every3.unit = .pack
        XCTAssertEqual(MedSchedules.dailyAmount(every3), 1.0 / 3, accuracy: 0.0001)
        XCTAssertEqual(MedSchedules.dailyAmount(schedule(.weekdays, weekdays: [1, 4])), 2.0 / 7, accuracy: 0.0001)
        XCTAssertEqual(MedSchedules.dailyAmount(schedule(.everyNHours, times: [], hours: 8)), 3)
        XCTAssertEqual(MedSchedules.dailyAmount(schedule(.asNeeded, times: [])), 0)
    }

    func testAddMakesOneRowPerPet() throws {
        let kong = UUID(), bori = UUID(), med = UUID()
        var f = MedSchedules.Form()
        XCTAssertEqual(Set(MedSchedules.errors(f).keys), [.pets, .med])
        f.petIds = [kong, bori]
        f.medicationId = med
        f.dose = "0.5"
        f.frequency = .everyNDays
        f.everyDays = "1"
        XCTAssertEqual(MedSchedules.errors(f), [.everyDays: "2 이상 숫자로 넣어 주세요"])
        f.everyDays = "2"
        f.times = ["21:00", "08:00"]
        f.startDate = at(2026, 10, 27, 0) // 날짜 고르기는 그날 0시
        let now = at(2026, 10, 27, 9)
        let rows = try MedSchedules.add(f, unit: .pill, now: now, in: context)
        XCTAssertEqual(rows.map(\.petId), [kong, bori])
        XCTAssertEqual(rows.map(\.activeFrom), [now, now])
        XCTAssertEqual(rows.map(\.seriesId), rows.map(\.id)) // 각자
        XCTAssertEqual(rows[0].times, ["08:00", "21:00"])
        XCTAssertEqual(rows[0].amount, 0.5)
        XCTAssertEqual(rows[0].startDate, "2026-10-27") // 6시 기준으로 하루 당기지 않음
        XCTAssertNil(rows[0].everyHours)
        f.frequency = .weekdays
        XCTAssertEqual(MedSchedules.errors(f), [.weekdays: "요일을 하나 이상 골라 주세요"])
    }
}
