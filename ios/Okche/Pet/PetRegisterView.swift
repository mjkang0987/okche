import SwiftData
import SwiftUI

/// 아이 등록 (디자인 보드 `Profile`·`ProfileDog`). 프로필 사진 칸은 11단계(사진 저장) 때 넣는다.
struct PetRegisterView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var form = PetRegistration()
    /// 한 번 만진 칸 — 칸 아래 오류는 만진 뒤부터. 저장 실패는 `failed`.
    @State private var touched: Set<PetRegistration.Field> = []
    @State private var failed = false
    private var errors: [PetRegistration.Field: String] {
        form.validate().filter { touched.contains($0.key) }.merging(failed ? [.save: "저장하지 못했어요. 다시 해 주세요."] : [:]) { a, _ in a }
    }
    /// 첫 번째로 남은 것(화면 위에서부터) — 있으면 '모시기'는 흐리게.
    private var missing: String? {
        let all = form.validate()
        return ([PetRegistration.Field.name, .birth, .age, .weight] + form.insulinSlots.map { .insulinUnits($0.id) } + [.insulinTimes])
            .lazy.compactMap { all[$0] }.first
    }
    /// 저장한 뒤 닫히는 동안 한 번 더 눌러 두 번 저장되지 않게.
    @State private var saved = false
    @FocusState private var focused: PetRegistration.Field?
    @AppStorage("lastPetId") private var lastPetId = ""

    var body: some View {
        VStack(spacing: 0) {
            header
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        OkcheTextField(label: "이름", text: $form.name, error: errors[.name])
                            .focused($focused, equals: .name).id(PetRegistration.Field.name)
                        PetIdentityFields(species: $form.species, breed: $form.breed, furColor: $form.furColor,
                                          sex: $form.sex, neutered: $form.neutered)
                        BirthFields(birthDate: $form.birthDate, birthUnknown: $form.birthUnknown, approxAge: $form.approxAge,
                                    birthError: errors[.birth], ageError: errors[.age], focused: $focused)
                        OkcheTextField(label: "체중", note: "(kg)", text: $form.weightKg, keyboard: .decimalPad, error: errors[.weight])
                            .focused($focused, equals: .weight).id(PetRegistration.Field.weight)
                        VStack(alignment: .leading, spacing: 6) {
                            OkcheTextField(label: "진단명", note: "(있을 때만)", text: $form.diagnosis, placeholder: "예: 염증성 장질환(IBD)")
                                .focused($focused, equals: .diagnosis)
                            Text("적어 두면 실록과 탕약 알림을 투병 관리에 맞춰 쓸 수 있어요.")
                                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                        }
                        OkcheSwitchRow(title: "당뇨가 있어요", detail: "켜면 일기와 위젯에 혈당·인슐린 칸이 생겨요.", isOn: $form.hasDiabetes)
                        if form.hasDiabetes {
                            InsulinSection(form: $form, errors: errors, focused: $focused)
                                .padding(.top, -6)
                        }
                    }
                    .padding(.horizontal, OkcheSpacing.space4)
                    .padding(.top, 8)
                    .padding(.bottom, OkcheSpacing.space4)
                }
                .scrollDismissesKeyboard(.interactively)
                .okcheFooter {
                    OkcheFieldError(message: errors[.save])
                    Button("모시기", action: save).buttonStyle(.okchePrimary).okcheMissing(missing)
                }
        }
        .background(OkcheColor.hanji)
        // 숫자 자판에는 리턴 키가 없어 닫는 버튼을 붙인다.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("완료") { focused = nil }
            }
        }
        // 만진 칸만 칸 아래 오류(열자마자 빨간 글자가 뜨지 않게).
        .onChange(of: form.name) { touched.insert(.name) }
        .onChange(of: form.weightKg) { touched.insert(.weight) }
        .onChange(of: form.approxAge) { touched.insert(.age) }
        .onChange(of: form.birthDate) { touched.insert(.birth) }
        .onChange(of: form.insulinSlots) { old, new in
            if old.map(\.time) != new.map(\.time) || old.count != new.count { touched.insert(.insulinTimes) }
            for slot in new where old.first(where: { $0.id == slot.id })?.units != slot.units {
                touched.insert(.insulinUnits(slot.id))
            }
        }
    }

    private var header: some View {
        FormHeader(title: "새 아기씨 모시기", note: "아이 등록", icon: "xmark", iconLabel: "닫기") { dismiss() }
    }

    private func save() {
        guard !saved, missing == nil else { return }
        do {
            // 새로 모신 아이는 바로 지금 보는 아이(6-3).
            lastPetId = try form.save(in: context).id.uuidString
            failed = false
            saved = true
            dismiss()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 인슐린 칸 (보드 `Profile`의 인슐린 상자).
private struct InsulinSection: View {
    @Binding var form: PetRegistration
    let errors: [PetRegistration.Field: String]
    var focused: FocusState<PetRegistration.Field?>.Binding

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("인슐린").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
            OkcheTextField(label: "인슐린 이름", note: "(몰라도 돼요)", text: $form.insulinName, placeholder: "예: 병원에서 받은 인슐린 이름")
                .focused(focused, equals: .insulinName)
            VStack(alignment: .leading, spacing: 0) {
                OkcheFieldLabel(title: "맞는 시간과 양").padding(.bottom, 8).accessibilityAddTraits(.isHeader)
                InsulinSlotsEditor(slots: $form.insulinSlots, errors: errors, focused: focused)
            }
            VStack(alignment: .leading, spacing: 8) {
                OkcheFieldLabel(title: "주사기 종류").accessibilityAddTraits(.isHeader)
                OkcheChoiceChips(options: [(Syringe.u40, "U-40"), (.u100, "U-100"), (.unknown, "잘 모르겠어요")],
                                 selection: Binding { form.syringe } set: { form.syringe = $0 ?? .unknown }, allowsNone: false)
                note("인슐린과 맞는 주사기를 쓰길 권해요. 모르면 병원에 물어보길 권해요.")
            }
            note("병원에서 정해 준 양을 넣어 주세요. 나중에 아이 정보에서 바꿀 수 있어요.")
            SourceNote(text: diabetesSourceNote).padding(.bottom, 8)
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 6)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
    }

    private func note(_ text: String) -> some View {
        Text(text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
    }
}
