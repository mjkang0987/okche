import SwiftData
import SwiftUI

/// 아이 정보 고치기 (디자인 보드 `PetEdit`). 들어가는 길은 지금 더보기의 임시 줄.
/// 사진 칸은 11단계, 호칭('마마')은 6-3, 사료·체중·추억·지우기 줄은 그 화면을 만드는 단계에서 눌리게 한다.
struct PetEditView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var form: PetEditing
    @State private var errors: [PetRegistration.Field: String] = [:]
    @State private var saved = false
    @FocusState private var focused: PetRegistration.Field?
    @Query private var weights: [Record]

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
            FormHeader(title: pet.name, note: "아이 정보", icon: "chevron.left", iconLabel: "뒤로") { dismiss() }
            ScrollViewReader { scroller in
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
                            if form.hasDiabetes {
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
                        OkcheSwitchRow(title: "휴양 중 · 관리 쉬기",
                                       detail: "알림을 멈추고, 이 기간은 약·끼니 챙긴 횟수에서 빼요. 휴양 중에도 기록할 수 있어요.",
                                       isOn: $form.resting)
                        VStack(alignment: .leading, spacing: 8) {
                            OkcheFieldError(message: errors[.save])
                            Button("저장하기") { save(scroller) }
                                .buttonStyle(.okchePrimary)
                                .disabled(saved)
                        }
                        laterLinks
                    }
                    .padding(.horizontal, OkcheSpacing.space4)
                    .padding(.top, 4)
                    .padding(.bottom, OkcheSpacing.space6)
                }
                .scrollDismissesKeyboard(.interactively)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar) // 탭 아래 화면은 하단 탭 없음(디자인 화면 짜임새)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("완료") { focused = nil }
            }
        }
        .onChange(of: form.name) { errors[.name] = nil }
        .onChange(of: form.approxAge) { errors[.age] = nil }
        .onChange(of: form.birthDate) { errors[.birth] = nil }
        .onChange(of: form.familyDate) { errors[.family] = nil }
        .onChange(of: form.hasDiabetes) { errors[.insulinPlan] = nil }
        // 인슐린 설정을 다녀와 계획이 생겼으면 '먼저 설정해 주세요' 오류를 지운다.
        .onAppear {
            if errors[.insulinPlan] != nil, !((try? InsulinPlan.open(for: pet.id, in: context)) ?? []).isEmpty {
                errors[.insulinPlan] = nil
            }
        }
    }

    /// 이름 + '고양이 · 코리안숏헤어 · 동행 1,248일' (저장된 값).
    private var summary: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(pet.name).okcheText(OkcheType.title).foregroundStyle(OkcheColor.ink)
            Text(([pet.species == .cat ? "고양이" : "강아지", pet.breed]
                  + [PetEditing.companionDays(pet.familyDate).map { "동행 \($0.formatted())일" }].compactMap { $0 })
                .joined(separator: " · "))
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .accessibilityElement(children: .combine)
    }

    /// 지금 먹는 사료·옥체(체중). 보기만(사료는 8단계, 체중 화면은 그 단계에서 눌리게).
    private var laterRows: some View {
        VStack(spacing: 0) {
            laterRow(title: "지금 먹는 사료", value: "아직 없어요")
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
        let kg = (Double(grams) / 1000).formatted(.number.precision(.fractionLength(0...2)))
        return "\(kg)kg · \(record.occurredAt.formatted(.dateTime.month().day()))"
    }

    /// 추억으로 간직하기 | 이 아이 기록 지우기 — 6-4에서 눌리게.
    private var laterLinks: some View {
        HStack(spacing: 4) {
            Button("추억으로 간직하기") {}
                .foregroundStyle(OkcheColor.inkMuted)
            Text("|").foregroundStyle(OkcheColor.line).accessibilityHidden(true)
            Button("이 아이 기록 지우기") {}
                .foregroundStyle(OkcheColor.yeonji)
        }
        .okcheText(OkcheType.label, weight: .regular)
        .frame(maxWidth: .infinity, minHeight: 44)
        .disabled(true)
    }

    private func save(_ scroller: ScrollViewProxy) {
        guard !saved else { return }
        do {
            try form.save(in: context)
            errors = [:]
            saved = true
            dismiss()
        } catch let invalid as PetRegistration.Invalid {
            errors = invalid.errors
            let order: [PetRegistration.Field] = [.name, .birth, .age, .family, .insulinPlan]
            if let first = order.first(where: { errors[$0] != nil }) {
                withAnimation { scroller.scrollTo(first, anchor: .center) }
                if first == .name || first == .age { focused = first }
                let message = errors.count == 1 ? errors[first]! : "고칠 곳 \(errors.count)개. \(errors[first]!)"
                AccessibilityNotification.Announcement(message).post()
            }
        } catch {
            errors = [.save: "저장하지 못했어요. 다시 해 주세요."]
            AccessibilityNotification.Announcement(errors[.save]!).post()
        }
    }
}

/// 더보기의 임시 줄에서 여는 아이 정보(저장소가 있어야 입력값을 만들 수 있어서 한 번 감쌈).
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
