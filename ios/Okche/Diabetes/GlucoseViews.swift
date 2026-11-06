import SwiftData
import SwiftUI

/// 일기 혈당 줄(보드 `RecordGlucose`, 2026-11-06 승인): 숫자 칸(mg/dL) + '남기기'(빈칸·틀린 값이면 흐림) + ⋯ 혈당 시트.
struct GlucoseRow: View {
    @Binding var text: String
    /// 숫자 칸 포커스 — 키보드 '완료'·남기기에서 내린다.
    var focused: FocusState<Bool>.Binding
    /// 인슐린 줄이 함께 있으면 줄 이름 칸 70(다른 줄과 맞춤).
    var wide = false
    let onLeave: () -> Void
    let onDetail: () -> Void
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 44
    @ScaledMetric(relativeTo: .body) private var narrowLabel: CGFloat = 62
    @ScaledMetric(relativeTo: .body) private var wideLabel: CGFloat = 70
    private var labelWidth: CGFloat { wide ? wideLabel : narrowLabel }
    @ScaledMetric(relativeTo: .body) private var moreSize: CGFloat = 44

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 6) {
                    label.frame(width: labelWidth, alignment: .leading)
                    field
                    more
                }
                VStack(alignment: .leading, spacing: 8) {
                    HStack { label; Spacer(minLength: 6); more }
                    field
                }
            }
            OkcheFieldError(message: GlucoseRules.error(text))
        }
        .padding(.vertical, 10)
        // 틀린 값이 되면 한 번 읽어 줌(흐린 남기기의 이유).
        .onChange(of: GlucoseRules.error(text)) { _, message in
            if let message { AccessibilityNotification.Announcement(message).post() }
        }
    }

    private var label: some View {
        HStack(spacing: 4) {
            Image(systemName: "syringe").font(.body).frame(minWidth: 22).accessibilityHidden(true)
            Text("혈당").okcheText(OkcheType.body, weight: .bold).fixedSize()
        }
        .foregroundStyle(OkcheColor.namsaekDeep)
        .accessibilityAddTraits(.isHeader)
    }

    private var field: some View {
        HStack(spacing: 6) {
            HStack(spacing: 4) {
                TextField(text: $text, prompt: Text("수치").foregroundStyle(OkcheColor.inkMuted)) { Text("혈당 수치 (mg/dL)") }
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                    .keyboardType(.numberPad)
                    .focused(focused)
                    .accessibilityLabel("혈당 수치 (mg/dL)")
                    .accessibilityHint(GlucoseRules.error(text) ?? "")
                Text("mg/dL").okcheText(OkcheType.caption).foregroundStyle(OkcheColor.inkMuted).accessibilityHidden(true)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                .strokeBorder(GlucoseRules.error(text) == nil ? OkcheColor.lineStrong : OkcheColor.yeonji, lineWidth: 1.5))
            Button("남기기", action: onLeave)
                .okcheText(OkcheType.body, weight: .bold)
                .buttonStyle(GlucoseLeaveStyle(height: height))
                .disabled(GlucoseRules.number(text) == nil)
                .accessibilityLabel("혈당 남기기")
        }
    }

    private var more: some View {
        Button(action: onDetail) {
            Image(systemName: "ellipsis").font(.body.weight(.bold)).foregroundStyle(OkcheColor.namsaek)
                .frame(width: min(moreSize, 72), height: min(moreSize, 72))
                .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                    .strokeBorder(OkcheColor.lineStrong, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("혈당 자세히 남기기")
    }
}

/// '남기기': 남색 채운 버튼, 못 누르면 회색(주 버튼과 같은 규칙).
private struct GlucoseLeaveStyle: ButtonStyle {
    let height: CGFloat
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(isEnabled ? .white : OkcheColor.inkMuted)
            .padding(.horizontal, 12)
            .frame(minHeight: height)
            .background(isEnabled ? OkcheColor.namsaek : OkcheColor.line, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .fixedSize()
    }
}

/// '혈당 186 · 언제 쟀나요?'(보드 `GlucoseAsk`): 있는 확인 창을 넓혀 고르는 버튼 셋 + '그만두기'. 고르면 바로 남김.
struct GlucoseAskDialog: View {
    let mgdl: Int
    let onPick: (GlucoseRules.Timing) -> Void
    let onCancel: () -> Void

    var body: some View {
        OkcheConfirmDialog(title: "혈당 \(mgdl) · 언제 쟀나요?", message: "이번 인슐린 기록이 아직 없어요. 고르면 바로 남아요.",
                           confirm: "", showsButtons: false, onCancel: onCancel, onConfirm: {}) {
            VStack(spacing: 10) {
                ForEach(GlucoseRules.timingTitles, id: \.value) { option in
                    // 보드: 남색 테두리·짙은 남색 굵은 글자(있는 `emphasis`).
                    Button(option.title) { onPick(option.value) }.buttonStyle(.okchePrimary(.emphasis))
                }
                Button("그만두기", action: onCancel)
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .contentShape(Rectangle())
                    .buttonStyle(.plain)
                    .keyboardShortcut(.cancelAction)
            }
        }
    }
}

/// 혈당 시트(보드 `Glucose`): 시간, 수치, 언제 쟀나요, 함께 보였나요(고르면 연지색 + 저혈당 안내), 늘 보이는 안내·출처, '기록하기'. 고치기도 같은 시트.
struct GlucoseSheet: View {
    let target: DetailTarget
    let onSaved: (Record, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    @Query(filter: #Predicate<InsulinPlan> { $0.deletedAt == nil }) private var plans: [InsulinPlan]
    @Query(filter: #Predicate<Record> { $0.kind == "insulin" && $0.deletedAt == nil }) private var insulin: [Record]
    @State private var text: String
    @State private var timing: GlucoseRules.Timing?
    @State private var signs: Set<GlucoseRules.Sign>
    @State private var occurredAt: Date
    @State private var timeChanged = false
    @State private var guiding = false
    @State private var failed = false
    @State private var saving = false

    /// `prefill`: 일기 줄에 넣던 수치(맞으면 채움).
    init(target: DetailTarget, prefill: String = "", onSaved: @escaping (Record, Bool) -> Void = { _, _ in }) {
        self.target = target
        self.onSaved = onSaved
        let r = target.editing
        _text = State(initialValue: r.flatMap(GlucoseRules.mgdl).map(String.init) ?? (GlucoseRules.number(prefill).map(String.init) ?? ""))
        _timing = State(initialValue: r.flatMap(GlucoseRules.timing))
        _signs = State(initialValue: r.map(GlucoseRules.signs) ?? [])
        _occurredAt = State(initialValue: r?.occurredAt ?? .now)
    }

    private var petId: UUID? { target.editing?.petId ?? target.petId }
    /// 새 기록의 처음 값은 남기기 짐작(물어야 하면 맞기 전).
    private var picked: GlucoseRules.Timing {
        timing ?? petId.flatMap { GlucoseRules.guess(petId: $0, plans: plans, insulinRecords: insulin) } ?? .beforeInsulin
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    let name = petId.flatMap { id in pets.first { $0.id == id }?.name } ?? "누군지 모름"
                    Text("\(name) · 혈당").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                RecordTimeRow(occurredAt: $occurredAt, changed: $timeChanged, isNew: target.editing == nil)
                OkcheTextField(label: "수치", text: $text, unit: "mg/dL", keyboard: .numberPad, error: GlucoseRules.error(text))
                    .frame(maxWidth: 200, alignment: .leading)
                VStack(alignment: .leading, spacing: 8) {
                    OkcheFieldLabel(title: "언제 쟀나요?").accessibilityAddTraits(.isHeader)
                    OkcheChoiceChips(options: GlucoseRules.timingTitles.map { ($0.value, $0.title) },
                                     selection: Binding(get: { picked }, set: { if let v = $0 { timing = v } }), allowsNone: false)
                }
                VStack(alignment: .leading, spacing: 8) {
                    OkcheFieldLabel(title: "함께 보였나요?", note: "(여러 개 고를 수 있어요)").accessibilityAddTraits(.isHeader)
                    OkcheChoiceCards(options: GlucoseRules.signTitles.map { ($0.value, $0.title) }, checklist: true,
                                     isOn: { signs.contains($0) }, onTap: { s in
                                         if signs.contains(s) { signs.remove(s) } else { signs.insert(s) }
                                         // 안내가 새로 뜨면 화면 읽기로 알림.
                                         if signs.count == 1, signs.contains(s) {
                                             AccessibilityNotification.Announcement("인슐린 맞는 아이가 이런 모습을 보이면 저혈당일 수 있어요. 바로 병원에 연락하길 권해요.").post()
                                         }
                                     })
                    // 고른 뒤에만 저혈당 안내(디자인 고침 — 2026-11-02).
                    if !signs.isEmpty {
                        Button { guiding = true } label: {
                            (Text("인슐린 맞는 아이가 이런 모습을 보이면 저혈당일 수 있어요. 바로 병원에 연락하길 권해요. ") + Text("의서 보기").fontWeight(.bold))
                                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.yeonji)
                                .multilineTextAlignment(.leading)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("의서 안내 열기")
                    }
                }
                Text("인슐린 양은 수의사와 상의 없이 바꾸지 않길 권해요. 맞힌 인슐린은 일기의 인슐린 줄에서 남겨 주세요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                SourceNote(text: diabetesSourceNote)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, OkcheSpacing.space6).padding(.bottom, OkcheSpacing.space4)
        }
        .okcheFooter {
            OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
            Button("기록하기", action: save).buttonStyle(.okchePrimary)
                .disabled(GlucoseRules.number(text) == nil)
                .okcheMissing(GlucoseRules.number(text) == nil ? okcheNamed("수치", "수치를 넣어 주세요") : nil)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .fullScreenCover(isPresented: $guiding) {
            GuideLayer(kind: .glucose) { guiding = false }.presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = guiding }
    }

    private func save() {
        guard !saving, let mgdl = GlucoseRules.number(text) else { return }
        saving = true
        // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
        let at = target.editing == nil && !timeChanged ? Date.now : occurredAt
        do {
            let record = try GlucoseRules.save(petId: petId, mgdl: mgdl, timing: picked, signs: signs, at: at, editing: target.editing, in: context)
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

/// 자세히 시트 고르기: 혈당은 혈당 시트, 나머지는 자세히 시트.
struct DetailSheet: View {
    let target: DetailTarget
    var prefill = ""
    var onSaved: (Record, Bool) -> Void = { _, _ in }

    var body: some View {
        switch target.kind {
        case .glucose: GlucoseSheet(target: target, prefill: prefill, onSaved: onSaved)
        case .insulin: InsulinSheet(target: target, onSaved: onSaved)
        default: RecordDetailSheet(target: target, onSaved: onSaved)
        }
    }
}
