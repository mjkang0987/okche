import SwiftData
import SwiftUI

/// 다과 목록(보드 `SnackList`): 더보기 '다과 · 간식 목록'. 들인 다과(모든 아이 공통, 들인 순), 줄을 누르면 고치기, '+ 다과 들이기'.
struct SnackListView: View {
    @Environment(\.dismiss) private var dismiss
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @Query private var snacks: [Snack]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }) private var records: [Record]
    @State private var form: SnackFormSheet.Mode?

    var body: some View {
        let petId = PetTitle.current(pets, lastPetId: lastPetId)?.id
        let listed = SnackRules.listed(snacks)
        VStack(spacing: 0) {
            FormHeader(title: "다과", note: "간식 목록", icon: "chevron.left", iconLabel: "뒤로", trailing: AnyView(PetSeal())) { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("자주 주는 간식을 들여 두면 다과를 남길 때 한 번에 골라요. 수라 바꾸기 기간에 무엇을 먹었는지 실록에서 함께 볼 수 있어요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    if !listed.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(Array(listed.enumerated()), id: \.element.id) { index, snack in
                                SnackRow(name: snack.name, detail: SnackRules.listDetail(snack, petId: petId, records: records)) {
                                    form = .edit(snack)
                                }
                                .overlay(alignment: .top) { if index > 0 { Rectangle().fill(OkcheColor.line).frame(height: 1) } }
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 4)
                        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel("들인 다과")
                    }
                    Button("+ 다과 들이기") { form = .add }.buttonStyle(.okchePrimary)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, 4).padding(.bottom, OkcheSpacing.space6)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .sheet(item: $form) { SnackFormSheet(mode: $0) }
    }
}

/// 다과 줄(목록): 48 '간식' 칸 + 이름(굵게) + 아래 줄 + ›, 72 높이. 약방 약 줄도 같이 씀(칸 글자 '알약'·'물약' — 8-1).
struct SnackRow: View {
    var badge = "간식"
    let name: String
    let detail: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(badge).okcheText(OkcheType.caption).foregroundStyle(OkcheColor.namsaekDeep)
                    .lineLimit(1).minimumScaleFactor(0.5).padding(.horizontal, 4) // 칸은 48 그대로
                    .frame(width: 48, height: 48)
                    .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusSm))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                    Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.body).foregroundStyle(OkcheColor.lineStrong).accessibilityHidden(true)
            }
            .multilineTextAlignment(.leading)
            .padding(.vertical, 12)
            .frame(minHeight: 72)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("고치기")
    }
}

/// 다과 들이기·고치기 시트(보드 `SnackReg`·`SnackRegSheet`·`SnackEdit`): 간식 이름, 종류, 수라 바꾸기 중에도 괜찮은 간식.
/// 고치기엔 '목록에서 빼기' → 확인 창(`SnackRemove`). 성분표 사진은 11단계.
struct SnackFormSheet: View {
    enum Mode: Identifiable {
        /// 다과 목록에서 들이기.
        case add
        /// 다과 기록 중 들이기 — 들이면 돌아가 그 다과가 골라짐.
        case addAndPick
        case edit(Snack)

        var id: String {
            switch self {
            case .add: "add"
            case .addAndPick: "addAndPick"
            case .edit(let snack): snack.id.uuidString
            }
        }
    }

    let mode: Mode
    var onAdded: (Snack) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var name = ""
    @State private var kind = SnackKind.lickable
    @State private var ok = false
    @State private var nameError: String?
    @State private var failed = false
    @State private var saving = false
    @State private var removing = false

    init(mode: Mode, onAdded: @escaping (Snack) -> Void = { _ in }) {
        self.mode = mode
        self.onAdded = onAdded
        if case .edit(let snack) = mode {
            _name = State(initialValue: snack.name)
            _kind = State(initialValue: snack.kind)
            _ok = State(initialValue: snack.okDuringFoodChange)
        }
    }

