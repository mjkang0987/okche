import SwiftData
import SwiftUI

/// 아이 정보 고치기 (디자인 보드 `PetEdit`). 들어가는 길은 더보기 아이 관리·아이 바꾸기 시트.
/// 사진 칸은 11단계, 사료·체중 줄은 그 화면을 만드는 단계에서 눌리게 한다. 추억·지우기는 6-4(추억 아이는 보드 `PetEditMemorial`).
struct PetEditView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(\.openTab) private var openTab
    @AppStorage("lastPetId") private var lastPetId = ""
    /// 마지막 아이를 지워도 시작하기 화면으로 돌아가지 않고 더보기로(6-4 — 이미 앱을 쓴 사람).
    @AppStorage("onboardingDone") private var onboardingDone = false
    /// 띄운 확인 창(간직·지우기·되돌리기).
    @State private var dialog: LifecycleDialog?
    @State private var lifecycleFailed = false

    enum LifecycleDialog { case keep, delete, restore }
    @State private var form: PetEditing
    /// 열린 인슐린 계획 — 설정 화면을 다녀오면 바로 다시 셈(`@Query`라 바뀌면 다시 그림).
    @Query(filter: #Predicate<InsulinPlan> { $0.deletedAt == nil && $0.activeUntil == nil }) private var plans: [InsulinPlan]
    /// 한 번 만진 칸 — 칸 아래 오류는 만진 뒤부터. 저장 실패는 `failed`.
    @State private var touched: Set<PetRegistration.Field> = []
    @State private var failed = false
    /// 지금 고칠 것 전부 — 당뇨를 켰는데 인슐린 계획이 없으면 '먼저 설정해 주세요'(저장 규칙과 같음, 추억 아이 빼고).
    private var allErrors: [PetRegistration.Field: String] {
        var all = form.validate()
        if form.hasDiabetes, pet.memorializedAt == nil, !plans.contains(where: { $0.petId == pet.id }) {
            all[.insulinPlan] = "인슐린 시간과 양을 먼저 설정해 주세요"
        }
        return all
    }
    private var errors: [PetRegistration.Field: String] {
        allErrors.filter { touched.contains($0.key) }.merging(failed ? [.save: "저장하지 못했어요. 다시 해 주세요."] : [:]) { a, _ in a }
    }
    /// 첫 번째로 남은 것(화면 위에서부터) — 있으면 저장 버튼은 흐리게.
    private var missing: String? {
        let all = allErrors
        return [PetRegistration.Field.name, .birth, .age, .family, .insulinPlan].lazy.compactMap { all[$0] }.first
    }
    @State private var saved = false
    @FocusState private var focused: PetRegistration.Field?
    @Query private var weights: [Record]
    @Query(filter: #Predicate<FoodChange> { $0.deletedAt == nil }) private var foodChanges: [FoodChange]
    @Query(filter: #Predicate<Food> { $0.deletedAt == nil }) private var foods: [Food]
    @State private var changingFood = false

    init(form: PetEditing) {
        _form = State(initialValue: form)
        let petId: UUID? = form.pet.id
        let kind = RecordKind.weight.rawValue
        var latest = FetchDescriptor<Record>(predicate: #Predicate { $0.petId == petId && $0.kind == kind && $0.deletedAt == nil },
                                             sortBy: [SortDescriptor(\.occurredAt, order: .reverse)])
        latest.fetchLimit = 1
        _weights = Query(latest)
    }

    private var pet: Pet { form.pet }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(title: PetTitle.name(pet), note: "아이 정보", icon: "chevron.left", iconLabel: "뒤로") { dismiss() }
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        summary
                        OkcheTextField(label: "이름", text: $form.name, error: errors[.name])
                            .focused($focused, equals: .name).id(PetRegistration.Field.name)
                        PetIdentityFields(species: $form.species, breed: $form.breed, furColor: $form.furColor,
                                          sex: $form.sex, neutered: $form.neutered)
                        BirthFields(birthDate: $form.birthDate, birthUnknown: $form.birthUnknown, approxAge: $form.approxAge,
                                    birthError: errors[.birth], ageError: errors[.age], focused: $focused)
                        DateField(label: "가족 된 날", date: $form.familyDate, error: errors[.family])
                            .id(PetRegistration.Field.family)
                        OkcheTextField(label: "진단명", note: "(있을 때만)", text: $form.diagnosis)
                            .focused($focused, equals: .diagnosis)
                        VStack(alignment: .leading, spacing: 4) {
                            OkcheSwitchRow(title: "당뇨가 있어요", detail: "켜면 일기와 위젯에 혈당·인슐린 칸이 생겨요.", isOn: $form.hasDiabetes)
                            // 추억 아이는 인슐린을 새로 설정하지 않는다(간직하면 계획을 닫아 둠 — 되돌릴 때 6-4 규칙대로).
                            if form.hasDiabetes && pet.memorializedAt == nil {
                                NavigationLink {
                                    InsulinSetupScreen(pet: pet)
                                } label: {
                                    Text("인슐린 이름·양·시간 설정 ›")
                                        .accessibilityLabel("인슐린 이름·양·시간 설정") // 화면 읽기는 '›' 없이
                                        .okcheText(OkcheType.body, weight: .medium)
                                        .foregroundStyle(OkcheColor.namsaek)
                                        .padding(.horizontal, 16)
                                        .frame(minHeight: 44)
                                        .contentShape(Rectangle())
                                }
                                OkcheFieldError(message: errors[.insulinPlan]).padding(.horizontal, 16)
                            }
                        }
                        .id(PetRegistration.Field.insulinPlan)
                        laterRows
                        // 추억으로 간직한 아이는 휴양 스위치 없음(6-4).
                        if pet.memorializedAt == nil {
                            OkcheSwitchRow(title: "휴양 중 · 관리 쉬기",
                                           detail: "알림을 멈추고, 이 기간은 약·끼니 챙긴 횟수에서 빼요. 휴양 중에도 기록할 수 있어요.",
                                           isOn: $form.resting)
                        }
                        lifecycleLinks
                    }
                    .padding(.horizontal, OkcheSpacing.space4)
                    .padding(.top, 4)
                    .padding(.bottom, OkcheSpacing.space4)
                }
                .scrollDismissesKeyboard(.interactively)
                .okcheFooter {
                    OkcheFieldError(message: errors[.save])
                    Button("저장하기", action: save).buttonStyle(.okchePrimary).okcheMissing(missing)
                }
        }
        .background(OkcheColor.hanji)
        .overlay { dialogView }
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar) // 탭 아래 화면은 하단 탭 없음(디자인 화면 짜임새)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("완료") { focused = nil }
            }
        }
        // 만진 칸만 칸 아래 오류(열자마자 빨간 글자가 뜨지 않게). 인슐린 계획은 설정을 다녀오면 저절로 다시 셈.
        .onChange(of: form.name) { touched.insert(.name) }
        .onChange(of: form.approxAge) { touched.insert(.age) }
        .onChange(of: form.birthDate) { touched.insert(.birth) }
        .onChange(of: form.familyDate) { touched.insert(.family) }
        .onChange(of: form.hasDiabetes) { touched.insert(.insulinPlan) }
    }

    /// 이름 + '고양이 · 코리안숏헤어 · 동행 1,248일' (저장된 값).
    private var summary: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(PetTitle.name(pet)).okcheText(OkcheType.title).foregroundStyle(OkcheColor.ink)
            Text(([pet.species == .cat ? "고양이" : "강아지", pet.breed]
                  + [pet.memorializedAt != nil ? "추억으로 간직 중"
                     : PetEditing.companionDays(pet.familyDate).map { "동행 \($0.formatted())일" }].compactMap { $0 })
                .joined(separator: " · "))
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .accessibilityElement(children: .combine)
    }

    /// 지금 먹는 사료(누르면 사료 바꾸기 — 7-4a, 추억 아이는 보기만)·옥체(체중, 그 단계에서 눌리게).
    private var laterRows: some View {
        VStack(spacing: 0) {
            let food = FoodRules.change(at: OkcheDay.of(.now), petId: pet.id, in: foodChanges)
                .map { change in FoodRules.line(change, food: foods.first { $0.id == change.foodId }) } ?? "아직 없어요"
            if pet.memorializedAt == nil {
                Button { changingFood = true } label: {
                    HStack {
                        laterRow(title: "지금 먹는 사료", value: food)
                        Text("›").okcheText(OkcheType.body).foregroundStyle(OkcheColor.lineStrong).accessibilityHidden(true)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint("사료 바꾸기")
                .sheet(isPresented: $changingFood) { FoodChangeSheet(pet: pet) }
            } else {
                laterRow(title: "지금 먹는 사료", value: food)
            }
            Rectangle().fill(OkcheColor.line).frame(height: 1)
            laterRow(title: "옥체 · 체중", value: latestWeight ?? "아직 없어요")
        }
        .padding(.horizontal, 14)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
    }

    private func laterRow(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
            Text(value).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
    }

    /// '4.62kg · 10월 25일'
    private var latestWeight: String? {
        guard let record = weights.first,
              let json = try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any],
              let grams = json["grams"] as? Int else { return nil }
        return "\(QuickRecord.kgText(grams: grams)) · \(record.occurredAt.formatted(.dateTime.month().day()))"
    }

    /// '추억으로 간직하기 | 이 아이 기록 지우기', 추억 아이는 '추억에서 되돌리기 | 이 아이 기록 지우기'(6-4). 누르면 확인 창.
    private var lifecycleLinks: some View {
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                if pet.memorializedAt == nil {
                    Button("추억으로 간직하기") { dialog = .keep }
                        .foregroundStyle(OkcheColor.inkMuted)
                } else {
                    Button("추억에서 되돌리기") { dialog = .restore }
                        .foregroundStyle(OkcheColor.namsaek)
                }
                Text("|").foregroundStyle(OkcheColor.line).accessibilityHidden(true)
                Button("이 아이 기록 지우기") { dialog = .delete }
                    .foregroundStyle(OkcheColor.yeonji)
            }
            .okcheText(OkcheType.label, weight: .regular)
            .frame(maxWidth: .infinity, minHeight: 44)
            OkcheFieldError(message: lifecycleFailed ? "저장하지 못했어요. 다시 해 주세요." : nil)
        }
    }

    @ViewBuilder private var dialogView: some View {
        switch dialog {
        case .keep:
            OkcheConfirmDialog(title: "\(pet.name)의 기록을 추억으로 간직할게요",
                               bullets: ["약·기록·사진 알림은 모두 멈춰요", "기록과 사진은 그대로 남아요",
                                         "생일 같은 축하 화면은 이제 뜨지 않아요", "아이 정보에서 언제든 되돌릴 수 있어요"],
                               confirm: "간직하기", onCancel: { dialog = nil }) { run(PetLifecycle.memorialize, then: .main) }
        case .delete:
            OkcheConfirmDialog(title: "\(pet.name)의 기록을 지울까요?", destructive: true,
                               message: "기록·사진·실록이 모두 사라지고, 되돌릴 수 없어요.", confirm: "지우기", confirmLook: .danger,
                               onCancel: { dialog = nil }, onConfirm: { run(PetLifecycle.delete, then: .more) }) {
                // 추억 아이에게는 간직 권유를 보이지 않는다(이미 간직 중).
                if pet.memorializedAt == nil {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("지우는 대신 **추억으로 간직**할 수도 있어요. 기록과 사진은 그대로 두고, 알림만 모두 멈춰요.")
                            .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.ink)
                        Button("추억으로 간직하기") { dialog = .keep }.buttonStyle(.okchePrimary(.outlined))
                    }
                    .padding(14)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                }
            }
        case .restore:
            OkcheConfirmDialog(title: "\(PetTitle.withObjectParticle(pet.name)) 다시 모실까요?",
                               bullets: ["간직할 때 멈춘 약·인슐린·끼니 알림이 다시 시작돼요", "생일 같은 축하 화면이 다시 떠요",
                                         "휴양 중이었다면 다시 켜 주세요"],
                               confirm: "다시 모시기", onCancel: { dialog = nil }) { run(PetLifecycle.restore, then: nil) }
        case nil:
            EmptyView()
        }
    }

    /// 간직(→ 문안의 추억 화면, 지금 보는 아이로)·지우기(→ 더보기)·되돌리기(이 화면 그대로).
    private func run(_ action: (Pet, Date, ModelContext) throws -> Void, then tab: AppTab?) {
        do {
            try action(pet, .now, context)
            lifecycleFailed = false
            dialog = nil
            guard let tab else { return }
            if tab == .main { lastPetId = pet.id.uuidString }
            if tab == .more { onboardingDone = true }
            openTab(tab)
            dismiss()
        } catch {
            dialog = nil
            lifecycleFailed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    private func save() {
        guard !saved, missing == nil else { return }
        do {
            try form.save(in: context)
            failed = false
            saved = true
            dismiss()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 더보기 아이 관리·아이 바꾸기 시트에서 여는 아이 정보(저장소가 있어야 입력값을 만들 수 있어서 한 번 감쌈).
struct PetEditScreen: View {
    let pet: Pet
    @Environment(\.modelContext) private var context

    var body: some View {
        PetEditView(form: PetEditing(pet: pet, context: context))
    }
}

/// 아이 정보의 인슐린 설정 줄에서 여는 화면. 감싸 두어 아이 정보를 다시 그릴 때마다 저장소를 읽지 않게.
struct InsulinSetupScreen: View {
    let pet: Pet
    @Environment(\.modelContext) private var context

    var body: some View {
        InsulinSetupView(form: InsulinSetup(pet: pet, context: context))
    }
}
