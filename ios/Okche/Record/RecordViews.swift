import SwiftData
import SwiftUI

/// 일기 탭 첫 화면(보드 `Record`): '누구 기록인가요?' 칩, 바로 기록 줄(구토·매화·소피는 ⋯ 자세히 — 7-2, 탕약 — 8-3a), 방금 남긴 기록. 옥체·사진은 그 단계에서.
struct RecordTab: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @Environment(\.modelContext) private var context
    /// '누군지 몰라요'를 골랐는지(이 화면에서만 — 지금 보는 아이는 그대로).
    @State private var unknownPicked = false
    /// 방금 누른 기록 — 그 기록일 때만 '아뢰었사옵니다'.
    @State private var justSaved: UUID?
    @State private var failed = false
    @State private var detail: DetailTarget?
    /// 탕약 ⋯ 오늘 먹일 약 시트(지금 보는 아이).
    @State private var medSheet: Pet?
    /// 일기 탭 안 화면 쌓기(수라 화면 — 7-3). `navigationDestination(isPresented:)`는 이 탭에서 화면을 끝없이 다시 그려 멈춰서(2026-11-04 확인) 경로로.
    @State private var path: [RecordRoute] = []
    // shortcut: 약 주기·탕약 기록 전체를 읽어 화면에서 거름, 기록이 수천 건이 되어 느려지면 `@Query`에 아이·날 조건을 넣는다.
    @Query(filter: #Predicate<MedSchedule> { $0.deletedAt == nil }) private var schedules: [MedSchedule]
    @Query private var meds: [Medication]
    @Query(filter: #Predicate<Record> { $0.kind == "med" && $0.deletedAt == nil }) private var medRecords: [Record]
    /// 방금 누른 탕약 버튼 — 약 1초 '✓ 프레드'로 보였다가 빠짐.
    @State private var checked: (key: String, medicationId: UUID, title: String, name: String)?
    /// 탭 바 위 '프레드니솔론 줌 · 21:02 · 되돌리기'와 되돌릴 기록.
    @Environment(\.showUndo) private var showUndo
    /// 탕약 알림의 '되돌리기' = 방금 남긴 기록 지우기(확인 창 → 지움 → '기록을 지웠어요 · 되돌리기').
    @State private var deleting: Record?
    /// 혈당 줄 숫자 칸(9-1)과 '언제 쟀나요?' 창에 띄울 수치.
    @State private var glucoseText = ""
    @FocusState private var glucoseFocused: Bool
    /// 묻는 수치와 그 아이(창이 떠 있는 사이 아이가 바뀌어도 물은 아이로).
    @State private var askingGlucose: GlucoseAsk?
    @State private var savingGlucose = false
    /// 인슐린 '설정하기 ›'(열린 계획이 없을 때 — 9-2).
    @State private var insulinSetup: Pet?
    @State private var savingInsulin = false
    /// 인슐린 줄의 '지금' — 구간이 끝날 때·앱으로 돌아올 때 다시 본다(켜 둔 채 차례가 바뀌어도 맞게).
    @State private var clock = Date.now
    @Environment(\.scenePhase) private var scenePhase
    @State private var router = NotifyRouter.shared
    @Query(filter: #Predicate<InsulinPlan> { $0.deletedAt == nil }) private var plans: [InsulinPlan]
    @Query(filter: #Predicate<Record> { $0.kind == "insulin" && $0.deletedAt == nil }) private var insulinRecords: [Record]

    /// 칩에 나오는 아이: 추억·지운 아이를 뺀 아이들(모신 순).
    private var chipPets: [Pet] { pets.filter { $0.memorializedAt == nil } }

    /// 고른 아이: '누군지 몰라요'면 nil. 지금 보는 아이가 칩에 없으면(추억·아이 없음) '누군지 몰라요'(2026-11-03 사용자).
    private var who: UUID? {
        guard !unknownPicked, let current = PetTitle.current(pets, lastPetId: lastPetId), current.memorializedAt == nil else { return nil }
        return current.id
    }

    var body: some View {
        NavigationStack(path: $path) {
            TabScreen(title: "승정원일기", subtitle: "매일 기록", trailing: { PetSeal() }) {
                FieldGroup(title: "누구 기록인가요?") {
                    OkcheChoiceChips(options: chipPets.map { ($0.id.uuidString, $0.name) } + [("", "누군지 몰라요")],
                                     selection: Binding(get: { who?.uuidString ?? "" }, set: { pick($0 ?? "") }),
                                     allowsNone: false)
                }
                // 수라 ⋯은 수라 화면(지금 보는 아이 — 7-3), 아이가 없거나 추억이면 숨김.
                let suraPet = PetTitle.current(pets, lastPetId: lastPetId).map { $0.memorializedAt == nil } ?? false
                QuickRecordCard(onTap: { row, value in record(row, value) }, onDetail: { kind in
                    if kind == .meal { path.append(.sura(nil)) }
                    else if kind == .med { medSheet = pets.first { $0.id == who } }
                    else { detail = DetailTarget(kind: kind, petId: who) }
                }, mealMore: suraPet, med: medLine, onGive: give,
                   glucose: showsGlucose ? $glucoseText : nil, glucoseFocused: $glucoseFocused, onLeaveGlucose: leaveGlucose,
                   insulin: insulinLine, onInsulin: giveInsulin, onInsulinEdit: { detail = DetailTarget(kind: .insulin, editing: $0) },
                   onInsulinSetup: { insulinSetup = pets.first { $0.id == who } })
                // 보드 `RecordGlucose`: 인슐린·탕약 줄이 있을 때만 그 안내.
                Text("누르면 바로 기록돼요. " + (insulinLine == nil ? "" : "인슐린은 '맞음'을 누르면 정해 둔 양과 지금 시각으로 남아요. ")
                     + (medLine == nil ? "" : "탕약은 오늘 줄 약이 먹일 시간 순으로 버튼이 돼요. ") + "빛깔·모양 같은 자세한 기록은 ⋯ 버튼으로.").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                RecentRecordSection(justSaved: justSaved)
            }
            .toolbar(.hidden, for: .navigationBar)
            .deleteRecordDialog($deleting, pets: pets)
            // 다른 아이로 바꾸면 앞 아이 되돌리기는 닫고, 적던 혈당은 비움(다른 아이 기록으로 남지 않게).
            .onChange(of: who) {
                showUndo(nil)
                glucoseText = ""
                askingGlucose = nil
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("완료") { glucoseFocused = false }
                }
            }
            .navigationDestination(for: RecordRoute.self) { route in
                switch route {
                case .sura(let petId): SuraScreen(petId: petId)
                case .log: RecordLogView()
                }
            }
        }
        .sheet(item: $medSheet) { pet in
            MedTodaySheet(pet: pet) { saved in
                guard let last = saved.last else { return }
                justSaved = last.id
                AccessibilityNotification.Announcement("아뢰었사옵니다").post()
            }
        }
        .sheet(item: $detail) { target in
            DetailSheet(target: target, prefill: glucoseText) { record, isNew in
                guard isNew else { return }
                justSaved = record.id
                if record.kind == RecordKind.glucose.rawValue { glucoseText = "" }
                AccessibilityNotification.Announcement("아뢰었사옵니다").post()
            }
        }
        .fullScreenCover(item: $askingGlucose) { ask in
            GlucoseAskDialog(mgdl: ask.mgdl, onPick: { timing in
                askingGlucose = nil
                saveGlucose(ask.mgdl, timing, petId: ask.petId)
            }, onCancel: { askingGlucose = nil })
            .presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = askingGlucose != nil }
        .sheet(item: $insulinSetup) { pet in NavigationStack { InsulinSetupScreen(pet: pet) } }
        .onChange(of: scenePhase) { if scenePhase == .active { clock = .now } }
        // 인슐린 알림을 누르면 그 아이를 골라 둔다(9-3) — 탭은 `RootTabView`가 일기로 바꾼다. 추억·지운 아이면 그대로.
        .onChange(of: router.insulinPetId, initial: true) { _, id in
            guard let id else { return }
            router.insulinPetId = nil
            guard chipPets.contains(where: { $0.id == id }) else { return }
            // 오늘 먹일 약 시트는 닫는다. 입력 중인 시트(자세히·인슐린 설정)는 그대로 — 남은 약 알림과 같이 입력을 잃지 않게.
            unknownPicked = false
            lastPetId = id.uuidString
            path = []
            medSheet = nil
            clock = .now
        }
        .task(id: insulinWindowEnd) {
            guard let end = insulinWindowEnd, end > .now else { return }
            try? await Task.sleep(for: .seconds(end.timeIntervalSinceNow + 1))
            if !Task.isCancelled { clock = .now }
        }
    }

    /// 인슐린 줄: 당뇨 아이만. 열린 계획이 없으면 설정 안내, 지금 구간에 기록이 있으면 처리한 상태.
    private var insulinLine: InsulinLine? {
        guard showsGlucose, let who else { return nil }
        guard let plan = GlucoseRules.openPlan(plans, petId: who) else { return .noPlan }
        guard let slot = InsulinRules.slot(plan, at: max(clock, .now)) else { return .noPlan }
        if let record = InsulinRules.handled(petId: who, records: insulinRecords, slot: slot) {
            return .handled(slot, record, next: InsulinRules.next(slot))
        }
        return .ready(slot)
    }

    private var insulinWindowEnd: Date? {
        if case .ready(let s) = insulinLine { return s.end }
        if case .handled(let s, _, _) = insulinLine { return s.end }
        return nil
    }

    /// '맞음'(그 차례 정해 둔 양)·'건너뜀'(0): 바로 기록 → 처리한 상태, 탭 바 위 되돌리기(= 지우기 확인 창).
    /// 누른 때 차례를 다시 본다(오래 켜 둔 화면의 옛 차례 양으로 남지 않게).
    private func giveInsulin(_ shown: InsulinRules.Slot, _ status: InsulinRules.Status) {
        guard !savingInsulin, let who, let plan = GlucoseRules.openPlan(plans, petId: who) else { return }
        guard let slot = InsulinRules.slot(plan, at: .now), slot.key == shown.key else { clock = .now; return } // 차례가 바뀌었으면 줄만 새로
        savingInsulin = true
        Task { try? await Task.sleep(for: .seconds(1)); savingInsulin = false } // 빨리 두 번 눌러 둘 남지 않게
        do {
            let saved = try InsulinRules.save(petId: who, plan: plan, units: slot.units, status: status, mealBefore: nil, at: .now, in: context)
            failed = false
            justSaved = saved.id
            let text = status == .skipped ? "인슐린 건너뜀" : "인슐린 \(MedRules.number(slot.units))단위 맞음"
            showUndo(UndoToastItem(text: "\(text) · \(QuickRecord.time(saved))") {
                deleting = saved // 지우기 확인 창(지우면 버튼이 돌아옴)
                return true
            })
            AccessibilityNotification.Announcement("아뢰었사옵니다").post()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    private struct GlucoseAsk: Identifiable { let mgdl: Int; let petId: UUID; let id = UUID() }

    /// 혈당 줄: 고른 아이가 당뇨일 때만(누군지 모르면 없음).
    private var showsGlucose: Bool { pets.first { $0.id == who }?.hasDiabetes == true }

    /// '남기기': 지금 차례 인슐린 기록이 있으면 맞고 난 뒤, 계획이 없으면 그 밖으로 바로, 아니면 '언제 쟀나요?'(9-1).
    private func leaveGlucose() {
        guard let who, let mgdl = GlucoseRules.number(glucoseText) else { return }
        glucoseFocused = false
        if let timing = GlucoseRules.guess(petId: who, plans: plans, insulinRecords: insulinRecords) {
            saveGlucose(mgdl, timing, petId: who)
        } else {
            askingGlucose = GlucoseAsk(mgdl: mgdl, petId: who)
        }
    }

    private func saveGlucose(_ mgdl: Int, _ timing: GlucoseRules.Timing, petId: UUID) {
        guard !savingGlucose else { return } // 두 번 눌러 둘 남지 않게
        savingGlucose = true
        Task { try? await Task.sleep(for: .seconds(1)); savingGlucose = false }
        do {
            let saved = try GlucoseRules.save(petId: petId, mgdl: mgdl, timing: timing, signs: [], at: .now, in: context)
            failed = false
            glucoseText = ""
            justSaved = saved.id
            showUndo(UndoToastItem(text: "혈당 \(GlucoseRules.value(saved))") {
                deleting = saved // 지우기 확인 창(지우면 '기록을 지웠어요 · 되돌리기')
                return true
            })
            AccessibilityNotification.Announcement("아뢰었사옵니다").post()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    /// 오늘 남은 차례(6-2) — 지금 보는 아이. 누군지 모르면 없음.
    private var slots: [MedSlots.Slot] {
        guard let who else { return [] }
        return MedSlots.remaining(schedules, records: medRecords, petId: who, day: OkcheDay.of(.now))
    }

    /// 탕약 줄: 그 아이에게 열린 약 주기가 있을 때만. 누른 버튼은 '✓'로 맨 앞, 같은 약 다음 차례는 그 뒤에 들어옴.
    private var medLine: MedLine? {
        guard let who, MedSlots.hasOpen(schedules, petId: who) else { return nil }
        // 약을 못 찾은 차례는 버튼으로 만들지 않음(빈 글자 버튼 방지).
        let buttons = MedSlots.buttons(slots.filter { $0.schedule.medicationId != checked?.medicationId }).compactMap { slot -> MedLine.Button? in
            guard let med = meds.first(where: { $0.id == slot.schedule.medicationId }) else { return nil }
            return MedLine.Button(slot: slot, title: MedSlots.shortName(med),
                                  label: "탕약 \(med.name) \(MedSlots.spokenTime(slot.due)) · 줬어요 · 바로 기록")
        }
        return MedLine(checked: checked.map { ($0.key, $0.title, $0.name) }, buttons: Array(buttons.prefix(checked == nil ? 3 : 2)))
    }

    /// 탕약 버튼: 바로 `med` 기록 → 약 1초 '✓' → 빠짐, 탭 바 위 되돌리기 알림.
    private func give(_ slot: MedSlots.Slot) {
        let med = meds.first { $0.id == slot.schedule.medicationId }
        do {
            let saved = try MedSlots.record(slot, in: context)
            failed = false
            justSaved = saved.id
            checked = (slot.key, slot.schedule.medicationId, "✓ \(MedSlots.shortName(med))", med?.name ?? "약")
            showUndo(UndoToastItem(text: "\(med?.name ?? "약") 줌 · \(QuickRecord.time(saved))") {
                deleting = saved // 지우기 확인 창(지우면 '기록을 지웠어요 · 되돌리기')
                return true
            })
            Task {
                try? await Task.sleep(for: .seconds(1))
                if checked?.key == slot.key { checked = nil } // 그새 다른 약을 눌렀으면 그 '✓'는 남김
            }
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    /// 칩에서 아이를 고르면 지금 보는 아이도 바뀐다(다른 탭도). '누군지 몰라요'는 이 화면에서만.
    private func pick(_ id: String) {
        if id.isEmpty {
            unknownPicked = true
        } else {
            unknownPicked = false
            lastPetId = id
        }
    }

    private func record(_ row: QuickRecord.Row, _ value: String) {
        do {
            justSaved = try QuickRecord.save(row, value: value, petId: who, in: context).id
            failed = false
            AccessibilityNotification.Announcement("아뢰었사옵니다").post()
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 일기 탭 안 화면. 수라 화면은 그 아이로(없으면 지금 보는 아이) — 기록 줄 연필에서 다른 아이 수라도 연다.
/// 기록 목록도 값으로 쌓는다 — 경로 쌓기와 화면을 직접 넘기는 쌓기를 섞으면 뒤로 갈 때 기록 목록이 빠질 수 있다.
enum RecordRoute: Hashable { case sura(UUID?), log }

/// 탕약 줄 내용: 방금 누른 '✓' 버튼과 남은 차례 버튼(같은 약 하나·최대 3). 둘 다 없으면 '오늘 줄 약을 다 줬어요'.
struct MedLine {
    struct Button {
        let slot: MedSlots.Slot
        let title: String
        let label: String
    }
    let checked: (key: String, title: String, name: String)?
    let buttons: [Button]
}

/// 바로 기록 줄 넷(구토·매화·소피·수라): 아이콘·종류 이름 + 56 높이 버튼 셋 + ⋯ 자세히(수라는 수라 화면), 흰 카드 안 줄 사이 선(보드 `Record`).
/// 큰 글자·좁은 폭에선 이름 아래로 버튼.
struct QuickRecordCard: View {
    let onTap: (QuickRecord.Row, String) -> Void
    let onDetail: (RecordKind) -> Void
    /// 수라 ⋯(수라 화면)을 보일지.
    var mealMore = false
    /// 탕약 줄(소피 다음) — 없으면 줄 없음(보드 `RecordNoMed`).
    var med: MedLine? = nil
    var onGive: (MedSlots.Slot) -> Void = { _ in }
    /// 혈당 줄(탕약 다음, 당뇨 아이만 — 9-1) 숫자 칸.
    var glucose: Binding<String>? = nil
    var glucoseFocused: FocusState<Bool>.Binding? = nil
    var onLeaveGlucose: () -> Void = {}
    /// 인슐린 줄(혈당 다음 — 9-2).
    var insulin: InsulinLine? = nil
    var onInsulin: (InsulinRules.Slot, InsulinRules.Status) -> Void = { _, _ in }
    var onInsulinEdit: (Record) -> Void = { _ in }
    var onInsulinSetup: () -> Void = {}
    @ScaledMetric(relativeTo: .body) private var buttonHeight: CGFloat = 44 // 보드 56 → 44(2026-11-05 사용자 — 너무 높음, 누르는 크기 최소)
    @ScaledMetric(relativeTo: .body) private var narrowLabel: CGFloat = 62
    @ScaledMetric(relativeTo: .body) private var wideLabel: CGFloat = 70
    /// 인슐린 줄이 있으면 줄 이름 칸을 모두 70으로('인슐린'이 들어가게 — 보드).
    private var labelWidth: CGFloat { insulin == nil ? narrowLabel : wideLabel }
    @ScaledMetric(relativeTo: .body) private var moreSize: CGFloat = 44

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(QuickRecord.rows.enumerated()), id: \.element.kind) { index, row in
                if index > 0 { OkcheColor.line.frame(height: 1) }
                if row.kind == .meal, let med {
                    medRow(med)
                    OkcheColor.line.frame(height: 1)
                }
                if row.kind == .meal, let glucose, let glucoseFocused {
                    GlucoseRow(text: glucose, focused: glucoseFocused, wide: insulin != nil, onLeave: onLeaveGlucose) { onDetail(.glucose) }
                    OkcheColor.line.frame(height: 1)
                }
                if row.kind == .meal, let insulin {
                    InsulinRow(line: insulin, onGive: onInsulin, onEdit: onInsulinEdit, onDetail: { onDetail(.insulin) }, onSetup: onInsulinSetup)
                    OkcheColor.line.frame(height: 1)
                }
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 6) {
                        label(row).frame(width: labelWidth, alignment: .leading)
                        HStack(spacing: 6) { buttons(row) }
                        Spacer(minLength: 0)
                        more(row.kind)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            label(row)
                            Spacer(minLength: 6)
                            more(row.kind)
                        }
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: 6) { buttons(row) }
                            VStack(alignment: .leading, spacing: 6) { buttons(row) }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, 10)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
    }

    private func label(_ row: QuickRecord.Row) -> some View {
        HStack(spacing: 4) {
            Image(systemName: Self.icon[row.kind] ?? "circle").font(.body).frame(minWidth: 22).accessibilityHidden(true)
            Text(QuickRecord.name(row.kind.rawValue)).okcheText(OkcheType.body, weight: .bold).fixedSize()
        }
        .foregroundStyle(OkcheColor.namsaekDeep)
        .accessibilityAddTraits(.isHeader)
    }

    /// ⋯ 자세히 남기기(점선 44) — 자세히 시트가 있는 종류만.
    @ViewBuilder private func more(_ kind: RecordKind) -> some View {
        if !RecordDetail.fields(kind).isEmpty || (kind == .meal && mealMore) || kind == .med {
            Button { onDetail(kind) } label: {
                Image(systemName: "ellipsis").font(.body.weight(.bold)).foregroundStyle(OkcheColor.namsaek)
                    .frame(width: min(moreSize, 72), height: min(moreSize, 72)) // 큰 글자에서 지나치게 커지지 않게
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                        .strokeBorder(OkcheColor.lineStrong, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(QuickRecord.name(kind.rawValue)) 자세히 남기기")
        }
    }

    /// 탕약 줄: 이름 + 버튼(자리가 모자라면 뒤 버튼은 그리지 않음 — 말줄임 없음) + ⋯ 오늘 먹일 약 시트(8-3b).
    private func medRow(_ line: MedLine) -> some View {
        let label = HStack(spacing: 4) {
            Image(systemName: "pill").font(.body).frame(minWidth: 22).accessibilityHidden(true)
            Text("탕약").okcheText(OkcheType.body, weight: .bold).fixedSize()
        }
        .foregroundStyle(OkcheColor.namsaekDeep)
        .accessibilityAddTraits(.isHeader)
        // 큰 글자·좁은 폭에선 다른 줄처럼 이름 아래로 버튼.
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 6) {
                label.frame(width: labelWidth, alignment: .leading)
                medContent(line)
                Spacer(minLength: 0)
                more(.med)
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    label
                    Spacer(minLength: 6)
                    more(.med)
                }
                medContent(line)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 10)
    }

    @ViewBuilder private func medContent(_ line: MedLine) -> some View {
        if line.checked == nil && line.buttons.isEmpty {
            Text("오늘 줄 약을 다 줬어요").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                .frame(minHeight: buttonHeight)
        } else {
            ViewThatFits(in: .horizontal) {
                ForEach((1...max(1, line.buttons.count + (line.checked == nil ? 0 : 1))).reversed(), id: \.self) { n in
                    HStack(spacing: 6) { medButtons(line, count: n) }
                }
            }
        }
    }

    @ViewBuilder private func medButtons(_ line: MedLine, count: Int) -> some View {
        if let checked = line.checked {
            Text(checked.title).okcheText(OkcheType.body, weight: .bold).foregroundStyle(.white)
                .fixedSize()
                .padding(.horizontal, 8)
                .frame(minWidth: 52, minHeight: buttonHeight)
                .background(OkcheColor.namsaek, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                .accessibilityLabel("탕약 \(checked.name) 줬어요 · 기록됨")
        }
        ForEach(Array(line.buttons.prefix(count - (line.checked == nil ? 0 : 1))), id: \.slot.key) { b in
            Button { onGive(b.slot) } label: {
                Text(b.title).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                    .fixedSize()
                    .padding(.horizontal, 8)
                    .frame(minWidth: 52, minHeight: buttonHeight)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd).strokeBorder(OkcheColor.lineStrong, lineWidth: 1.5))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(b.label)
        }
    }

    /// 디자인 아이콘(물방울·알약·물방울 둘·그릇)에 가까운 SF Symbols.
    private static let icon: [RecordKind: String] = [.vomit: "drop", .stool: "capsule", .urine: "drop.halffull", .meal: "fork.knife"]

    @ViewBuilder private func buttons(_ row: QuickRecord.Row) -> some View {
        ForEach(row.choices, id: \.value) { choice in
            Button { onTap(row, choice.value) } label: {
                Text(choice.title).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                    .fixedSize()
                    .padding(.horizontal, 8)
                    .frame(minWidth: 52, minHeight: buttonHeight)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd).strokeBorder(OkcheColor.lineStrong, lineWidth: 1.5))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(QuickRecord.name(row.kind.rawValue)) \(choice.title) · 바로 기록")
        }
    }
}

/// 방금 남긴 기록: 오늘 이 기기에서 가장 늦게 남긴 기록 하나, 누른 직후에만 '아뢰었사옵니다'. 오늘 기록이 없으면 숨김.
struct RecentRecordSection: View {
    let justSaved: UUID?
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    // shortcut: 지우지 않은 기록 전체를 읽어 화면에서 거름, 기록이 수천 건이 되어 느려지면 `@Query`에 날 조건을 넣는다.
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }, sort: \Record.createdAt, order: .reverse) private var records: [Record]
    @State private var deleting: Record?
    @State private var editing: DetailTarget?

    var body: some View {
        // shortcut: 켜 둔 채 6시를 넘겨도 다시 그리기 전까지 어제가 오늘로 보임, 문안 탭(7-0)에서 날 바뀜을 함께 다룬다.
        let today = OkcheDay.of(.now)
        if let latest = QuickRecord.latest(records, today: today) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("방금 남긴 기록").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 8)
                    NavigationLink(value: RecordRoute.log) {
                        Text("오늘 일기 전체").okcheText(OkcheType.label).foregroundStyle(OkcheColor.namsaek).frame(minHeight: 44)
                    }
                }
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 0) {
                        (Text(QuickRecord.time(latest)).foregroundColor(OkcheColor.inkMuted)
                            + Text("  \(RecordText.title(latest, pets: pets)) \(QuickRecord.value(latest))").foregroundColor(OkcheColor.ink))
                            .okcheText(OkcheType.body)
                        if latest.id == justSaved {
                            Text("아뢰었사옵니다").font(.custom(OkcheFontFamily.display.fontName ?? "", size: 14, relativeTo: .subheadline))
                                .foregroundStyle(OkcheColor.geumDeep)
                        }
                    }
                    Spacer(minLength: 0)
                    EditRecordButton(record: latest, pets: pets, color: OkcheColor.namsaek) { editing = $0 }
                    RowIconButton(label: "\(QuickRecord.time(latest)) \(RecordText.speech(latest, pets: pets)) 기록 지우기",
                                  color: OkcheColor.inkMuted) { deleting = latest }
                }
                .padding(.leading, 14)
                .padding(.trailing, 6)
                .padding(.vertical, 2)
                .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            }
            .deleteRecordDialog($deleting, pets: pets)
            .sheet(item: $editing) { DetailSheet(target: $0) }
        }
    }
}

