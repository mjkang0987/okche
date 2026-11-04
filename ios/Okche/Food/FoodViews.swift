import SwiftData
import SwiftUI

/// 사료 바꾸기 시트(보드 `Food`): 사료 이름 + 전에 먹던 사료, 먹기 시작한 날, 어떻게 바꾸나요, 사료 바꾸는 법 보기, '바꾸기'.
/// 성분표 사진 칸은 11단계(사진)까지 숨김.
struct FoodChangeSheet: View {
    let pet: Pet
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<FoodChange> { $0.deletedAt == nil }) private var changes: [FoodChange]
    @Query(filter: #Predicate<Food> { $0.deletedAt == nil }) private var foods: [Food]
    @State private var name = ""
    @State private var startedOn: Date? = .now
    @State private var method = FoodChangeMethod.gradual
    @State private var nameError: String?
    @State private var guiding = false
    @State private var failed = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("\(pet.name) · 사료 바꾸기").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                            .accessibilityAddTraits(.isHeader)
                        Spacer(minLength: 0)
                        Button { dismiss() } label: {
                            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                        }
                        .accessibilityLabel("닫기")
                    }
                    OkcheTextField(label: "사료 이름", text: $name, placeholder: "예: 가수분해 처방식 1.5kg", error: nameError)
                        .onChange(of: name) { if !name.trimmingCharacters(in: .whitespaces).isEmpty { nameError = nil } }
                    let previous = FoodRules.previousNames(petId: pet.id, today: OkcheDay.of(.now), changes: changes, foods: foods)
                    if !previous.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("전에 먹던 사료").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                                .accessibilityAddTraits(.isHeader)
                            // 누르면 이름 칸을 채운다.
                            OkcheChoiceChips(options: previous.map { ($0, $0) }, isOn: { $0 == name.trimmingCharacters(in: .whitespaces) },
                                             onTap: { name = $0 })
                        }
                    }
                    DateField(label: "먹기 시작한 날", date: $startedOn, error: nil, clearable: false)
                    FieldGroup(title: "어떻게 바꾸나요?") {
                        OkcheChoiceCards(options: [(FoodChangeMethod.gradual, "조금씩 섞어서 바꿔요"), (.atOnce, "한 번에 바꿨어요")],
                                         selection: $method)
                    }
                    Button("\(FoodGuideSheet.speciesName(pet.species)) 사료 바꾸는 법 보기 ›") { guiding = true }
                        .okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                        .frame(minHeight: 44)
                    // 실록 금색 선 안내는 그 단계(10)에서 — 지금 안 되는 안내는 넣지 않는다.
                    Text("바꾼 지 7·10·12·14일째에 문안에서 다 바꿨는지 물어볼게요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 12).padding(.horizontal, 14)
                        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, OkcheSpacing.space6)
                .padding(.bottom, OkcheSpacing.space4)
            }
            VStack(spacing: 8) {
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button("바꾸기", action: save).buttonStyle(.okchePrimary)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, 10).padding(.bottom, OkcheSpacing.space4)
            .overlay(alignment: .top) { Rectangle().fill(OkcheColor.line).frame(height: 1) }
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .sheet(isPresented: $guiding) { FoodGuideSheet(species: pet.species) }
    }

    private func save() {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            nameError = "사료 이름을 넣어 주세요"
            AccessibilityNotification.Announcement("사료 이름을 넣어 주세요").post()
            return
        }
        do {
            try FoodRules.save(petId: pet.id, name: name, startedOn: PetRegistration.day(startedOn ?? .now, .okcheDevice),
                               method: method, in: context)
            AccessibilityNotification.Announcement("사료를 바꿨어요").post()
            dismiss()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 사료 바꾸는 법 레이어(보드 `FoodGuide`·`FoodGuideDog`, 출처 미국 동물병원협회).
struct FoodGuideSheet: View {
    let species: Species
    @Environment(\.dismiss) private var dismiss

    static func speciesName(_ species: Species) -> String { species == .cat ? "고양이" : "강아지" }

    private let mixes: [(days: String, newShare: Double, text: String)] = [
        ("1~2일", 0.25, "새 사료 4분의 1"), ("3~4일", 0.5, "반반"), ("5~6일", 0.75, "새 사료 4분의 3"), ("7일", 1, "새 사료만"),
    ]

    var body: some View {
        let cat = species == .cat
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    (Text("사료 바꾸는 법") + Text(" · \(Self.speciesName(species))").font(.subheadline).fontWeight(.medium)
                        .foregroundColor(OkcheColor.inkMuted))
                        .okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                Text(cat ? "갑자기 바꾸면 배탈이 나거나 그 사료를 싫어하게 될 수 있어서, 고양이는 천천히 바꾸길 권해요. 길게는 40일까지 걸릴 수 있어요."
                         : "갑자기 바꾸면 배탈이 날 수 있어서, 강아지는 7일에 걸쳐 천천히 바꾸길 권해요.")
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                mixCard(cat: cat)
                if cat {
                    bullets("권고사항", [
                        "습식·건식처럼 사료 모양이 바뀌면 한두 달에 걸쳐 바꾸길 권해요.",
                        "너무 차갑거나 뜨겁지 않게, 체온 정도로 맞춰 주길 권해요.",
                        "메스껍거나 토할 때는 새 사료를 잠시 미루길 권해요. 그때 먹은 사료를 길게는 40일까지 싫어할 수 있어요.",
                        "하루에 필요한 만큼은 먹어야 하니, 서두르지 말고 새 사료를 먹을 때까지 천천히 이어 가길 권해요.",
                    ])
                }
                VStack(alignment: .leading, spacing: 8) {
                    bullets("잘 안 먹을 때 권하는 방법", ["캔 사료를 조금 올려 주기", cat ? "싱겁게 끓인 육수를 조금 더하기(참치·조개·닭)" : "싱겁게 끓인 육수를 조금 더하기(닭·소고기)"])
                    Text("처방식을 먹는 아이는 처방식만 먹어야 할 수 있어요. 무엇이든 더하기 전에 병원에 먼저 물어보길 권해요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.namsaekDeep)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 12).padding(.horizontal, 14)
                        .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                }
                SourceNote(text: "출처: 미국 동물병원협회 「새 사료로 바꾸는 요령과 기간」. 위 내용은 이 단체의 권고사항이고, 아이마다 다를 수 있어요.")
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.vertical, OkcheSpacing.space6)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    /// 권하는 섞는 순서: 날짜 + 옛 사료·새 사료 비율 막대 + 글자.
    private func mixCard(cat: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("권하는 섞는 순서").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
            HStack(spacing: 6) {
                Circle().fill(OkcheColor.namsaekSoft).overlay(Circle().strokeBorder(OkcheColor.lineStrong, lineWidth: 1)).frame(width: 12, height: 12)
                Text("옛 사료")
                Circle().fill(OkcheColor.namsaek).frame(width: 12, height: 12).padding(.leading, 6)
                Text("새 사료")
            }
            .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            .accessibilityHidden(true)
            ForEach(mixes, id: \.days) { mix in
                HStack(spacing: 12) {
                    Text(mix.days).okcheText(OkcheType.label, weight: .bold).foregroundStyle(OkcheColor.namsaekDeep)
                        .frame(minWidth: 56, alignment: .leading)
                    VStack(alignment: .leading, spacing: 4) {
                        GeometryReader { geo in
                            HStack(spacing: 0) {
                                OkcheColor.namsaekSoft.frame(width: geo.size.width * (1 - mix.newShare))
                                OkcheColor.namsaek
                            }
                        }
                        .frame(height: 12)
                        .clipShape(Capsule())
                        .overlay(Capsule().strokeBorder(OkcheColor.lineStrong, lineWidth: 1))
                        .accessibilityHidden(true)
                        Text(mix.text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.ink)
                    }
                }
                .accessibilityElement(children: .combine)
            }
            Text(cat ? "처음엔 새 사료를 4분의 1만 섞고, 잘 먹는 만큼 늘리길 권해요. 잘 안 먹으면 그 단계에 며칠 더 머물러도 돼요."
                     : "처음엔 새 사료를 4분의 1만 섞고, 잘 먹는 만큼 늘리길 권해요.")
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
    }

    private func bullets(_ title: String, _ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
            ForEach(items, id: \.self) { item in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Circle().fill(OkcheColor.namsaek).frame(width: 6, height: 6).alignmentGuide(.firstTextBaseline) { $0[.bottom] + 2 }
                        .accessibilityHidden(true)
                    Text(item).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                }
            }
        }
    }
}

