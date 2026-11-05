import SwiftData
import XCTest
@testable import Okche

/// 탕약 알림 목록 계산(plan.md 8-4a). 안드로이드 MedNotifyTest와 같은 경우.
@MainActor
final class MedNotifyTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private func at(_ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        Calendar.okcheDevice.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h, minute: min))!
    }
    private lazy var kong = Pet(now: at(1, 9), name: "콩이", species: .cat, breed: "", furColor: "")
    private lazy var bori = Pet(now: at(2, 9), name: "보리", species: .cat, breed: "", furColor: "")
    private func med(_ name: String) -> Medication {
        Medication(name: name, category: .medicine, form: .pill, doseAmount: 1, unit: .pill)
    }
    private func row(_ p: Pet, _ m: Medication, _ times: [String], lead: Int = 10, again: Int = 0, reminder: Bool = true,
                     freq: MedFrequency = .daily, hours: Int? = nil, firstAt: Date? = nil) -> MedSchedule {
        MedSchedule(petId: p.id, medicationId: m.id, amount: 1, unit: .pill, frequency: freq, times: times, everyHours: hours,
                    firstAt: firstAt, reminder: reminder, leadMinutes: lead, remindAgainMinutes: again, activeFrom: at(20, 9))
    }
    private func alerts(_ rows: [MedSchedule], records: [Record] = [], pets: [Pet]? = nil, rests: [RestPeriod] = [], meds: [Medication],
                        on: Bool = true, now: Date? = nil) -> [MedNotify.Alert] {
        MedNotify.alerts(rows, records: records, pets: pets ?? [kong, bori], rests: rests, meds: meds, medTimeOn: on, now: now ?? at(27, 12))
    }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testLeadAndWindow() {
        let p = med("프레드니솔론")
        let list = alerts([row(kong, p, ["21:00"])], meds: [p])
        // 27일 12시부터 72시간: 27·28·29일 21:00 − 10분, 30일 20:50은 창 밖.
        XCTAssertEqual(list.filter { $0.kind == .due }.map(\.fireAt), [at(27, 20, 50), at(28, 20, 50), at(29, 20, 50)])
        XCTAssertEqual(list.first?.title, "탕약 · 오후 9:00")
        XCTAssertEqual(list.first?.body, "콩이 프레드니솔론 1알")
        XCTAssertEqual(list.first?.petId, kong.id)
        XCTAssertEqual(list.last?.kind, .more)
        XCTAssertEqual(list.last?.fireAt, at(29, 20, 51))
        XCTAssertEqual(list.last?.body, "옥체를 열면 알림이 이어져요")
        // 지난 차례(08:00)는 울리지 않음.
        XCTAssertFalse(alerts([row(kong, p, ["08:00"])], meds: [p]).contains { $0.fireAt == at(27, 7, 50) })
    }

    func testHandledAndExcluded() throws {
        let p = med("프레드니솔론")
        let s = row(kong, p, ["21:00"])
        let data = try JSONSerialization.data(withJSONObject: ["scheduleId": s.id.uuidString.lowercased(), "status": "given",
                                                               "medicationId": p.id.uuidString.lowercased(), "amount": 1, "unit": "pill"])
        let given = Record(petId: kong.id, kind: .med, occurredAt: at(27, 11), day: "2026-10-27",
                           slotKey: "\(s.id.uuidString.lowercased())|2026-10-27|21:00", data: String(decoding: data, as: UTF8.self))
        // 미리 준 오늘 차례는 빠지고 내일부터.
        XCTAssertEqual(alerts([s], records: [given], meds: [p]).first?.fireAt, at(28, 20, 50))
        XCTAssertTrue(alerts([row(kong, p, ["21:00"], reminder: false)], meds: [p]).isEmpty)
        XCTAssertTrue(alerts([s], meds: [p], on: false).isEmpty)
        let gone = Pet(now: at(3, 9), name: "별이", species: .cat, breed: "", furColor: "", memorializedAt: at(4, 9))
        XCTAssertTrue(alerts([row(gone, p, ["21:00"])], pets: [gone], meds: [p]).isEmpty)
        XCTAssertTrue(alerts([row(kong, p, ["21:00"], freq: .asNeeded)], meds: [p]).isEmpty)
        // 휴양 중(28일 끝)이면 27일만 빠짐.
        let rest = RestPeriod(petId: kong.id, startedAt: at(26, 9), endedAt: at(28, 9))
        XCTAssertEqual(alerts([s], rests: [rest], meds: [p]).first?.fireAt, at(28, 20, 50))
    }

    func testGroupingAndAgain() {
        let p = med("프레드니솔론"), u = med("유산균"), a = med("가바펜틴"), z = med("츄르약")
        let list = alerts([row(bori, u, ["21:00"]), row(kong, p, ["21:00"], again: 30)], meds: [p, u])
        let first = list[0]
        XCTAssertEqual(first.title, "탕약 · 오후 9:00")
        XCTAssertEqual(first.body, "콩이 프레드니솔론 1알 · 보리 유산균 1알") // 먼저 모신 아이부터
        XCTAssertEqual(first.petId, kong.id)
        let again = list.first { $0.kind == .again }
        XCTAssertEqual(again?.fireAt, at(27, 21, 30))
        XCTAssertEqual(again?.title, "아직 안 준 약이 있어요")
        XCTAssertEqual(again?.body, "콩이 프레드니솔론")
        // 넷이면 셋 + 외 1(같은 아이는 약 이름 순).
        let four = alerts([row(kong, p, ["21:00"]), row(kong, u, ["21:00"]), row(kong, a, ["21:00"]), row(bori, z, ["21:00"])], meds: [p, u, a, z])
        XCTAssertEqual(four[0].body, "콩이 가바펜틴 1알 · 콩이 유산균 1알 · 콩이 프레드니솔론 1알 외 1")
    }

    func testCapAndMore() {
        let p = med("프레드니솔론")
        // 1시간마다 → 72시간에 72개 → 63개 + 이어짐.
        let s = row(kong, p, [], lead: 0, freq: .everyNHours, hours: 1, firstAt: at(27, 12, 30))
        let list = alerts([s], meds: [p])
        XCTAssertEqual(list.count, 64)
        XCTAssertEqual(list.filter { $0.kind == .due }.count, MedNotify.limit)
        XCTAssertEqual(list.last?.fireAt, list[62].fireAt.addingTimeInterval(60))
        XCTAssertEqual(Set(list.map(\.id)).count, 64)
    }

    /// 다시 알림만 바꾸면 그 줄을 고침(5장), 새 줄은 먹이는 방법·늦게 줬을 때도 그대로.
    func testAgainChangeSameRow() throws {
        let p = med("프레드니솔론")
        context.insert(kong); context.insert(p)
        var f = MedSchedules.Form()
        f.petIds = [kong.id]; f.medicationId = p.id; f.howTo = .inFood; f.remindAgainMinutes = 10
        f.frequency = .everyNHours; f.shiftOnLate = false
        let s = try XCTUnwrap(MedSchedules.add(f, unit: .pill, in: context).first)
        XCTAssertEqual(s.howTo, .inFood)
        XCTAssertFalse(s.shiftOnLate)
        XCTAssertEqual(s.remindAgainMinutes, 10)
        let original = MedSchedules.form(s, firstAt: s.firstAt ?? .now)
        var next = original
        next.remindAgainMinutes = 60
        XCTAssertEqual(try MedSchedules.change(s, from: original, to: next, in: context), .sameRow)
        XCTAssertEqual(s.remindAgainMinutes, 60)
    }
}