/// 기록 글자: '콩이 · 매화'(이름 + 종류, 누군지 모르면 '누군지 모름').
enum RecordText {
    static func petName(_ record: Record, pets: [Pet]) -> String {
        record.petId.flatMap { id in pets.first { $0.id == id }?.name } ?? "누군지 모름"
    }

    static func title(_ record: Record, pets: [Pet]) -> String {
        "\(petName(record, pets: pets)) · \(QuickRecord.name(record.kind))"
    }

    /// 화면 읽기 이름에 쓰는 '콩이 매화'(누군지 모르면 종류만 — 보드 `Log`).
    static func speech(_ record: Record, pets: [Pet]) -> String {
        record.petId == nil ? QuickRecord.name(record.kind) : "\(petName(record, pets: pets)) \(QuickRecord.name(record.kind))"
    }
}

/// 기록 줄 아이콘 버튼(44): 지우기(휴지통)·고치기(연필).
struct RowIconButton: View {
    var icon = "trash"
    let label: String
    var color = OkcheColor.ink
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon).font(.body).foregroundStyle(color)
                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel(label)
    }
}

/// 고치기(연필) — 자세히 시트가 있는 종류, 수라는 그 아이 수라 화면(2026-11-04 사용자 — 누군지 모르는 수라는 먼저 누구였는지 정하기).
struct EditRecordButton: View {
    let record: Record
    let pets: [Pet]
    var color = OkcheColor.ink
    let onEdit: (DetailTarget) -> Void

