import SwiftData
import SwiftUI

/// 추억으로 간직하기·되돌리기·아이 지우기와 추억 화면 숫자(plan.md 6-4, 데이터 2장). 안드로이드 `PetMemorial.kt`와 같은 규칙.
enum PetLifecycle {
    /// 간직: `memorializedAt` = 지금, 열린 약 주기·인슐린·끼니 계획 `activeUntil` = 같은 시각, 열린 휴양 `endedAt` = 같은 시각.
    static func memorialize(_ pet: Pet, now: Date = .now, in context: ModelContext) throws {
        // 이미 간직 중이면 그대로(두 번 누름 — 간직 시각이 바뀌면 되돌릴 줄을 못 찾는다).
        guard pet.memorializedAt == nil else { return }
        let petId = pet.id
        let meds = try context.fetch(FetchDescriptor<MedSchedule>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == nil
        }))
        let insulin = try InsulinPlan.open(for: petId, in: context)
        let meals = try context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == nil
        }))
        let rests = try RestPeriod.open(for: petId, in: context)
        pet.memorializedAt = now
        pet.updatedAt = now
        for med in meds { med.activeUntil = now; med.updatedAt = now }
        for plan in insulin { plan.activeUntil = now; plan.updatedAt = now }
        for meal in meals { meal.activeUntil = now; meal.updatedAt = now }
        RestPeriod.apply(false, open: rests, petId: petId, now: now, in: context)
        try save(context)
    }

    /// 되돌리기: `memorializedAt` 없음 + 간직할 때 끝낸 줄(`activeUntil` = 그때 `memorializedAt`)을 같은 내용 새 줄로(`activeFrom` = 지금).
    /// 약 주기는 `seriesId` 그대로. 휴양은 다시 열지 않는다. 인슐린은 지금도 당뇨이고 열린 계획이 없을 때만(간직 중 당뇨를 껐거나
    /// 인슐린을 새로 설정했으면 6-2 규칙대로 그대로 둔다).
    static func restore(_ pet: Pet, now: Date = .now, in context: ModelContext) throws {
        guard let kept = pet.memorializedAt else { return }
        let petId = pet.id
        let until: Date? = kept
        let meds = try context.fetch(FetchDescriptor<MedSchedule>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == until
        }))
        let reopenInsulin = try pet.hasDiabetes && InsulinPlan.open(for: petId, in: context).isEmpty
        let insulin = reopenInsulin ? try context.fetch(FetchDescriptor<InsulinPlan>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == until
        })) : []
        let meals = try context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == until
        }))
        pet.memorializedAt = nil
        pet.updatedAt = now
        for m in meds {
            context.insert(MedSchedule(now: now, petId: petId, medicationId: m.medicationId, seriesId: m.seriesId, amount: m.amount,
                                       unit: m.unit, frequency: m.frequency, times: m.times, everyDays: m.everyDays,
                                       startDate: m.startDate, weekdays: m.weekdays, everyHours: m.everyHours, firstAt: m.firstAt,
                                       shiftOnLate: m.shiftOnLate, howTo: m.howTo, reminder: m.reminder,
                                       leadMinutes: m.leadMinutes, remindAgainMinutes: m.remindAgainMinutes, activeFrom: now))
        }
        for p in insulin {
            context.insert(InsulinPlan(now: now, petId: petId, name: p.name, syringe: p.syringe, slots: p.slots,
                                       ringDuringQuiet: p.ringDuringQuiet, leadMinutes: p.leadMinutes, activeFrom: now))
        }
        for m in meals {
            context.insert(MealPlan(now: now, petId: petId, mode: m.mode, slots: m.slots, activeFrom: now))
        }
        try save(context)
    }

    /// 아이 지우기: 아이와 그 아이의 기록·약 주기·인슐린·사료 바꾸기·끼니 계획·휴양 기간에 `deletedAt`.
    /// 사진은 그 아이 연결(`PhotoPet`)을 지우고, 남은 아이가 없는 사진도 지운다. '되돌릴 수 없어요'라서 선택 칸은 비운다(데이터 1-4 —
    /// 기록 `data`는 `{}`, 아이의 선택 칸, 인슐린 이름).
    static func delete(_ pet: Pet, now: Date = .now, in context: ModelContext) throws {
        let petId = pet.id
        func mark<T: PersistentModel>(_ rows: [T], _ deletedAt: ReferenceWritableKeyPath<T, Date?>,
                                      _ updatedAt: ReferenceWritableKeyPath<T, Date>) {
            for row in rows {
                row[keyPath: deletedAt] = now
                row[keyPath: updatedAt] = now
            }
        }
        let records = try context.fetch(FetchDescriptor<Record>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        let meds = try context.fetch(FetchDescriptor<MedSchedule>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        let insulin = try context.fetch(FetchDescriptor<InsulinPlan>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        let foods = try context.fetch(FetchDescriptor<FoodChange>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        let meals = try context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        let rests = try context.fetch(FetchDescriptor<RestPeriod>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        let links = try context.fetch(FetchDescriptor<PhotoPet>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil }))
        // 이 아이 연결을 뺀 뒤에도 다른 아이가 남는 사진은 그대로 둔다.
        var orphanPhotos: [Photo] = []
        for link in links {
            let photoId = link.photoId
            let others = try context.fetchCount(FetchDescriptor<PhotoPet>(predicate: #Predicate {
                $0.photoId == photoId && $0.petId != petId && $0.deletedAt == nil
            }))
            if others == 0 {
                orphanPhotos += try context.fetch(FetchDescriptor<Photo>(predicate: #Predicate { $0.id == photoId && $0.deletedAt == nil }))
            }
        }
        pet.deletedAt = now
        pet.updatedAt = now
        pet.sex = nil
        pet.neutered = nil
        pet.birthDate = nil
        pet.familyDate = nil
        pet.diagnosis = nil
        pet.profilePhotoId = nil
        for record in records {
            record.data = "{}"
            record.slotKey = nil
        }
        for plan in insulin { plan.name = nil }
        for med in meds { med.howTo = nil }
        for food in foods { food.finishedOn = nil }
        for photo in orphanPhotos {
            photo.caption = nil
            photo.bodyView = nil
            photo.bodyScore = nil
            photo.recordId = nil
        }
        mark(records, \.deletedAt, \.updatedAt)
        mark(meds, \.deletedAt, \.updatedAt)
        mark(insulin, \.deletedAt, \.updatedAt)
        mark(foods, \.deletedAt, \.updatedAt)
        mark(meals, \.deletedAt, \.updatedAt)
        mark(rests, \.deletedAt, \.updatedAt)
        mark(links, \.deletedAt, \.updatedAt)
        mark(orphanPhotos, \.deletedAt, \.updatedAt)
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

    /// 추억 화면 숫자: 함께한 날(가족 된 날부터 오늘까지, 없으면 첫 기록 날부터, 둘 다 없으면 nil)·처음/마지막 기록 날짜(없으면 nil).
    struct MemorialStats: Equatable {
        var days: Int? = nil
        var first: String? = nil
        var last: String? = nil
    }

    static func stats(_ pet: Pet, today: Date = .now, calendar: Calendar = .okcheDevice, in context: ModelContext) throws -> MemorialStats {
        let petId = pet.id
        var firstQuery = FetchDescriptor<Record>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil },
                                                 sortBy: [SortDescriptor(\.occurredAt)])
        firstQuery.fetchLimit = 1
        var lastQuery = FetchDescriptor<Record>(predicate: #Predicate { $0.petId == petId && $0.deletedAt == nil },
                                                sortBy: [SortDescriptor(\.occurredAt, order: .reverse)])
        lastQuery.fetchLimit = 1
        let first = try context.fetch(firstQuery).first?.occurredAt
        let last = try context.fetch(lastQuery).first?.occurredAt
        let start = pet.familyDate ?? first.map { PetRegistration.day($0, calendar) }
        let style = Date.FormatStyle(date: .long, time: .omitted, locale: Locale(identifier: "ko_KR"), calendar: calendar,
                                     timeZone: calendar.timeZone)
        return MemorialStats(days: PetEditing.companionDays(start, today: today, calendar: calendar),
                             first: first.map { $0.formatted(style) }, last: last.map { $0.formatted(style) })
    }
}

/// 추억 화면(보드 `Memorial`, 문안 탭 자리): 머리 '콩이 · 추억으로 간직 중' + 낙관, 잠든 모습 자리, 함께한 날·처음/마지막 기록.
/// 컨디션 좋았던 날들·앨범·내보내기(11·13단계)와 편지·도움받을 곳(보류)은 아직 없음.
struct MemorialView: View {
    let pet: Pet
    @Environment(\.modelContext) private var context

    var body: some View {
        let stats = (try? PetLifecycle.stats(pet, in: context)) ?? .init()
        TabScreen(title: pet.name, subtitle: "추억으로 간직 중", trailing: { PetSeal() }) {
            sleepingBand
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 8) { cards(stats) }
                VStack(spacing: 8) { cards(stats) }
            }
        }
    }

    /// 잠든 모습 그림 자리(그림은 캐릭터 단계). 남색 띠에 별 몇 개.
    private var sleepingBand: some View {
        ZStack(alignment: .bottom) {
            OkcheColor.namsaekDeep
            Canvas { context, size in
                for (x, y, r) in [(0.11, 0.23, 1.6), (0.26, 0.15, 1.2), (0.84, 0.25, 1.6), (0.70, 0.13, 1.1),
                                  (0.92, 0.58, 1.2), (0.07, 0.67, 1.1), (0.50, 0.12, 1.4)] {
                    let rect = CGRect(x: x * size.width - r, y: y * size.height - r, width: r * 2, height: r * 2)
                    context.fill(Path(ellipseIn: rect), with: .color(OkcheColor.geumLight))
                }
            }
            Text("잠든 모습").okcheText(OkcheType.caption).foregroundStyle(OkcheColor.onDeepMuted)
                .frame(width: 120, height: 56)
                .overlay(UnevenRoundedRectangle(topLeadingRadius: 60, bottomLeadingRadius: 20, bottomTrailingRadius: 20,
                                                topTrailingRadius: 60)
                    .stroke(OkcheColor.onDeepMuted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                .padding(.bottom, 18)
        }
        .frame(height: 120)
        .accessibilityElement()
        .accessibilityLabel("별 아래 잠든 \(pet.name) 그림 자리")
        .accessibilityAddTraits(.isImage)
    }

    @ViewBuilder private func cards(_ stats: PetLifecycle.MemorialStats) -> some View {
        if let days = stats.days { card("함께한 날", "\(days.formatted())일", big: true) }
        card("처음 기록", stats.first ?? "없어요")
        card("마지막 기록", stats.last ?? "없어요")
    }

    private func card(_ label: String, _ value: String, big: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).okcheText(big ? OkcheType.heading : OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
            Text(label).okcheText(OkcheType.caption).foregroundStyle(OkcheColor.inkMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(value)")
    }
}
