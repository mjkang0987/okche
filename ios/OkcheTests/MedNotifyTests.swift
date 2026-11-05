import SwiftData
import UserNotifications
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
        // 초가 붙은 차례(12:30:12)도 분 단위로 내려 울림.
        let odd = row(kong, p, [], lead: 0, freq: .everyNHours, hours: 8, firstAt: at(27, 12, 30).addingTimeInterval(12.345))
        XCTAssertEqual(alerts([odd], meds: [p]).first?.fireAt, at(27, 12, 30))
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

    /// 8-4b: 휴대폰 알림 요청 — 같은 id·제목·내용, 그 분에 울림, 누르면 열 아이.
    func testRequestFromAlert() throws {
        let alert = MedNotify.Alert(id: "med.due.1", kind: .due, fireAt: at(27, 20, 50), title: "탕약 · 오후 9:00", body: "콩이 프레드니솔론 1알", petId: kong.id)
        let request = MedReminders.request(alert)
        XCTAssertEqual(request.identifier, "med.due.1")
        XCTAssertEqual(request.content.title, "탕약 · 오후 9:00")
        XCTAssertEqual(request.content.body, "콩이 프레드니솔론 1알")
        XCTAssertEqual(request.content.userInfo["petId"] as? String, kong.id.uuidString)
        let trigger = try XCTUnwrap(request.trigger as? UNCalendarNotificationTrigger)
        XCTAssertEqual(Calendar.current.date(from: trigger.dateComponents), at(27, 20, 50))
        XCTAssertFalse(trigger.repeats)
        XCTAssertTrue(MedReminders.request(MedNotify.Alert(id: "med.more", kind: .more, fireAt: at(27, 21), title: "옥체", body: "", petId: nil)).content.userInfo.isEmpty)
    }

    /// 8-4c 남은 약: 기준 이하면 오늘 10시(지났으면 내일), 아니면 기준이 되는 날 10시, 보낸 것·밤 쉬기·끔·남은 양 없음은 빠짐.
    func testLowMed() {
        let p = Medication(name: "프레드니솔론", category: .medicine, form: .pill, doseAmount: 1, unit: .pill, stockAmount: 2, stockCountedAt: at(20, 9))
        let daily = row(kong, p, ["08:00"]) // 하루 1알 → 2일분
        func low(_ m: Medication, now: Date, days: Int = 3, quiet: QuietHours? = nil, sent: Set<String> = [], on: Bool = true) -> [MedNotify.Alert] {
            MedNotify.lowMedAlerts(meds: [m], schedules: [daily], records: [], on: on, days: days, quiet: quiet, sent: sent, now: now)
        }
        let early = low(p, now: at(27, 9))
        XCTAssertEqual(early.map(\.fireAt), [at(27, 10)])
        XCTAssertEqual(early.first?.title, "약방")
        XCTAssertEqual(early.first?.body, "프레드니솔론이 2일분 남았어요")
        XCTAssertEqual(early.first?.medId, p.id)
        XCTAssertEqual(low(p, now: at(27, 12)).map(\.fireAt), [at(28, 10)])
        // 5일분, 기준 3 → 이틀 뒤 10시 '3일분'.
        p.stockAmount = 5
        let ahead = low(p, now: at(27, 12))
        XCTAssertEqual(ahead.map(\.fireAt), [at(29, 10)])
        XCTAssertEqual(ahead.first?.body, "프레드니솔론이 3일분 남았어요")
        XCTAssertTrue(low(p, now: at(27, 12), days: 0).isEmpty) // 닷새 뒤는 3일 창 밖
        XCTAssertTrue(low(p, now: at(27, 12), sent: [MedNotify.sentKey(p)]).isEmpty)
        XCTAssertTrue(low(p, now: at(27, 12), quiet: QuietHours(enabled: true, start: "22:00", end: "11:00")).isEmpty)
        XCTAssertEqual(low(p, now: at(27, 12), quiet: QuietHours(enabled: false, start: "22:00", end: "11:00")).count, 1)
        XCTAssertTrue(low(p, now: at(27, 12), on: false).isEmpty)
        p.stockAmount = nil
        XCTAssertTrue(low(p, now: at(27, 12)).isEmpty)
        // 탕약은 남은 자리만큼.
        let s = row(kong, p, [], lead: 0, freq: .everyNHours, hours: 1, firstAt: at(27, 12, 30))
        XCTAssertEqual(MedNotify.alerts([s], records: [], pets: [kong], rests: [], meds: [p], medTimeOn: true, limit: 60, now: at(27, 12)).filter { $0.kind == .due }.count, 60)
        XCTAssertTrue(MedNotify.inQuiet("23:30", QuietHours(enabled: true, start: "22:00", end: "07:00")))
        XCTAssertFalse(MedNotify.inQuiet("07:00", QuietHours(enabled: true, start: "22:00", end: "07:00")))
        XCTAssertTrue(MedNotify.wantsReminders([daily]))
        XCTAssertFalse(MedNotify.wantsReminders([row(kong, p, ["08:00"], reminder: false)]))
        XCTAssertEqual(NotifySettingsView.weekly(WeightReminder(weekday: 7, time: "10:00")), "매주 일요일 오전 10시")
    }
}
