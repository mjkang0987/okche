import SwiftData
import XCTest
@testable import Okche

/// 탕약 차례·오늘 남은 차례·차례 기록(plan.md 8-3a, 데이터 5장 예 1~3·6-1·6-2). 안드로이드 MedSlotsTest와 같은 경우.
@MainActor
final class MedSlotsTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let pet = UUID()
    private let day = "2026-10-27"
    private func at(_ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        Calendar.okcheDevice.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h, minute: min))!
    }
    private func row(_ freq: MedFrequency = .daily, times: [String] = [], hours: Int? = nil, firstAt: Date? = nil, shift: Bool = true,
                     med: UUID = UUID(), series: MedSchedule? = nil, from: Date, until: Date? = nil) -> MedSchedule {
        let s = MedSchedule(petId: pet, medicationId: series?.medicationId ?? med, seriesId: series?.seriesId, amount: 1, unit: .pill,
                            frequency: freq, times: times, everyHours: hours, firstAt: firstAt, shiftOnLate: shift, activeFrom: from)
        s.activeUntil = until
        return s
    }
    private func given(_ s: MedSchedule, key: String, at time: Date, day: String? = nil, status: String = "given") -> Record {
        let data = try! JSONSerialization.data(withJSONObject: ["scheduleId": s.id.uuidString.lowercased(), "status": status,
                                                                "medicationId": s.medicationId.uuidString.lowercased(), "amount": 1, "unit": "pill"])
        return Record(petId: pet, kind: .med, occurredAt: time, day: day, slotKey: key, data: String(decoding: data, as: UTF8.self))
    }
    private func key(_ s: MedSchedule, _ time: String, _ d: String = "2026-10-27") -> String { "\(s.id.uuidString.lowercased())|\(d)|\(time)" }
    private func remaining(_ rows: [MedSchedule], _ records: [Record]) -> [Date] {
        MedSlots.remaining(rows, records: records, petId: pet, day: day).map(\.due)
    }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testFirstDayAndBefore6() {
        // 처음 만든 날은 activeFrom 뒤 시간부터, 06:00 전 시간은 다음 날 새벽.
        let s = row(times: ["08:00", "20:00", "02:00"], from: at(27, 10))
        XCTAssertEqual(remaining([s], []), [at(27, 20), at(28, 2)])
        // 처리한 차례는 빠지고(건너뜀도), 시간이 지나도 남는다.
        let old = row(times: ["08:00", "20:00"], from: at(20, 9))
        XCTAssertEqual(remaining([old], [given(old, key: key(old, "08:00"), at: at(27, 8, 5), status: "skipped")]), [at(27, 20)])
        XCTAssertEqual(remaining([old], []), [at(27, 8), at(27, 20)])
    }

    func testChangedDayExamples() {
        // 예 1: 08:00에 주고 10:00에 09:00·21:00으로 → 09:00 넘김, 21:00만.
        let s1 = row(times: ["08:00", "20:00"], from: at(20, 9), until: at(27, 10))
        let s2 = row(times: ["09:00", "21:00"], series: s1, from: at(27, 10))
        let g = given(s1, key: key(s1, "08:00"), at: at(27, 8))
        XCTAssertEqual(remaining([s1, s2], [g]), [at(27, 21)])
        // 예 2: 안 주고 바꾸면 옛 08:00은 버리고 새 09:00(지났어도)·21:00.
        XCTAssertEqual(remaining([s1, s2], []), [at(27, 9), at(27, 21)])
        // 예 3: 08:00에 주고 08:30에 S2, 10:00에 S3 → k = 1.
        let a1 = row(times: ["08:00", "20:00"], from: at(20, 9), until: at(27, 8, 30))
        let a2 = row(times: ["09:00", "21:00"], series: a1, from: at(27, 8, 30), until: at(27, 10))
        let a3 = row(times: ["10:30", "22:00"], series: a1, from: at(27, 10))
        XCTAssertEqual(remaining([a3, a1, a2], [given(a1, key: key(a1, "08:00"), at: at(27, 8))]), [at(27, 22)])
        // 필요할 때만 → 매일로 바꾼 날도 바꾼 날 규칙(k = 0, 지난 08:00도 뜸).
        let n1 = row(.asNeeded, from: at(20, 9), until: at(27, 10))
        let n2 = row(times: ["08:00", "20:00"], series: n1, from: at(27, 10))
        XCTAssertEqual(remaining([n1, n2], []), [at(27, 8), at(27, 20)])
        // 같은 주기에 열린 줄이 둘(백업 병합)이면 activeFrom이 늦은 것 하나만.
        let o1 = row(times: ["08:00"], from: at(20, 9))
        let o2 = row(times: ["09:00"], series: o1, from: at(21, 9))
        XCTAssertEqual(remaining([o1, o2], []), [at(27, 9)])
        // 빠진 열린 줄로 준 기록은 짝 없는 기록 — 남은 차례 앞에서 하나 처리(하루 세 번 먹이지 않게).
        XCTAssertEqual(remaining([o1, row(times: ["09:00", "21:00"], series: o1, from: at(27, 7))],
                                 [given(o1, key: key(o1, "08:00"), at: at(27, 8))]), [at(27, 21)])
        // 바꾼 다음 날은 새 줄 그대로.
        XCTAssertEqual(MedSlots.remaining([s1, s2], records: [g], petId: pet, day: "2026-10-28").map(\.due), [at(28, 9), at(28, 21)])
    }

    func testHoursShiftAndUnmatched() {
        let first = at(27, 8)
        let late = row(.everyNHours, hours: 8, firstAt: first, from: at(27, 7))
        let g = given(late, key: "\(late.id.uuidString.lowercased())|#0", at: at(27, 9))
        // 늦게 줬을 때 밀림: #1 = 09:00 + 8 = 17:00, #2 = 01:00(새벽이라 같은 날).
        XCTAssertEqual(remaining([late], [g]), [at(27, 17), at(28, 1)])
        // 처음 먹일 때가 저장한 순간보다 조금 앞이어도(분 올림) 0번 차례는 남음.
        XCTAssertEqual(remaining([row(.everyNHours, hours: 8, firstAt: first, from: at(27, 8, 0) + 30)], []).first, first)
        // 옛 firstAt으로 다시 연 줄(추억 되돌리기)은 연 때 뒤 차례만.
        XCTAssertEqual(remaining([row(.everyNHours, hours: 8, firstAt: at(13, 8), from: at(27, 20))], []), [at(28, 0)])
        // 처음 정한 시간대로: 16:00, 00:00.
        let fixed = row(.everyNHours, hours: 8, firstAt: first, shift: false, from: at(27, 7))
        XCTAssertEqual(remaining([fixed], [given(fixed, key: "\(fixed.id.uuidString.lowercased())|#0", at: at(27, 9))]), [at(27, 16), at(28, 0)])
        // 짝 없는 기록: 닫힌 옛 줄 키로 오늘 준 기록 → 남은 차례 앞에서 하나 처리.
        let old = row(times: ["08:00"], from: at(20, 9), until: at(26, 12))
        let now = row(times: ["08:00", "20:00"], series: old, from: at(26, 12))
        XCTAssertEqual(remaining([old, now], [given(old, key: key(old, "08:00"), at: at(27, 8, 10))]), [at(27, 20)])
    }

    func testButtonsAndRecord() throws {
        let a = row(times: ["08:00", "20:00"], from: at(20, 9))
        let b = row(times: ["09:00"], from: at(20, 9))
        let c = row(times: ["10:00"], from: at(20, 9))
        let d = row(times: ["11:00"], from: at(20, 9))
        let buttons = MedSlots.buttons(MedSlots.remaining([a, b, c, d], records: [], petId: pet, day: day))
        XCTAssertEqual(buttons.map(\.due), [at(27, 8), at(27, 9), at(27, 10)]) // 같은 약 하나·최대 3
        XCTAssertTrue(MedSlots.hasOpen([a], petId: pet))
        XCTAssertFalse(MedSlots.hasOpen([a], petId: UUID()))

        // 누르면 기록(day = 차례의 날 — 저녁 20:00 차례를 다음 날 06:30에 줘도 전날) → 빠짐, 지우면 돌아옴.
        let med = Medication(name: "프레드니솔론", shortName: "프레드", category: .medicine, form: .pill, doseAmount: 1, unit: .pill)
        context.insert(med)
        let s = row(times: ["20:00"], med: med.id, from: at(20, 9))
        context.insert(s)
        let slot = MedSlots.remaining([s], records: [], petId: pet, day: day)[0]
        let record = try MedSlots.record(slot, now: at(28, 6, 30), in: context)
        XCTAssertEqual([record.day, record.slotKey], [day, key(s, "20:00")])
        XCTAssertEqual(QuickRecord.value(record), "프레드니솔론 1알")
        XCTAssertTrue(MedSlots.remaining([s], records: [record], petId: pet, day: day).isEmpty)
        try QuickRecord.delete(record, in: context)
        XCTAssertEqual(MedSlots.remaining([s], records: [record], petId: pet, day: day).count, 1)
        XCTAssertEqual(MedSlots.shortName(med), "프레드")
        XCTAssertEqual(MedSlots.value(given(s, key: "x", at: at(27, 9), status: "skipped"), meds: [med]), "프레드니솔론 건너뜀")
        XCTAssertEqual(MedSlots.value(given(s, key: "x", at: at(27, 9), status: "vomited"), meds: [med]), "프레드니솔론 먹고 토함")
        XCTAssertEqual(MedSlots.spokenTime(at(27, 21)), "오후 9시")
    }

    /// 8-3b: 오늘 모든 차례(기록한 것 포함)·여러 개 한 번에·예정에 없던 약·기록 고치기·고르기 순서.
    func testTodaySheetAndExtra() throws {
        let med = Medication(name: "프레드니솔론", category: .medicine, form: .pill, doseAmount: 1, unit: .pill, stockAmount: 10)
        let syrup = Medication(name: "위장약 시럽", category: .medicine, form: .liquid, doseAmount: 2, unit: .ml)
        let other = Medication(name: "유산균", category: .supplement, form: .powder, doseAmount: 1, unit: .pack)
        [med, syrup, other].forEach { context.insert($0) }
        let s = row(times: ["08:00", "20:00"], med: med.id, from: at(20, 9))
        let need = row(.asNeeded, med: syrup.id, from: at(20, 9))
        [s, need].forEach { context.insert($0) }
        // 08:00은 줬고 20:00은 남음 — 시트에는 둘 다, 기록한 차례는 그 기록과 함께.
        let given8 = given(s, key: key(s, "08:00"), at: at(27, 8, 5))
        let entries = MedSlots.today([s, need], records: [given8], petId: pet, day: day)
        XCTAssertEqual(entries.map(\.slot.due), [at(27, 8), at(27, 20)])
        XCTAssertTrue(entries[0].record === given8)
        XCTAssertNil(entries[1].record)
        XCTAssertEqual(MedSlots.hhmm(at(27, 8, 5)), "08:05")
        XCTAssertEqual(MedSlots.clockText(at(27, 20)), "오후 8:00")
        XCTAssertEqual(MedSlots.slotDetail(s, med: med), "알약 · 1알")

        // 기록하기: 고른 차례마다(건너뜀도) 한 번에.
        let saved = try MedSlots.record([(entries[1].slot, "skipped")], now: at(27, 21), in: context)
        XCTAssertEqual(saved.map(\.slotKey), [key(s, "20:00")])
        XCTAssertEqual(QuickRecord.value(saved[0]), "프레드니솔론 건너뜀")
        XCTAssertTrue(MedSlots.remaining([s, need], records: [given8] + saved, petId: pet, day: day).isEmpty)

        // 예정에 없던 약: 키 없음, 약방 남은 양에서 빠짐.
        let extra = try MedSlots.saveExtra(petId: pet, medication: med, amount: 2, status: "given", occurredAt: at(27, 12), in: context)
        XCTAssertNil(extra.slotKey)
        XCTAssertNil(MedSlots.json(extra)["scheduleId"])
        XCTAssertEqual(MedRules.remaining(med, records: [extra]), 8)
        XCTAssertTrue(MedSlots.remaining([s], records: [extra], petId: pet, day: day).count == 2) // 차례는 그대로

        // 고치기: 양·상태·시간. 차례 기록은 차례의 날 그대로, 예정에 없던 약은 바뀐 시각의 날.
        let slotRecord = try MedSlots.record(entries[1].slot, now: at(27, 21), in: context)
        try MedSlots.saveExtra(petId: pet, medication: med, amount: 1, status: "vomited", occurredAt: at(28, 7), editing: slotRecord, in: context)
        XCTAssertEqual([slotRecord.day, QuickRecord.value(slotRecord)], [day, "프레드니솔론 먹고 토함"])
        try MedSlots.saveExtra(petId: pet, medication: med, amount: 2, status: "given", occurredAt: at(28, 7), editing: extra, in: context)
        XCTAssertEqual(extra.day, "2026-10-28")

        // 고르기 순서: 이 아이의 필요할 때만 → 먹는 약 → 나머지.
        let kong = Pet(id: pet, name: "콩이", species: .cat, breed: "", furColor: "")
        let options = MedSlots.extraOptions([other, med, syrup], schedules: [s, need], pet: kong)
        XCTAssertEqual(options.map(\.med.name), ["위장약 시럽", "프레드니솔론", "유산균"])
        XCTAssertEqual(options.map(\.detail), ["물약 · 필요할 때만", "알약 · 콩이가 먹는 약", "가루"])
    }
}
