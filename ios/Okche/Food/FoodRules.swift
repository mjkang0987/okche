import Foundation
import SwiftData

/// 사료 바꾸기 규칙(plan.md 7-4a, 데이터 8장). 안드로이드 `FoodRules.kt`와 같은 규칙.
enum FoodRules {
    /// '사료 다 바꿨나요?'를 묻는 날(먹기 시작한 날 = 1일째).
    static let steps = [7, 10, 12, 14]

    /// 그날(`YYYY-MM-DD`) 먹던 사료 바꾸기: 그날까지 시작한 것 중 가장 늦은 `startedOn`(같으면 늦게 만든 것).
    static func change(at day: String, petId: UUID, in changes: [FoodChange]) -> FoodChange? {
        changes.filter { $0.petId == petId && $0.deletedAt == nil && $0.startedOn <= day }
            .max { ($0.startedOn, $0.createdAt) < ($1.startedOn, $1.createdAt) }
    }

    static func allChanges(in context: ModelContext) throws -> [FoodChange] {
        try context.fetch(FetchDescriptor<FoodChange>(predicate: #Predicate { $0.deletedAt == nil }))
    }

    /// 그날 먹던 사료 ID — 수라 기록 `foodId`.
    static func foodId(petId: UUID?, day: String, in context: ModelContext) throws -> UUID? {
        guard let petId else { return nil }
        return change(at: day, petId: petId, in: try allChanges(in: context))?.foodId
    }

    static func food(_ id: UUID?, in context: ModelContext) throws -> Food? {
        guard let id else { return nil }
        var descriptor = FetchDescriptor<Food>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        return try context.fetch(descriptor).first
    }

    /// 전에 먹던 사료: 이 아이가 먹었던 사료 이름, 최근 바꾼 순, 지금 먹는 사료는 뺌.
    static func previousNames(petId: UUID, today: String, changes: [FoodChange], foods: [Food]) -> [String] {
        let current = change(at: today, petId: petId, in: changes)
        var seen = Set<UUID>()
        return changes.filter { $0.petId == petId && $0.deletedAt == nil && $0.foodId != current?.foodId }
            .sorted { ($0.startedOn, $0.createdAt) > ($1.startedOn, $1.createdAt) }
            .compactMap { change in
                guard seen.insert(change.foodId).inserted else { return nil }
                return foods.first { $0.id == change.foodId }?.name
            }
    }

    /// 사료 바꾸기 저장: 같은 이름(앞뒤 빈칸 뺀 글자 그대로)의 사료가 있으면 그 사료, 없으면 새 사료.
    @discardableResult
    static func save(petId: UUID, name: String, startedOn: String, method: FoodChangeMethod, now: Date = .now,
                     in context: ModelContext) throws -> FoodChange {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let food: Food
        if let same = try context.fetch(FetchDescriptor<Food>(predicate: #Predicate { $0.deletedAt == nil && $0.name == trimmed })).first {
            food = same
        } else {
            food = Food(now: now, name: trimmed)
            context.insert(food)
        }
        let change = FoodChange(now: now, petId: petId, foodId: food.id, startedOn: startedOn, method: method)
        context.insert(change)
        try save(context)
        return change
    }

    /// 아이 정보 줄 '가수분해 처방식 · 10/14부터'.
    static func line(_ change: FoodChange, food: Food?) -> String {
        let md = change.startedOn.split(separator: "-").dropFirst().compactMap { Int($0) }
        let from = md.count == 2 ? " · \(md[0])/\(md[1])부터" : ""
        return (food?.name ?? "") + from
    }

    /// 먹기 시작한 날을 1일째로 센 오늘.
    static func dayNumber(startedOn: String, today: String) -> Int? {
        let calendar = Calendar(identifier: .gregorian)
        guard let start = PetRegistration.date(startedOn, calendar), let end = PetRegistration.date(today, calendar),
              let days = calendar.dateComponents([.day], from: start, to: end).day else { return nil }
        return days + 1
    }

    /// '사료 다 바꿨나요?' 카드: 지금 먹는 사료 바꾸기 하나만, 단계 날부터 다음 단계 전날까지(14일째는 그날만),
    /// '아직이에요'를 누른 단계·'다 바꿨어요'면 안 띄움. (단계, N일째)
    static func check(_ change: FoodChange?, today: String) -> (step: Int, day: Int)? {
        guard let change, change.finishedOn == nil, let day = dayNumber(startedOn: change.startedOn, today: today), day <= 14,
              let step = steps.last(where: { $0 <= day }), step > change.checkedStep else { return nil }
        return (step, day)
    }

    /// 카드 안내 '아직이면 10일·12일·14일째에 한 번 더 물어볼게요.' — 14일째는 없음.
    static func laterNote(step: Int) -> String? {
        let later = steps.filter { $0 > step }
        return later.isEmpty ? nil : "아직이면 \(later.map { "\($0)일" }.joined(separator: "·"))째에 한 번 더 물어볼게요."
    }

    static func notYet(_ change: FoodChange, step: Int, now: Date = .now, in context: ModelContext) throws {
        change.checkedStep = step
        change.updatedAt = now
        try save(context)
    }

    static func finished(_ change: FoodChange, today: String, now: Date = .now, in context: ModelContext) throws {
        change.finishedOn = today
        change.updatedAt = now
        try save(context)
    }

    private static func save(_ context: ModelContext) throws {
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }
}
