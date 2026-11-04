import SwiftData
import SwiftUI

/// 약방(보드 `Yakbang`·`YakbangEmpty`): 더보기 '약방 · 약 목록'. '약'·'보조제' 묶음(없는 묶음은 숨김), 줄을 누르면 고치기, '+ 약 들이기'.
struct YakbangView: View {
    @Environment(\.dismiss) private var dismiss
    @Query private var meds: [Medication]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }) private var records: [Record]
    @State private var sheet: MedFormSheet.Mode?

    var body: some View {
        let groups = MedRules.grouped(meds)
        VStack(spacing: 0) {
            FormHeader(title: "약방", note: "약 목록", icon: "chevron.left", iconLabel: "뒤로", trailing: AnyView(PetSeal())) { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("병원에서 받은 약과 보조제를 미리 들여 두면, 탕약 알림을 만들거나 기록할 때 골라 쓰기만 하면 돼요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    if groups.isEmpty {
                        Text("아직 들인 약이 없어요.").okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                    }
                    ForEach(groups, id: \.category) { group in
                        VStack(alignment: .leading, spacing: 0) {
                            Text(group.title).okcheText(OkcheType.label).foregroundStyle(OkcheColor.inkMuted)
                                .accessibilityAddTraits(.isHeader)
                                .padding(.top, 12)
                            ForEach(Array(group.meds.enumerated()), id: \.element.id) { index, med in
                                SnackRow(badge: MedRules.formTitle(med.form), name: med.name, detail: MedRules.detail(med, records: records)) {
                                    sheet = .edit(med)
                                }
                                .overlay(alignment: .top) { if index > 0 { Rectangle().fill(OkcheColor.line).frame(height: 1) } }
                            }
                        }
                        .padding(.horizontal, 16).padding(.bottom, 4)
                        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                        .accessibilityElement(children: .contain)
                    }
                    Button("+ 약 들이기") { sheet = .add }.buttonStyle(.okchePrimary)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, 4).padding(.bottom, OkcheSpacing.space6)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .sheet(item: $sheet) { MedFormSheet(mode: $0) }
    }
}

/// 약 들이기·고치기 시트(보드 `MedReg`·`MedEdit`): 약 이름, 짧은 이름, 종류, 모양, 한 번에·지금 남은 양(단위는 모양을 따름), 메모.
/// 고치기엔 '약방에서 빼기' → 먹고 있는 약이면 안내 창(`MedRemoveBlocked`), 아니면 확인 창(`MedRemove`). 약 봉투 사진은 11단계.
struct MedFormSheet: View {
    enum Mode: Identifiable {
        case add
        case edit(Medication)

        var id: String {
            switch self {
            case .add: "add"
            case .edit(let med): med.id.uuidString
            }
        }
    }

    enum Dialog { case remove, blocked }