    var body: some View {
        if record.kind == RecordKind.meal.rawValue, let petId = record.petId,
           pets.contains(where: { $0.id == petId && $0.memorializedAt == nil }) {
            NavigationLink(value: RecordRoute.sura(petId)) {
                Image(systemName: "pencil").font(.body).foregroundStyle(color)
                    .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel("\(QuickRecord.time(record)) \(RecordText.speech(record, pets: pets)) 기록 고치기")
        } else if record.kind == RecordKind.snack.rawValue, let petId = record.petId,
                  let pet = pets.first(where: { $0.id == petId && $0.memorializedAt == nil }) {
            // 다과는 다과 기록 시트(7-4b).
            SnackEditButton(record: record, pet: pet, color: color,
                            label: "\(QuickRecord.time(record)) \(RecordText.speech(record, pets: pets)) 기록 고치기")
        } else if record.kind == RecordKind.med.rawValue, let petId = record.petId,
                  let pet = pets.first(where: { $0.id == petId && $0.memorializedAt == nil }) {
            // 탕약은 기록 하나 고치기 시트(8-3b — 약은 고정).
            MedEditButton(record: record, pet: pet, color: color,
                          label: "\(QuickRecord.time(record)) \(RecordText.speech(record, pets: pets)) 기록 고치기")
        } else if let kind = RecordKind(rawValue: record.kind), !RecordDetail.fields(kind).isEmpty || kind == .glucose || kind == .insulin {
            RowIconButton(icon: "pencil", label: "\(QuickRecord.time(record)) \(RecordText.speech(record, pets: pets)) 기록 고치기", color: color) {
                onEdit(DetailTarget(kind: kind, editing: record))
            }
        }
    }
}

extension View {
    /// 기록 지우기 확인 창(보드 `DeleteConfirm`): 지우면 `deletedAt`·`data` 비움.
    func deleteRecordDialog(_ record: Binding<Record?>, pets: [Pet]) -> some View {
        modifier(DeleteRecordDialog(record: record, pets: pets))
    }
}

private struct DeleteRecordDialog: ViewModifier {
    @Binding var record: Record?
    let pets: [Pet]
    @Environment(\.modelContext) private var context
    @Environment(\.showUndo) private var showUndo
    @State private var failed = false

