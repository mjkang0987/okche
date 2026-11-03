import SwiftData
import XCTest
@testable import Okche

/// 자세히 시트 저장·고치기·의서 레이어 조건·기록 줄 값 글자(plan.md 7-2). 안드로이드 RecordDetailTest와 같은 경우.
@MainActor
final class RecordDetailTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul = TimeZone(identifier: "Asia/Seoul")!
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
    private func field(_ kind: RecordKind, _ key: String) -> RecordDetail.Field { RecordDetail.fields(kind).first { $0.key == key }! }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testSaveMultiSingleNumberAndEmpty() throws {
        let values: RecordDetail.Values = ["contents": ["foreign", "kibble"], "color": ["red"], "count": ["3"]]
        let vomit = try RecordDetail.save(.vomit, values: values, occurredAt: at(2026, 10, 25, 9), editing: nil, petId: nil,
                                          now: at(2026, 10, 25, 9, 5), timeZone: seoul, in: context)
        XCTAssertEqual(vomit.data, #"{"color":"red","contents":["kibble","foreign"],"count":3}"#) // 여럿은 시트 순서, 횟수는 숫자
        XCTAssertEqual(RecordDetail.values(vomit), ["contents": ["kibble", "foreign"], "color": ["red"], "count": ["3"]])
        XCTAssertEqual(QuickRecord.value(vomit), "사료 · 이물질 · 붉은색 · 3번 이상")

        let empty = try RecordDetail.save(.urine, values: [:], occurredAt: at(2026, 10, 25, 9), editing: nil, petId: nil,
                                          now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        XCTAssertEqual(empty.data, "{}") // 아무것도 안 골라도 기록
        XCTAssertEqual(QuickRecord.value(empty), "자세한 내용 없음")
    }

    func testLineSkipsUsualValues() throws {
        let vomit = try RecordDetail.save(.vomit, values: ["color": ["food"], "count": ["1"]], occurredAt: at(2026, 10, 25, 9),
                                          editing: nil, petId: nil, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        XCTAssertEqual(QuickRecord.value(vomit), "자세한 내용 없음") // 사료 색·1번은 생략
        let stool = try RecordDetail.save(.stool, values: ["shape": ["loose"], "color": ["brown"], "signs": ["mucus", "blood"]],
                                          occurredAt: at(2026, 10, 25, 9), editing: nil, petId: nil, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        XCTAssertEqual(QuickRecord.value(stool), "묽음 · 피 · 점액")
        let urine = try RecordDetail.save(.urine, values: ["amount": ["little"], "color": ["paleYellow"], "signs": ["straining"]],
                                          occurredAt: at(2026, 10, 25, 9), editing: nil, petId: nil, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        XCTAssertEqual(QuickRecord.value(urine), "찔끔 · 힘만 줌")
    }

    /// 시간 바꾸기: 6시 경계로 `day`를 다시 정하고, 지금보다 뒤는 지금으로.
    func testTimeChangeAndEditKeepsOthers() throws {
        let kong = UUID()
        let record = try QuickRecord.save(QuickRecord.rows[1], value: "normal", petId: kong, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        let created = record.createdAt
        try RecordDetail.save(.stool, values: ["shape": ["soft"], "signs": ["blood"]], occurredAt: at(2026, 10, 25, 5, 30), editing: record,
                              petId: nil, now: at(2026, 10, 25, 10), timeZone: seoul, in: context)
        XCTAssertEqual(record.day, "2026-10-24") // 5:30은 전날
        XCTAssertEqual(record.occurredAt, at(2026, 10, 25, 5, 30))
        XCTAssertEqual(record.updatedAt, at(2026, 10, 25, 10))
        XCTAssertEqual(record.petId, kong) // 고치기는 아이·만든 때 그대로
        XCTAssertEqual(record.createdAt, created)
        XCTAssertEqual(record.data, #"{"shape":"soft","signs":["blood"]}"#)

        let later = try RecordDetail.save(.urine, values: [:], occurredAt: at(2026, 10, 25, 23), editing: nil, petId: nil,
                                          now: at(2026, 10, 25, 10), timeZone: seoul, in: context)
        XCTAssertEqual(later.occurredAt, at(2026, 10, 25, 10))
    }

    /// 시간을 안 바꾼 고치기는 `day`를 그대로 둔다(다른 시간대에서 고쳐도).
    func testEditWithoutTimeChangeKeepsDay() throws {
        let record = try QuickRecord.save(QuickRecord.rows[0], value: "kibble", petId: nil, now: at(2026, 10, 25, 7), timeZone: seoul, in: context)
        try RecordDetail.save(.vomit, values: ["color": ["yellow"]], occurredAt: record.occurredAt, editing: record, petId: nil,
                              now: at(2026, 10, 25, 9), timeZone: TimeZone(identifier: "America/Los_Angeles")!, in: context)
        XCTAssertEqual(record.day, "2026-10-25")
        XCTAssertEqual(record.updatedAt, at(2026, 10, 25, 9))
        XCTAssertEqual(QuickRecord.value(record), "노란색")
    }

    func testToggle() {
        let contents = field(.vomit, "contents")
        var values = RecordDetail.toggle([:], field: contents, value: "kibble")
        values = RecordDetail.toggle(values, field: contents, value: "plant")
        XCTAssertEqual(values["contents"], ["kibble", "plant"])
        values = RecordDetail.toggle(values, field: contents, value: "kibble")
        XCTAssertEqual(values["contents"], ["plant"])
        let color = field(.vomit, "color")
        values = RecordDetail.toggle(values, field: color, value: "red")
        values = RecordDetail.toggle(values, field: color, value: "green")
        XCTAssertEqual(values["color"], ["green"]) // 하나 고르기는 바뀜
        values = RecordDetail.toggle(values, field: color, value: "green")
        XCTAssertNil(values["color"]) // 다시 누르면 풂
    }

    func testAlertOnlyWhenNewlyPicked() {
        XCTAssertTrue(RecordDetail.picksAlert(.vomit, before: [:], after: ["color": ["red"]]))
        XCTAssertTrue(RecordDetail.picksAlert(.vomit, before: [:], after: ["color": ["darkRed"]]))
        XCTAssertTrue(RecordDetail.picksAlert(.vomit, before: ["contents": ["kibble"]], after: ["contents": ["kibble", "foreign"]]))
        XCTAssertTrue(RecordDetail.picksAlert(.vomit, before: [:], after: ["count": ["3"]]))
        XCTAssertFalse(RecordDetail.picksAlert(.vomit, before: [:], after: ["count": ["2"], "color": ["yellow"]]))
        XCTAssertFalse(RecordDetail.picksAlert(.vomit, before: ["color": ["red"]], after: [:])) // 풀 때는 안 뜸
        XCTAssertFalse(RecordDetail.picksAlert(.vomit, before: ["color": ["red"]], after: ["color": ["red"], "count": ["2"]]))
        XCTAssertTrue(RecordDetail.picksAlert(.stool, before: [:], after: ["color": ["black"]]))
        XCTAssertFalse(RecordDetail.picksAlert(.stool, before: [:], after: ["color": ["pale"], "signs": ["blood"]])) // 회백색·피는 안 띄움
        XCTAssertTrue(RecordDetail.picksAlert(.urine, before: [:], after: ["signs": ["straining"]]))
        XCTAssertFalse(RecordDetail.picksAlert(.urine, before: [:], after: ["color": ["pinkRed"], "signs": ["pain"]]))
    }
}
