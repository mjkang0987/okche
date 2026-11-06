import SwiftData
import SwiftUI

/// 일기 인슐린 줄 내용(혈당 줄 아래, 당뇨 아이만 — 9-2).
enum InsulinLine {
    /// 지금 차례 처리 전: '✓ 맞음 · 2단위'·'건너뜀'.
    case ready(InsulinRules.Slot)
    /// 처리함: 그 기록과 다음 차례 시각(누르면 고치기 시트, 승인 2026-11-06).
    case handled(InsulinRules.Slot, Record, next: Date)
    /// 열린 인슐린 계획이 없는 당뇨 아이: '인슐린 설정이 없어요 · 설정하기 ›'.
    case noPlan
}

/// 일기 인슐린 줄(보드 `RecordGlucose`·`RecordInsulinDone`·`RecordInsulinSkip`).
struct InsulinRow: View {
    let line: InsulinLine
    let onGive: (InsulinRules.Slot, InsulinRules.Status) -> Void
    let onEdit: (Record) -> Void
    let onDetail: () -> Void
    let onSetup: () -> Void
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 44
    @ScaledMetric(relativeTo: .body) private var labelWidth: CGFloat = 62
    @ScaledMetric(relativeTo: .body) private var moreSize: CGFloat = 44

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 6) {
                label.frame(width: labelWidth, alignment: .leading)
                content
                Spacer(minLength: 0)
                more
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack { label; Spacer(minLength: 6); more }
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 10)
    }

    private var label: some View {
        HStack(spacing: 4) {
            Image(systemName: "cross.vial").font(.body).frame(minWidth: 22).accessibilityHidden(true)
            Text("인슐린").okcheText(OkcheType.body, weight: .bold).fixedSize()
        }
        .foregroundStyle(OkcheColor.namsaekDeep)
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private var content: some View {
        switch line {
        case .ready(let slot):
            let spoken = MedSlots.spokenTime(slot.due)
            HStack(spacing: 6) {
                quick("✓ 맞음 · \(MedRules.number(slot.units))단위", label: "인슐린 \(spoken) · \(MedRules.number(slot.units))단위 맞음 · 바로 기록") { onGive(slot, .full) }
                quick("건너뜀", label: "인슐린 \(spoken) 건너뜀 · 바로 기록") { onGive(slot, .skipped) }
            }
        case .handled(let slot, let record, let next):
            let skipped = InsulinRules.status(record) == .skipped
            let time = QuickRecord.time(record)
            HStack(spacing: 8) {
                Button { onEdit(record) } label: {
                    Text(skipped ? "– \(time) 건너뜀" : "✓ \(time) 맞음").okcheText(OkcheType.body, weight: .bold)
                        .foregroundStyle(skipped ? OkcheColor.inkMuted : OkcheColor.namsaekDeep)
                        .fixedSize()
                        .padding(.horizontal, 10)
                        .frame(minHeight: height)
                        .background(skipped ? OkcheColor.paper : OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                        .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                            .strokeBorder(skipped ? OkcheColor.lineStrong : OkcheColor.namsaek, lineWidth: 1.5))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("인슐린 \(MedSlots.spokenTime(slot.due)) · \(time)에 \(skipped ? "건너뜀" : "\(MedRules.number(InsulinRules.unitsValue(record)))단위 맞음") · 눌러서 고치기")
                Text("다음\n\(MedSlots.clockText(next))").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    .fixedSize()
                    .accessibilityLabel("다음 \(MedSlots.clockText(next))")
            }
        case .noPlan:
            Button(action: onSetup) {
                (Text("인슐린 설정이 없어요 · ") + Text("설정하기 ›").fontWeight(.bold).foregroundColor(OkcheColor.namsaek))
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    .frame(minHeight: height)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    /// 바로 기록 버튼(일기 다른 줄과 같은 모양).
    private func quick(_ title: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                .fixedSize()
                .padding(.horizontal, 8)
                .frame(minWidth: 52, minHeight: height)
                .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd).strokeBorder(OkcheColor.lineStrong, lineWidth: 1.5))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    @ViewBuilder private var more: some View {
        if case .noPlan = line {
            EmptyView()
        } else {
            Button(action: onDetail) {
                Image(systemName: "ellipsis").font(.body.weight(.bold)).foregroundStyle(OkcheColor.namsaek)
                    .frame(width: min(moreSize, 72), height: min(moreSize, 72))
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                        .strokeBorder(OkcheColor.lineStrong, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("인슐린 자세히 남기기")
        }
    }
}

/// 인슐린 시트(보드 `Insulin`·`InsulinTwice`): 시간(+ 이름·정해 둔 양, 이미 맞은 시간대면 연지색 경고), 맞은 양, 어떻게 됐나요, 맞기 전에 밥은(선택),
/// 안내, '인슐린 이름·양·시간 바꾸기 ›', 출처, '기록하기'. 고치기도 같은 시트.
struct InsulinSheet: View {
    let target: DetailTarget
    let onSaved: (Record, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    @Query(filter: #Predicate<InsulinPlan> { $0.deletedAt == nil }) private var plans: [InsulinPlan]
    @Query(filter: #Predicate<Record> { $0.kind == "insulin" && $0.deletedAt == nil }) private var records: [Record]
    @State private var text: String?
    @State private var status: InsulinRules.Status
    @State private var meal: String?
    @State private var occurredAt: Date
    @State private var timeChanged = false
    @State private var setup: Pet?
    @State private var failed = false
    @State private var saving = false

    init(target: DetailTarget, onSaved: @escaping (Record, Bool) -> Void = { _, _ in }) {
        self.target = target
        self.onSaved = onSaved
        let r = target.editing
        _text = State(initialValue: r.flatMap { InsulinRules.status($0) == .skipped ? nil : MedRules.number(InsulinRules.unitsValue($0)) })
        _status = State(initialValue: r.flatMap(InsulinRules.status) ?? .full)
        _meal = State(initialValue: r.flatMap(InsulinRules.mealBefore))
        _occurredAt = State(initialValue: r?.occurredAt ?? .now)
    }

    private var petId: UUID? { target.editing?.petId ?? target.petId }
    private var plan: InsulinPlan? { petId.flatMap { GlucoseRules.openPlan(plans, petId: $0) } }
    /// 시트 시각이 든 구간의 차례(새 기록은 시간을 안 바꿨으면 지금).
    private var sheetTime: Date { target.editing == nil && !timeChanged ? .now : occurredAt }
    private var slot: InsulinRules.Slot? { plan.flatMap { InsulinRules.slot($0, at: sheetTime) } }
    /// 맞은 양 칸: 손대지 않았으면 그 차례 정해 둔 양.
    private var unitsText: String { text ?? slot.map { MedRules.number($0.units) } ?? "" }
    private var twice: Record? {
        guard let petId, let slot else { return nil }
        return InsulinRules.twice(petId: petId, records: records, slot: slot, editing: target.editing)
    }
    private var missing: String? { status == .skipped || InsulinRules.units(unitsText) != nil ? nil : okcheNamed("맞은 양", "맞은 양을 넣어 주세요") }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    let name = petId.flatMap { id in pets.first { $0.id == id }?.name } ?? "누군지 모름"
                    Text("\(name) · 인슐린").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                VStack(alignment: .leading, spacing: 6) {
                    RecordTimeRow(occurredAt: $occurredAt, changed: $timeChanged, isNew: target.editing == nil,
                                  detail: slot.map { "\(plan?.name ?? "인슐린") · 정해 둔 양 \(MedRules.number($0.units))단위" },
                                  warning: twice != nil)
                    if let twice, let slot {
                        Text("이 시간대(\(MedSlots.clockText(slot.due)) 차례)에 이미 맞은 기록이 있어요 · \(QuickRecord.time(twice)) · \(MedRules.number(InsulinRules.unitsValue(twice)))단위. 두 번 맞히지 않았는지 확인해 주세요. 맞다면 그대로 남길 수 있어요.")
                            .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.yeonji)
                    }
                }
                // 경고가 새로 뜨면 화면 읽기로 알림.
                .onChange(of: twice?.id) { _, id in
                    if id != nil { AccessibilityNotification.Announcement("이 시간대에 이미 맞은 기록이 있어요. 두 번 맞히지 않았는지 확인해 주세요.").post() }
                }
                if status != .skipped {
                    OkcheTextField(label: "맞은 양", text: Binding(get: { unitsText }, set: { text = $0 }), unit: "단위",
                                   keyboard: .decimalPad, error: InsulinRules.unitsError(unitsText))
                        .frame(maxWidth: 200, alignment: .leading)
                }
                VStack(alignment: .leading, spacing: 8) {
                    OkcheFieldLabel(title: "어떻게 됐나요?").accessibilityAddTraits(.isHeader)
                    OkcheChoiceChips(options: InsulinRules.statusTitles.map { ($0.value, $0.title) },
                                     selection: Binding(get: { status }, set: { if let v = $0 { status = v } }), allowsNone: false)
                }
                VStack(alignment: .leading, spacing: 8) {
                    OkcheFieldLabel(title: "맞기 전에 밥은?", note: "(선택)").accessibilityAddTraits(.isHeader)
                    OkcheChoiceChips(options: InsulinRules.mealTitles.map { ($0.value, $0.title) }, selection: $meal)
                }
                Text("밥을 안 먹은 날 인슐린을 어떻게 할지는 미리 수의사와 정해 두길 권해요. 양은 상의 없이 바꾸지 않길 권해요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                if let pet = pets.first(where: { $0.id == petId && $0.memorializedAt == nil }) {
                    Button { setup = pet } label: {
                        Text("인슐린 이름·양·시간 바꾸기 ›").okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                            .frame(minHeight: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                SourceNote(text: diabetesSourceNote)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, OkcheSpacing.space6).padding(.bottom, OkcheSpacing.space4)
        }
        .okcheFooter {
            OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
            Button("기록하기", action: save).buttonStyle(.okchePrimary)
                .disabled(missing != nil)
                .okcheMissing(missing)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .sheet(item: $setup) { pet in NavigationStack { InsulinSetupScreen(pet: pet) } }
    }

    private func save() {
        guard !saving, missing == nil, let petId else { return }
        saving = true
        do {
            let record = try InsulinRules.save(petId: petId, plan: plan, units: InsulinRules.units(unitsText) ?? 0, status: status,
                                               mealBefore: meal, at: sheetTime, editing: target.editing, in: context)
            onSaved(record, target.editing == nil)
            if target.editing != nil { AccessibilityNotification.Announcement("고쳤어요").post() }
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}
