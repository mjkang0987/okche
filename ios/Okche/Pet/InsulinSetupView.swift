import SwiftData
import SwiftUI

/// 인슐린 설정 (디자인 보드 `InsulinSetup`). 아이 정보의 '인슐린 이름·양·시간 설정'에서 들어온다. '저장하기'는 바로 저장.
struct InsulinSetupView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var form: InsulinSetup
    @State private var errors: [PetRegistration.Field: String] = [:]
    @State private var saved = false
    @FocusState private var focused: PetRegistration.Field?

    init(form: InsulinSetup) {
        _form = State(initialValue: form)
    }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(title: "인슐린", note: "설정", icon: "chevron.left", iconLabel: "뒤로") { dismiss() }
            ScrollViewReader { scroller in
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        OkcheTextField(label: "인슐린 이름", text: $form.name, placeholder: "예: 병원에서 받은 인슐린 이름")
                            .focused($focused, equals: .insulinName)
                        FieldGroup(title: "주사기 종류") {
                            OkcheChoiceChips(options: [(Syringe.u40, "U-40"), (.u100, "U-100"), (.unknown, "잘 모르겠어요")],
                                             selection: Binding { form.syringe } set: { form.syringe = $0 ?? .unknown },
                                             allowsNone: false)
                            note("인슐린과 맞는 주사기를 쓰길 권해요. 모르면 병원에 물어보길 권해요.")
                        }
                        FieldGroup(title: "맞는 시간과 양") {
                            InsulinSlotsEditor(slots: $form.slots, errors: errors, focused: $focused)
                                .padding(.horizontal, 14)
                                .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                            note("병원에서 정해 준 양을 넣어 주세요. 일기의 '맞음' 버튼이 그 시간의 양으로 바로 기록해요. 양을 바꿀 땐 수의사와 먼저 상의하길 권해요.")
                        }
                        OkcheSwitchRow(title: "인슐린 시간 알림", detail: "끄면 모든 아이의 인슐린 알림이 멈춰요", isOn: $form.notify)
                        OkcheSwitchRow(title: "밤에도 울리기", detail: "밤에 알림 쉬기 중에도 이 인슐린 알림은 울려요",
                                       isOn: $form.ringDuringQuiet)
                        leadPicker
                        VStack(alignment: .leading, spacing: 8) {
                            OkcheFieldError(message: errors[.save])
                            Button("저장하기") { save(scroller) }
                                .buttonStyle(.okchePrimary)
                                .disabled(saved)
                        }
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
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("완료") { focused = nil }
            }
        }
        .onChange(of: form.slots) { old, new in
            errors[.insulinTimes] = nil
            for slot in new where old.first(where: { $0.id == slot.id })?.units != slot.units {
                errors[.insulinUnits(slot.id)] = nil
            }
        }
    }

    /// 알림 보낼 때: 이름표 왼쪽, 고르기 칸 오른쪽.
    private var leadPicker: some View {
        let title = InsulinSetup.leadChoices.first { $0.minutes == form.leadMinutes }?.title ?? ""
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) { leadLabel; Spacer(minLength: 0); leadMenu(title).frame(width: 150) }
            VStack(alignment: .leading, spacing: 6) { leadLabel; leadMenu(title) }
        }
        .padding(.leading, 16)
    }

    private var leadLabel: some View {
        Text("알림 보낼 때").okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).accessibilityHidden(true)
    }

    private func leadMenu(_ title: String) -> some View {
        Menu {
            Picker("알림 보낼 때", selection: $form.leadMinutes) {
                ForEach(InsulinSetup.leadChoices, id: \.minutes) { Text($0.title).tag($0.minutes) }
            }
        } label: {
            HStack {
                Text(title).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                Spacer(minLength: 8)
                Image(systemName: "chevron.down").font(.body.weight(.medium)).foregroundStyle(OkcheColor.inkMuted)
            }
            .okcheFieldBox()
        }
        .accessibilityLabel("알림 보낼 때")
        .accessibilityValue(title)
    }

    private func note(_ text: String) -> some View {
        Text(text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
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
            let order: [PetRegistration.Field] = form.slots.map { .insulinUnits($0.id) } + [.insulinTimes]
            if let first = order.first(where: { errors[$0] != nil }) {
                withAnimation { scroller.scrollTo(first, anchor: .center) }
                if first != .insulinTimes { focused = first }
                let message = errors.count == 1 ? errors[first]! : "고칠 곳 \(errors.count)개. \(errors[first]!)"
                AccessibilityNotification.Announcement(message).post()
            }
        } catch {
            errors = [.save: "저장하지 못했어요. 다시 해 주세요."]
            AccessibilityNotification.Announcement(errors[.save]!).post()
        }
    }
}
