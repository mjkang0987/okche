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
    private var missingField: PetRegistration.Field? {
        let all = form.validate()
        return ([PetRegistration.Field.name, .birth, .age, .weight] + form.insulinSlots.map { .insulinUnits($0.id) } + [.insulinTimes])
            .first { all[$0] != nil }
    }
    private var missing: String? {
        guard let field = missingField, let message = form.validate()[field] else { return nil }
        return petFieldLabel(field, slots: form.insulinSlots.map(\.id)).map { okcheNamed($0, message) } ?? message
    }
    /// 저장한 뒤 닫히는 동안 한 번 더 눌러 두 번 저장되지 않게.
    @State private var saved = false
    /// 당뇨 아이를 모시기 전 허락 안내 창(`NotifyAsk`, 9-3)과 묻는 중(두 번 눌러도 한 번만).
    @State private var asking = false
    @State private var checking = false
    @FocusState private var focused: PetRegistration.Field?
    @AppStorage("lastPetId") private var lastPetId = ""

    var body: some View {
        VStack(spacing: 0) {
            header
                ScrollViewReader { proxy in
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
                    // 남은 것 줄을 누르면 그 칸으로 가서 칸 아래 오류를 보여 줌.
                    Button("모시기", action: save).buttonStyle(.okchePrimary).okcheMissing(missing) { show(missingField, proxy) }
                }
                }
        }
        .background(OkcheColor.hanji)
        // 저장(시작하기면 탭으로 바뀜)·닫기는 창이 다 닫힌 뒤에.
        .fullScreenCover(isPresented: $asking, onDismiss: { checking = false; commit() }) {
            NotifyAskDialog(title: "인슐린 맞을 시간을 알려 드릴까요?") { asking = false }
        }
        .transaction { $0.disablesAnimations = asking }
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

    private func show(_ field: PetRegistration.Field?, _ proxy: ScrollViewProxy) {
        guard let field else { return }
        touched.insert(field)
        withAnimation { proxy.scrollTo(field, anchor: .center) }
        if field != .birth, field != .insulinTimes { focused = field }
    }

    /// 당뇨 아이면 저장 전에 허락 안내(9-3) — 시작하기에서 모시면 저장하자마자 탭 화면으로 바뀌어 뒤에 띄운 창이 사라져서.
    private func save() {
        guard !saved, !checking, missing == nil else { return }
        // 인슐린 시간 알림(설정 `notify.insulinTime`, 모든 아이 공통)을 꺼 두었으면 묻지 않는다.
        guard form.hasDiabetes, (try? Setting.loadOrCreate(in: context))?.notify.insulinTime ?? true else { return commit() }
        checking = true
        Task {
            if await MedReminders.shouldAsk() {
                MedReminders.markAsked()
                asking = true
            } else {
                checking = false
                commit()
            }
        }
    }

    private func commit() {
        guard !saved else { return }
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

/// 아래 줄용 칸 이름 — 인슐린 양은 몇 번째인지까지.
func petFieldLabel(_ field: PetRegistration.Field, slots: [UUID]) -> String? {
    switch field {
    case .name: "이름"
    case .birth: "생일"
    case .age: "나이"
    case .weight: "체중"
    case .family: "가족 된 날"
    case .insulinTimes: "인슐린 시간"
    case .insulinUnits(let id): slots.firstIndex(of: id).map { "\($0 + 1)번째 인슐린 양" }
    default: nil
    }
}