    func body(content: Content) -> some View {
        content.fullScreenCover(item: $record) { target in
            OkcheConfirmDialog(title: "이 기록을 지울까요?", destructive: true, confirm: "지우기", confirmLook: .danger,
                               onCancel: { record = nil }, onConfirm: {
                                   do {
                                       // 되돌리기에 쓸 내용(지우면 data·slotKey를 비움 — 1-4).
                                       let kept = (data: target.data, slotKey: target.slotKey)
                                       try QuickRecord.delete(target, in: context)
                                       record = nil
                                       showUndo(.removed("기록을 지웠어요", show: showUndo) {
                                           try QuickRecord.restore(target, data: kept.data, slotKey: kept.slotKey, in: context)
                                       })
                                   } catch {
                                       failed = true
                                       AccessibilityNotification.Announcement("지우지 못했어요. 다시 해 주세요.").post()
                                   }
                               }) {
                // 보드 `DeleteConfirm`: 기록 요약 칸이 안내 글보다 위.
                Text("\(QuickRecord.time(target)) · \(RecordText.title(target, pets: pets)) \(QuickRecord.value(target))")
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 12).padding(.horizontal, 14)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                Text("지운 기록은 실록 통계에서도 빠져요.").okcheText(OkcheType.label, weight: .regular)
                    .foregroundStyle(OkcheColor.inkMuted)
                OkcheFieldError(message: failed ? "지우지 못했어요. 다시 해 주세요." : nil)
            }
            .onAppear { failed = false }
            .presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = record != nil }
    }
}