    let mode: Mode
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<MedSchedule> { $0.deletedAt == nil }) private var schedules: [MedSchedule]
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    @State private var form: MedRules.Form
    private let original: MedRules.Form
    @State private var errors: [MedRules.Field: String] = [:]
    @State private var failed = false
    @State private var saving = false
    @State private var dialog: Dialog?

    init(mode: Mode) {
        self.mode = mode
        let start: MedRules.Form
        if case .edit(let med) = mode {
            // 남은 양 칸은 계산한 지금 남은 양 — 기록은 시트를 열 때 한 번 읽는다.
            let all = (try? med.modelContext?.fetch(FetchDescriptor<Record>(predicate: #Predicate { $0.deletedAt == nil }))) ?? []
            start = MedRules.Form(med, records: all)
        } else {
            start = MedRules.Form()
        }
        original = start
        _form = State(initialValue: start)
    }

    private var editing: Medication? { if case .edit(let med) = mode { med } else { nil } }

    var body: some View {
        let unit = MedRules.unitTitle(MedRules.unit(form.form))
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text(editing == nil ? "약방에 약 들이기" : "약 고치기").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                            .accessibilityAddTraits(.isHeader)
                        Spacer(minLength: 0)
                        Button { dismiss() } label: {
                            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                        }
                        .accessibilityLabel("닫기")
                    }
                    OkcheTextField(label: "약 이름", text: field(\.name, .name), placeholder: "예: 병원에서 받은 약 이름", error: errors[.name])
                    OkcheTextField(label: "버튼에 보일 짧은 이름", note: "(선택)", text: field(\.shortName, .shortName),
                                   placeholder: "예: 프레드니솔론 → 프레드 · 비우면 원래 이름", error: errors[.shortName])
                        .onChange(of: form.shortName) { if form.shortName.count > 6 { form.shortName = String(form.shortName.prefix(6)) } }
                    FieldGroup(title: "종류") {
                        OkcheChoiceChips(options: MedRules.categories, selection: Binding(get: { form.category }, set: { form.category = $0 ?? form.category }),
                                         allowsNone: false)
                    }
                    FieldGroup(title: "모양") {
                        OkcheChoiceChips(options: MedRules.forms, selection: Binding(get: { form.form }, set: { form.form = $0 ?? form.form }),
                                         allowsNone: false)
                    }
                    // 큰 글자에선 두 칸을 위아래로.
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .top, spacing: 8) { amountFields(unit) }
                        VStack(alignment: .leading, spacing: 12) { amountFields(unit) }
                    }
                    OkcheTextField(label: "메모", note: "(선택)", text: $form.memo, placeholder: "예: 공복에 주면 토해요")
                    Text(editing == nil ? "들인 약은 탕약 알림을 만들 때 골라 쓸 수 있어요."
                                        : "남은 양을 고치면 지금부터 다시 세요. 약방에서 빼도 지난 기록의 약 이름은 그대로 남아요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, OkcheSpacing.space6)
                .padding(.bottom, OkcheSpacing.space4)
            }
            VStack(spacing: 4) {
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button(editing == nil ? "약방에 들이기" : "저장하기", action: save).buttonStyle(.okchePrimary)
                if let editing {
                    Button {
                        dialog = MedRules.takers(editing, schedules: schedules, pets: pets).isEmpty ? .remove : .blocked
                    } label: {
                        Text("약방에서 빼기").okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.yeonji)
                            .frame(maxWidth: .infinity, minHeight: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, 10).padding(.bottom, OkcheSpacing.space4)
            .overlay(alignment: .top) { Rectangle().fill(OkcheColor.line).frame(height: 1) }
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .fullScreenCover(isPresented: Binding(get: { dialog != nil }, set: { if !$0 { dialog = nil } })) {
            dialogView.presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = dialog != nil }
    }

    @ViewBuilder private func amountFields(_ unit: String) -> some View {
        OkcheTextField(label: "한 번에", text: field(\.dose, .dose), unit: unit, keyboard: .decimalPad, error: errors[.dose])
        OkcheTextField(label: "지금 남은 양", note: "(선택)", text: field(\.stock, .stock), placeholder: "예: 120", unit: unit, keyboard: .decimalPad, error: errors[.stock])
    }

    /// 칸을 고치면 그 칸 오류를 지운다.
    private func field(_ key: WritableKeyPath<MedRules.Form, String>, _ error: MedRules.Field) -> Binding<String> {
        Binding(get: { form[keyPath: key] }, set: { form[keyPath: key] = $0; errors[error] = nil })
    }

    @ViewBuilder private var dialogView: some View {
        if let editing {
            switch dialog {
            case .blocked:
                OkcheConfirmDialog(title: "지금은 뺄 수 없어요",
                                   message: MedRules.blockedMessage(editing, takers: MedRules.takers(editing, schedules: schedules, pets: pets)),
                                   confirm: "알겠어요", showsCancel: false, onCancel: { dialog = nil }, onConfirm: { dialog = nil })
            default:
                OkcheConfirmDialog(title: "\(PetTitle.withObjectParticle(editing.name)) 약방에서 뺄까요?",
                                   message: "탕약에서 더는 고를 수 없어요. 지난 기록의 약 이름은 그대로 남아요.",
                                   confirm: "빼기", confirmLook: .danger, onCancel: { dialog = nil }, onConfirm: remove)
            }
        }
    }

    private func save() {
        guard !saving else { return }
        errors = MedRules.errors(form)
        guard errors.isEmpty else {
            AccessibilityNotification.Announcement(errors.values.sorted().first ?? "").post()
            return
        }
        saving = true
        do {
            if let editing {
                try MedRules.update(editing, from: original, to: form, in: context)
            } else {
                try MedRules.add(form, in: context)
            }
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    private func remove() {
        guard let editing else { return }
        do {
            try MedRules.remove(editing, in: context)
            dialog = nil
            dismiss()
        } catch {
            dialog = nil
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}
