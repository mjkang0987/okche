import SwiftData
import XCTest
@testable import Okche

/// 문안 탭 규칙(plan.md 7-0). 안드로이드 MainCardTest와 같은 경우.
@MainActor
final class MainCardTests: XCTestCase {
    private let seoul = TimeZone(identifier: "Asia/Seoul")!
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    func testPillBirthdayFamilyDayAndOverlap() {
        XCTAssertEqual(MainCard.pill(birthDate: "2019-10-25", birthEstimated: false, familyDate: nil, day: "2026-10-25"), "오늘 생일")
        XCTAssertNil(MainCard.pill(birthDate: "2019-10-25", birthEstimated: true, familyDate: nil, day: "2026-10-25")) // 어림잡은 생일
        XCTAssertNil(MainCard.pill(birthDate: "2019-10-24", birthEstimated: false, familyDate: nil, day: "2026-10-25"))
        XCTAssertEqual(MainCard.pill(birthDate: nil, birthEstimated: false, familyDate: "2023-10-25", day: "2026-10-25"), "가족 된 날")
        XCTAssertNil(MainCard.pill(birthDate: nil, birthEstimated: false, familyDate: "2026-10-25", day: "2026-10-25")) // 가족 된 그날
        XCTAssertEqual(MainCard.pill(birthDate: "2019-10-25", birthEstimated: false, familyDate: "2023-10-25", day: "2026-10-25"), "오늘 생일")
        // 어림 생일이면 겹쳐도 가족 된 날
        XCTAssertEqual(MainCard.pill(birthDate: "2019-10-25", birthEstimated: true, familyDate: "2023-10-25", day: "2026-10-25"), "가족 된 날")
    }

    func testPillFeb29() {
        XCTAssertEqual(MainCard.pill(birthDate: "2020-02-29", birthEstimated: false, familyDate: nil, day: "2026-02-28"), "오늘 생일") // 평년
        XCTAssertNil(MainCard.pill(birthDate: "2020-02-29", birthEstimated: false, familyDate: nil, day: "2028-02-28")) // 윤년은 2/29에
        XCTAssertEqual(MainCard.pill(birthDate: "2020-02-29", birthEstimated: false, familyDate: nil, day: "2028-02-29"), "오늘 생일")
        XCTAssertNil(MainCard.pill(birthDate: "2020-02-29", birthEstimated: false, familyDate: nil, day: "2026-03-01"))
    }

    func testDetailLineAndDayTitle() {
        let pet = Pet(name: "콩이", species: .cat, breed: "코리안숏헤어", furColor: "", birthDate: "2019-03-01")
        XCTAssertEqual(MainCard.detail(pet, today: at(2026, 10, 25, 9)), "고양이 · 코리안숏헤어 · 7살")
        let noBirth = Pet(name: "보리", species: .dog, breed: "", furColor: "")
        XCTAssertEqual(MainCard.detail(noBirth), "강아지") // 품종·생일 없으면 뺌
        XCTAssertEqual(MainCard.dayTitle("2026-10-25"), "10월 25일 일요일")
    }

    func testTodayRecordsFiltersAndSorts() throws {
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        let kong = UUID(), bori = UUID()
        func add(_ pet: UUID?, _ kind: RecordKind, _ date: Date) -> Record {
            let record = Record(petId: pet, kind: kind, occurredAt: date, timeZone: seoul)
            context.insert(record)
            return record
        }
        let late = add(kong, .urine, at(2026, 10, 25, 9, 40))
        let early = add(kong, .meal, at(2026, 10, 25, 7, 20))
        _ = add(kong, .stool, at(2026, 10, 25, 5, 59)) // 6시 전 = 어제
        _ = add(bori, .vomit, at(2026, 10, 25, 8)) // 다른 아이
        _ = add(nil, .vomit, at(2026, 10, 25, 8)) // 누군지 모름
        _ = add(kong, .weight, at(2026, 10, 25, 8)) // 옥체는 그 단계에서
        let deleted = add(kong, .stool, at(2026, 10, 25, 8))
        deleted.deletedAt = .now
        let rows = MainCard.todayRecords(try context.fetch(FetchDescriptor<Record>()), petId: kong, day: "2026-10-25")
        XCTAssertEqual(rows.map(\.id), [early.id, late.id])
        XCTAssertEqual(rows.compactMap { MainCard.plainWord($0.kind) }, ["식사", "소변"])
        XCTAssertNil(MainCard.plainWord("vomit"))
    }
}