    private var editing: Snack? { if case .edit(let snack) = mode { snack } else { nil } }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text(title).okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
                        Spacer(minLength: 0)
                        Button { dismiss() } label: {
                            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                        }
                        .accessibilityLabel("닫기")
                    }
                    OkcheTextField(label: "간식 이름", text: $name, placeholder: "예: 닭가슴살 동결건조", error: nameError)
                        .onChange(of: name) { if !name.trimmingCharacters(in: .whitespaces).isEmpty { nameError = nil } }
                    FieldGroup(title: "종류") {
                        OkcheChoiceChips(options: SnackRules.kinds, selection: Binding(get: { kind }, set: { kind = $0 ?? kind }), allowsNone: false)
                    }
                    OkcheSwitchRow(title: "수라 바꾸기 중에도 괜찮은 간식", detail: "병원에서 괜찮다고 한 간식만 켜 두길 권해요.", isOn: $ok)
                    Text(note).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, OkcheSpacing.space6)
                .padding(.bottom, OkcheSpacing.space4)
            }
            VStack(spacing: 8) {
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button(buttonTitle, action: save).buttonStyle(.okchePrimary)
                if editing != nil {
                    Button { removing = true } label: {
                        Text("목록에서 빼기").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.yeonji)
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
        .fullScreenCover(isPresented: $removing) {
            OkcheConfirmDialog(title: "\(editing.map { PetTitle.withObjectParticle($0.name) } ?? "") 목록에서 뺄까요?",
                               message: "다과 기록에서 더는 고를 수 없어요. 지난 기록의 간식 이름은 그대로 남아요.",
                               confirm: "빼기", confirmLook: .danger,
                               onCancel: { removing = false }, onConfirm: remove) { EmptyView() }
                .presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = removing }
    }

    private var title: String {
        switch mode {
        case .add: "다과 들이기"
        case .addAndPick: "새 다과 들이기"
        case .edit: "다과 고치기"
        }
    }

    private var note: String {
        switch mode {
        case .add: "들인 간식은 다과 기록에서 바로 골라 쓸 수 있어요."
        case .addAndPick: "들이면 다과 기록으로 돌아가 이 간식이 바로 골라져 있어요. 다과 목록에도 남아요."
        case .edit: "목록에서 빼도 지난 기록의 간식 이름은 그대로 남아요."
        }
    }

    private var buttonTitle: String {
        switch mode {
        case .add: "다과에 들이기"
        case .addAndPick: "다과에 들이고 고르기"
        case .edit: "저장하기"
        }
    }

    private func save() {
        guard !saving else { return }
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            nameError = "간식 이름을 넣어 주세요"
            AccessibilityNotification.Announcement("간식 이름을 넣어 주세요").post()
            return
        }
        saving = true
        do {
            if let editing {
                try SnackRules.update(editing, name: name, kind: kind, okDuringFoodChange: ok, in: context)
            } else {
                onAdded(try SnackRules.add(name: name, kind: kind, okDuringFoodChange: ok, in: context))
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
            try SnackRules.remove(editing, in: context)
            removing = false
            dismiss()
        } catch {
            removing = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 다과 기록 시트(보드 `Snack`): 수라 화면 '+ 다과 · 간식 남기기'·기록 줄 연필. 시간, 어떤 다과(이 아이에게 최근 준 순), '+ 새 다과 들이기',
/// 얼마나 줬나요(선택), 수라 바꾸기 기간 안내, '기록하기'.
struct SnackRecordSheet: View {
    let pet: Pet
    var editing: Record?
    /// 저장한 기록 — 부른 화면이 눈에 보이게 알린다(수라 화면 '아뢰었사옵니다').
    var onSaved: (Record) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query private var snacks: [Snack]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }) private var records: [Record]
    @Query(filter: #Predicate<FoodChange> { $0.deletedAt == nil }) private var changes: [FoodChange]
    @State private var occurredAt = Date.now
    @State private var timeChanged = false
    @State private var picked: UUID?
    @State private var amount = ""
    @State private var pickError: String?
    @State private var adding = false
    @State private var failed = false
    @State private var saving = false

    init(pet: Pet, editing: Record? = nil, onSaved: @escaping (Record) -> Void = { _ in }) {
        self.pet = pet
        self.editing = editing
        self.onSaved = onSaved
        if let editing {
            _occurredAt = State(initialValue: editing.occurredAt)
            _picked = State(initialValue: SnackRules.snackId(editing).flatMap(UUID.init(uuidString:)))
            _amount = State(initialValue: SnackRules.amount(editing) ?? "")
        }
    }

    var body: some View {
        let today = OkcheDay.of(.now)
        var options = SnackRules.pickOrder(snacks, petId: pet.id, records: records)
        // 고치는 기록의 다과를 목록에서 뺐어도 고른 채 보이게.
        if let picked, !options.contains(where: { $0.snack.id == picked }), let gone = snacks.first(where: { $0.id == picked }) {
            options.insert((gone, nil), at: 0)
        }
        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    (Text("\(pet.name) · 다과") + Text(" 간식").font(.body).fontWeight(.medium).foregroundColor(OkcheColor.inkMuted))
                        .okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                RecordTimeRow(occurredAt: $occurredAt, changed: $timeChanged, isNew: editing == nil)
                VStack(alignment: .leading, spacing: 8) {
                    OkcheFieldLabel(title: "어떤 다과를 줬나요?").accessibilityAddTraits(.isHeader)
                    ForEach(options, id: \.snack.id) { option in
                        SnackPickCard(name: option.snack.name,
                                      detail: ([SnackRules.kindTitle(option.snack.kind)] + [option.lastDay.map { SnackRules.lastGiven($0, today: today) }].compactMap { $0 })
                                          .joined(separator: " · "),
                                      isOn: picked == option.snack.id) {
                            picked = option.snack.id
                            pickError = nil
                        }
                    }
                    Button { adding = true } label: {
                        Text("+ 새 다과 들이기").okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    OkcheFieldError(message: pickError)
                }
                OkcheTextField(label: "얼마나 줬나요?", note: "(선택)", text: $amount, placeholder: "예: 3알, 반 개")
                if SnackRules.inFoodChange(petId: pet.id, day: OkcheDay.of(occurredAt), changes: changes) {
                    Text("지금은 수라 바꾸기 기간이에요. 간식은 실록에 따로 표시돼서, 처방식 효과를 볼 때 함께 확인할 수 있어요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.namsaekDeep)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 12).padding(.horizontal, 14)
                        .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                }
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button("기록하기", action: save).buttonStyle(.okchePrimary)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.vertical, OkcheSpacing.space6)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .sheet(isPresented: $adding) {
            SnackFormSheet(mode: .addAndPick) { snack in
                picked = snack.id
                pickError = nil
            }
        }
    }

    private func save() {
        guard !saving else { return }
        guard let picked else {
            pickError = "어떤 다과를 줬는지 골라 주세요"
            AccessibilityNotification.Announcement("어떤 다과를 줬는지 골라 주세요").post()
            return
        }
        saving = true
        // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
        let at = editing == nil && !timeChanged ? Date.now : occurredAt
        do {
            onSaved(try SnackRules.record(snackId: picked, amount: amount, occurredAt: at, editing: editing, petId: pet.id, in: context))
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 다과 고르기 줄(다과 기록 시트): 라디오 + 이름 + 아래 줄, 고르면 남색 테두리·연한 남색 바탕.
struct SnackPickCard: View {
    let name: String
    let detail: String
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: isOn ? "largecircle.fill.circle" : "circle")
                    .font(.title3).foregroundStyle(isOn ? OkcheColor.namsaek : OkcheColor.lineStrong)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text(name).okcheText(OkcheType.body, weight: isOn ? .bold : .regular)
                        .foregroundStyle(isOn ? OkcheColor.namsaekDeep : OkcheColor.ink)
                    Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .multilineTextAlignment(.leading)
            .padding(.vertical, 12).padding(.horizontal, 16)
            .frame(minHeight: 52)
            .background(isOn ? OkcheColor.namsaekSoft : OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                .strokeBorder(isOn ? OkcheColor.namsaek : OkcheColor.line, lineWidth: 1.5))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isOn ? [.isSelected] : [])
    }
}
