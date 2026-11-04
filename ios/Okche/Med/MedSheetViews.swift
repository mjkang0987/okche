import SwiftData
import SwiftUI

/// 오늘 먹일 약 시트(보드 `MedSheet`, plan.md 8-3b): 오늘의 모든 차례를 먹일 시간으로 묶어 시간 순, 약마다 줬어요·건너뜀·먹고 토함을 골라 두고
/// '기록하기'로 한 번에. 이미 기록한 차례는 고른 채 흐리게(여기서 못 바꿈 — 고치기는 기록 줄 연필). 안드로이드 `MedSheetScreens.kt`와 같은 짜임.
struct MedTodaySheet: View {
    let pet: Pet
    var onSaved: ([Record]) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<MedSchedule> { $0.deletedAt == nil }) private var schedules: [MedSchedule]
    @Query private var meds: [Medication]
    @Query(filter: #Predicate<Record> { $0.kind == "med" && $0.deletedAt == nil }) private var records: [Record]
    /// 고른 차례: `slotKey` → 상태.
    @State private var picks: [String: String] = [:]
    @State private var extra = false
    @State private var failed = false
    @State private var saving = false

    static let statuses: [(value: String, title: String)] = [("given", "줬어요"), ("skipped", "건너뜀"), ("vomited", "먹고 토함")]

    var body: some View {
        // shortcut: 켜 둔 채 6시를 넘겨도 다시 그리기 전까지 어제 차례가 보임, 일기 탭과 같이 날 바뀜을 다룰 때 함께.
        let entries = MedSlots.today(schedules, records: records, petId: pet.id, day: OkcheDay.of(.now))
        let groups = Dictionary(grouping: entries, by: \.slot.due).sorted { $0.key < $1.key }
        let next = groups.first { $0.value.contains { $0.record == nil } }?.key
        return ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                SheetHeader(title: "\(pet.name) · 탕약", subtitle: "오늘 먹일 약") { dismiss() }
                if groups.isEmpty {
                    Text("오늘 먹일 약이 없어요.").okcheText(OkcheType.body, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                ForEach(groups, id: \.key) { due, list in
                    group(due: due, list: list, isNext: due == next)
                }
                Button { extra = true } label: {
                    Text("+ 예정에 없던 약 남기기").okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if !groups.isEmpty {
                    Text("'먹고 토함'은 실록에 따로 세요. 다시 먹여도 되는지는 병원에 물어보길 권해요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                if !groups.isEmpty {
                    Button("기록하기") { save(entries) }.buttonStyle(.okchePrimary).disabled(picks.isEmpty)
                }
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.vertical, OkcheSpacing.space6)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .sheet(isPresented: $extra) {
            MedExtraSheet(pet: pet) { record in
                onSaved([record])
                dismiss()
            }
        }
    }

    /// 묶음: 머리 '오전 8:00' · 오른쪽 '08:05에 기록함'(모두 기록) 또는 '다음 차례', 약마다 줄.
    private func group(due: Date, list: [MedSlots.Entry], isNext: Bool) -> some View {
        let done = list.allSatisfy { $0.record != nil }
        let right = done ? list.compactMap(\.record).map(\.occurredAt).max().map { "\(MedSlots.hhmm($0))에 기록함" } : isNext ? "다음 차례" : nil
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(MedSlots.clockText(due)).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                Spacer(minLength: 8)
                if let right { Text(right).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted) }
            }
            .padding(.top, 10)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            ForEach(Array(list.enumerated()), id: \.element.slot.key) { index, entry in
                if index > 0 { OkcheColor.line.frame(height: 1) }
                row(entry)
            }
        }
        .padding(.horizontal, 14).padding(.bottom, 6)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .opacity(done ? 0.7 : 1)
    }

    private func row(_ entry: MedSlots.Entry) -> some View {
        let med = meds.first { $0.id == entry.slot.schedule.medicationId }
        let recorded = entry.record.flatMap { MedSlots.json($0)["status"] as? String }
        return VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(med?.name ?? "약").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                Text(MedSlots.slotDetail(entry.slot.schedule, med: med)).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            }
            .accessibilityElement(children: .combine)
            OkcheChoiceChips(options: Self.statuses,
                             selection: Binding(get: { recorded ?? picks[entry.slot.key] }, set: { picks[entry.slot.key] = $0 }))
                .disabled(recorded != nil)
                .accessibilityHint(recorded != nil ? "이미 기록했어요. 고치려면 기록 목록에서" : "")
        }
        .padding(.vertical, 12)
    }

    private func save(_ entries: [MedSlots.Entry]) {
        guard !saving else { return }
        let chosen = entries.filter { $0.record == nil }.compactMap { e in picks[e.slot.key].map { (slot: e.slot, status: $0) } }
        guard !chosen.isEmpty else { return }
        saving = true
        do {
            onSaved(try MedSlots.record(chosen, in: context))
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 예정에 없던 약 시트(보드 `MedExtra`) — 탕약 기록 고치기도 같은 칸(약은 고정, 차례 기록이면 '건너뜀'도).
struct MedExtraSheet: View {
    let pet: Pet
    var editing: Record?
    var onSaved: (Record) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<MedSchedule> { $0.deletedAt == nil }) private var schedules: [MedSchedule]
    @Query private var meds: [Medication]
    @State private var picked: UUID?
    @State private var amount = ""
    @State private var status = "given"
    @State private var occurredAt = Date.now
    @State private var timeChanged = false
    @State private var pickError: String?
    @State private var amountError: String?
    @State private var adding = false
    @State private var failed = false
    @State private var saving = false

    init(pet: Pet, editing: Record? = nil, onSaved: @escaping (Record) -> Void = { _ in }) {
        self.pet = pet
        self.editing = editing
        self.onSaved = onSaved
        if let editing {
            let j = MedSlots.json(editing)
            _picked = State(initialValue: (j["medicationId"] as? String).flatMap(UUID.init(uuidString:)))
            _amount = State(initialValue: (j["amount"] as? NSNumber).map { MedRules.number($0.doubleValue) } ?? "")
            _status = State(initialValue: j["status"] as? String ?? "given")
            _occurredAt = State(initialValue: editing.occurredAt)
        }
    }

    private var statuses: [(value: String, title: String)] {
        editing?.slotKey != nil ? MedTodaySheet.statuses : MedTodaySheet.statuses.filter { $0.value != "skipped" }
    }

    var body: some View {
        let options = editing == nil
            ? MedSlots.extraOptions(meds, schedules: schedules, pet: pet)
            : meds.filter { $0.id == picked }.map { ($0, MedRules.formTitle($0.form)) } // 고치기는 약 고정(약방에서 뺀 약도)
        let med = meds.first { $0.id == picked }
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SheetHeader(title: "\(pet.name) · \(editing == nil ? "예정에 없던 약" : "탕약 기록")") { dismiss() }
                VStack(alignment: .leading, spacing: 8) {
                    OkcheFieldLabel(title: "어떤 약").accessibilityAddTraits(.isHeader)
                    ForEach(options, id: \.med.id) { option in
                        SnackPickCard(name: option.med.name, detail: option.detail, isOn: picked == option.med.id) { pick(option.med) }
                            .disabled(editing != nil)
                    }
                    if editing == nil {
                        Button { adding = true } label: {
                            Text("+ 약방에 새 약 들이기").okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    OkcheFieldError(message: pickError)
                }
                OkcheTextField(label: "얼마나", text: $amount, unit: med.map { MedRules.unitTitle($0.unit) }, keyboard: .decimalPad, error: amountError)
                VStack(alignment: .leading, spacing: 6) {
                    OkcheFieldLabel(title: "언제")
                    RecordTimeRow(occurredAt: $occurredAt, changed: $timeChanged, isNew: editing == nil)
                }
                FieldGroup(title: "어떻게 됐나요") {
                    OkcheChoiceChips(options: statuses, selection: Binding(get: { status }, set: { status = $0 ?? status }), allowsNone: false)
                }
                if editing == nil {
                    Text("정해 둔 시간 밖에 준 약이라 탕약 횟수(1/3)에는 들어가지 않아요. 약방 남은 양에서는 빠져요.")
                        .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
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
            MedFormSheet(mode: .addAndPick) { pick($0) }
        }
        // 새로 남길 땐 맨 위 약(이 아이의 필요할 때만 약)을 골라 둔다(보드 `MedExtra`).
        .task(id: options.first?.med.id) {
            if editing == nil, picked == nil, let first = options.first { pick(first.med) }
        }
    }

    /// 고르면 '얼마나'는 그 약의 '한 번에'.
    private func pick(_ med: Medication) {
        picked = med.id
        amount = MedRules.number(med.doseAmount)
        pickError = nil
        amountError = nil
    }

    private func save() {
        guard !saving else { return }
        let med = meds.first { $0.id == picked }
        let value = PetRegistration.number(amount.trimmingCharacters(in: .whitespaces))
        pickError = med == nil ? "어떤 약인지 골라 주세요" : nil
        amountError = (value ?? 0) > 0 ? nil : "0보다 큰 숫자로 넣어 주세요"
        guard let med, let value, value > 0 else {
            AccessibilityNotification.Announcement([pickError, amountError].compactMap { $0 }.joined(separator: ". ")).post()
            return
        }
        saving = true
        // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
        let at = editing == nil && !timeChanged ? Date.now : occurredAt
        do {
            onSaved(try MedSlots.saveExtra(petId: pet.id, medication: med, amount: value, status: status, occurredAt: at, editing: editing, in: context))
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 시트 머리: 제목(+ 흐린 덧말) + 닫기 44 — 다과 기록 시트 머리와 같은 모양.
private struct SheetHeader: View {
    let title: String
    var subtitle: String?
    let onClose: () -> Void

    var body: some View {
        HStack {
            (Text(title) + Text(subtitle.map { " \($0)" } ?? "").font(.body).fontWeight(.medium).foregroundColor(OkcheColor.inkMuted))
                .okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            Button(action: onClose) {
                Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                    .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel("닫기")
        }
    }
}