/// 기록 목록(보드 `Log`·`LogEmpty`): 날짜 이동, '누구 기록' 칩, 그날 요약, 기록 줄(고치기·지우기·누구였는지 정하기). 수라 고치기는 7-3.
struct RecordLogView: View {
    @Environment(\.dismiss) private var dismiss
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }, sort: \Record.occurredAt, order: .reverse) private var allRecords: [Record]
    @State private var day = OkcheDay.of(.now)
    /// '누구 기록' 칩: ''이면 모두.
    @State private var filter: String? = ""
    @State private var deleting: Record?
    @State private var assigning: Record?
    @State private var editing: DetailTarget?

    var body: some View {
        let today = OkcheDay.of(.now)
        let records = QuickRecord.records(allRecords, day: day, petId: filter.flatMap(UUID.init(uuidString:)))
        VStack(spacing: 0) {
            FormHeader(title: "승정원일기", note: "기록 목록", icon: "chevron.left", iconLabel: "뒤로") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    dateBar(today: today)
                    OkcheChoiceChips(options: [("", "모두")] + PetTitle.ordered(pets).map { ($0.id.uuidString, $0.name) },
                                     selection: $filter, allowsNone: false)
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel("누구 기록")
                    if let summary = QuickRecord.summary(records) {
                        Text(summary).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    }
                    if records.isEmpty {
                        Text("이날 남긴 기록이 없어요.").okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    } else {
                        VStack(spacing: 8) {
                            ForEach(records) { record in row(record) }
                        }
                    }
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.bottom, OkcheSpacing.space6)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .deleteRecordDialog($deleting, pets: pets)
        .sheet(item: $assigning) { record in AssignSheet(record: record) }
        .sheet(item: $editing) { DetailSheet(target: $0) }
    }

    /// 날짜 이동: 전날·다음 날(오늘에서 다음 날은 안 눌림), 6시 기준 날.
    private func dateBar(today: String) -> some View {
        HStack {
            Button { day = QuickRecord.shift(day, by: -1) } label: {
                Image(systemName: "chevron.left").frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel("전날")
            Spacer(minLength: 4)
            Text(QuickRecord.dayTitle(day, today: today)).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 4)
            Button { day = QuickRecord.shift(day, by: 1) } label: {
                Image(systemName: "chevron.right").frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .disabled(day >= today)
            .opacity(day >= today ? 0.35 : 1)
            .accessibilityLabel("다음 날")
        }
        .foregroundStyle(OkcheColor.ink)
        .padding(4)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
    }

    private func row(_ record: Record) -> some View {
        let unknown = record.petId == nil
        return HStack(spacing: 12) {
            Text(QuickRecord.time(record)).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                .frame(minWidth: 44, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(RecordText.title(record, pets: pets)).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                if unknown {
                    Button("누구였는지 정하기") { assigning = record }
                        .okcheText(OkcheType.label).foregroundStyle(OkcheColor.namsaek)
                        .frame(minHeight: 32)
                } else if !QuickRecord.value(record).isEmpty {
                    Text(QuickRecord.value(record)).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                }
            }
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            EditRecordButton(record: record, pets: pets) { editing = $0 }
            RowIconButton(label: "\(QuickRecord.time(record)) \(RecordText.speech(record, pets: pets)) 기록 지우기") { deleting = record }
        }
        .padding(.leading, 14)
        .padding(.vertical, 10)
        .padding(.trailing, 6)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        .overlay(alignment: .leading) {
            if unknown { UnevenRoundedRectangle(topLeadingRadius: OkcheRadius.radiusMd, bottomLeadingRadius: OkcheRadius.radiusMd).fill(OkcheColor.namsaek).frame(width: 4) }
        }
    }
}