/// 문안 '사료 다 바꿨나요?' 카드(보드 `MainFoodCheck`): 지금 먹는 사료 바꾸기의 7·10·12·14일째.
struct FoodCheckCard: View {
    let pet: Pet
    let day: String
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<FoodChange> { $0.deletedAt == nil }) private var changes: [FoodChange]
    @Query(filter: #Predicate<Food> { $0.deletedAt == nil }) private var foods: [Food]
    @State private var guiding = false
    @State private var failed = false

    var body: some View {
        let change = FoodRules.change(at: day, petId: pet.id, in: changes)
        if let change, let check = FoodRules.check(change, today: day) {
            let food = foods.first { $0.id == change.foodId }?.name ?? ""
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("사료 다 바꿨나요?").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
                    Text("\(food) · 바꾼 지 \(check.day)일째").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                // 큰 글자에선 버튼을 위아래로.
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { buttons(change, step: check.step) }
                    VStack(spacing: 8) { buttons(change, step: check.step) }
                }
                if let note = FoodRules.laterNote(step: check.step) {
                    Text(note).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button("\(FoodGuideSheet.speciesName(pet.species)) 사료 바꾸는 법 보기 ›") { guiding = true }
                    .okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                    .frame(minHeight: 44)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
            .sheet(isPresented: $guiding) { FoodGuideSheet(species: pet.species) }
        }
    }

    @ViewBuilder private func buttons(_ change: FoodChange, step: Int) -> some View {
        Button("다 바꿨어요") { run("사료를 다 바꿨다고 남겼어요") { try FoodRules.finished(change, today: day, in: context) } }
            .buttonStyle(.okchePrimary(.outlined))
        Button("아직이에요") { run(FoodRules.laterNote(step: step) ?? "알겠어요") { try FoodRules.notYet(change, step: step, in: context) } }
            .buttonStyle(.okchePrimary(.outlined))
    }

    /// 누르면 카드가 사라지니 화면 읽기에도 결과를 알린다.
    private func run(_ announce: String, _ work: () throws -> Void) {
        do {
            try work()
            failed = false
            AccessibilityNotification.Announcement(announce).post()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}