/// 누구였는지 정하기(보드 `Assign`): 기록 요약, 아이들 + 누군지 몰라요 라디오, '정하기'.
struct AssignSheet: View {
    let record: Record
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var allPets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @State private var picked: UUID?
    @State private var failed = false
    private var pets: [Pet] { allPets.filter { $0.memorializedAt == nil } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("누구였나요?").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                Text(summary).okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                VStack(spacing: 8) {
                    ForEach(pets) { pet in
                        RadioRow(title: pet.name, selected: picked == pet.id) { picked = pet.id }
                    }
                    RadioRow(title: "누군지 몰라요", selected: picked == nil) { picked = nil }
                }
                .accessibilityElement(children: .contain)
                .accessibilityLabel("이 기록의 아이")
                Text("정하면 그 아이 실록 통계에 들어가요. 모르는 채로 두면 어느 실록에도 안 들어가요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button("정하기") {
                    do {
                        try QuickRecord.assign(record, to: picked, in: context)
                        dismiss()
                    } catch {
                        failed = true
                        AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
                    }
                }
                .buttonStyle(.okchePrimary)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.vertical, OkcheSpacing.space6)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        // 누군지 모르는 기록이면 지금 보는 아이를 먼저 골라 둔다(보드 `Assign`).
        .onAppear { picked = record.petId ?? PetTitle.current(allPets, lastPetId: lastPetId).flatMap { $0.memorializedAt == nil ? $0.id : nil } }
    }

    /// '오늘 02:14 · 구토 · 사료'
    private var summary: String {
        let day = record.day == OkcheDay.of(.now) ? "오늘" : QuickRecord.dayTitle(record.day, today: "")
        return [ "\(day) \(QuickRecord.time(record))", QuickRecord.name(record.kind), QuickRecord.value(record)]
            .filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

/// 다과 기록 연필 → 같은 다과 기록 시트(값이 채워진 채).
private struct MedEditButton: View {
    let record: Record
    let pet: Pet
    let color: Color
    let label: String
    @State private var editing = false

    var body: some View {
        RowIconButton(icon: "pencil", label: label, color: color) { editing = true }
            .sheet(isPresented: $editing) { MedExtraSheet(pet: pet, editing: record) }
    }
}

private struct SnackEditButton: View {
    let record: Record
    let pet: Pet
    let color: Color
    let label: String
    @State private var editing = false

    var body: some View {
        RowIconButton(icon: "pencil", label: label, color: color) { editing = true }
            .sheet(isPresented: $editing) { SnackRecordSheet(pet: pet, editing: record) }
    }
}
