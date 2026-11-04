import SwiftData
import SwiftUI

/// 수라 화면(보드 `Sura`·`SuraNoPlan`·`Feeder`): 급여 방식, 끼니 시간, 끼니 칸(또는 끼니 없이 '지금 먹은 것'). 지금 보는 아이를 따른다.
/// 사료 이름·다과는 7-4.
struct SuraScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @Query(filter: #Predicate<MealPlan> { $0.deletedAt == nil }) private var plans: [MealPlan]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil && $0.kind == "meal" }, sort: \Record.occurredAt) private var meals: [Record]
    @AppStorage("lastPetId") private var lastPetId = ""
    /// '괜찮아요'를 누른 아이 ID들(이 기기만의 설정 `mealHintDismissed`, 쉼표로 이음).
    @AppStorage("mealHintDismissed") private var hintDismissed = ""
    @State private var editing: MealMode?
    @State private var failed = false
    /// 1분마다 — 켜 둔 채 시간이 지나도 '아직 시간 전'·오늘(6시 기준)·급식기 자동 기록이 따라가게.
    @State private var now = Date.now

    private var pet: Pet? { PetTitle.current(pets, lastPetId: lastPetId).flatMap { $0.memorializedAt == nil ? $0 : nil } }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(title: "수라", note: "식사량", icon: "chevron.left", iconLabel: "뒤로", trailing: AnyView(PetSeal())) { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let pet {
                        content(pet)
                    }
                    OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, 8)
                .padding(.bottom, OkcheSpacing.space6)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .onAppear { try? MealRules.fillFeeder(in: context) }
        .onReceive(Timer.publish(every: 60, on: .main, in: .common).autoconnect()) {
            now = $0
            try? MealRules.fillFeeder(now: $0, in: context)
        }
        .sheet(item: $editing) { mode in
            if let pet { MealTimeSheet(petId: pet.id, mode: mode, plan: plan(pet)) }
        }
    }

    private func plan(_ pet: Pet) -> MealPlan? { MealRules.openPlan(plans.filter { $0.petId == pet.id }) }

    @ViewBuilder private func content(_ pet: Pet) -> some View {
        let plan = plan(pet)
        let mode = plan?.mode ?? .direct
        ModePicker(mode: mode) { picked in
            guard picked != mode else { return }
            if let plan {
                run { try MealRules.savePlan(petId: pet.id, mode: picked, slots: plan.slots, in: context) }
            } else if picked == .feeder {
                editing = .feeder // 급식기는 끼니가 있어야 해서 시간 시트를 먼저 — 저장해야 바뀜
            }
        }
        if let plan {
            PlanRow(plan: plan) { editing = plan.mode }
            // 그날 계획을 바꿨으면 바꾸기 전 끼니는 앞 계획의 차례로 보인다.
            let slots = MealRules.occurrences(plans.filter { $0.petId == pet.id }, day: OkcheDay.of(now))
            if slots.isEmpty {
                // 오늘 처음 정해 끼니가 모두 지났을 때('아직 지나지 않은 끼니부터 적용').
                Text("오늘은 남은 끼니가 없어요. 내일부터 끼니마다 남겨요.").okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
            }
            ForEach(slots, id: \.key) { slot in
                MealCard(slot: slot, now: now, record: meals.first { $0.slotKey == slot.key }) { result in
                    run { try MealRules.pick(result, for: slot, petId: pet.id, in: context) }
                } onLeft: { record, grams in
                    run { try MealRules.setLeft(grams, on: record, in: context) }
                }
            }
        } else {
            noPlan(pet)
        }
        if mode == .direct {
            Text("그램은 넣고 싶을 때만 넣어도 돼요. 평소만큼 먹었는지만 남겨도 실록에서 흐름을 보여 드려요.")
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 12).padding(.horizontal, 14)
                .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        }
    }

    /// 끼니 없음(보드 `SuraNoPlan`): 안내(한 번 — '괜찮아요'면 그 아이는 다시 안 뜸), 지금 먹은 것, 오늘 남긴 수라.
    @ViewBuilder private func noPlan(_ pet: Pet) -> some View {
        let id = pet.id.uuidString.lowercased() // ID 글자는 소문자(5-1)
        if !hintDismissed.split(separator: ",").contains(Substring(id)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("끼니 시간을 정해 둘까요?").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.namsaekDeep)
                    .accessibilityAddTraits(.isHeader)
                Text("정해 두면 아침·저녁처럼 끼니마다 남기고, 실록에서 다 먹은 끼니를 세어 드려요. 정하지 않으면 남긴 때를 먹은 때로 적어요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.namsaekDeep)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { hintButtons(id) }
                    VStack(spacing: 8) { hintButtons(id) }
                }
            }
            .padding(16)
            .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        }
        VStack(alignment: .leading, spacing: 12) {
            Text("지금 먹은 것").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
            HStack(spacing: 8) {
                ForEach(MealCard.results, id: \.value) { result in
                    Button(result.title) { run { try MealRules.pickNow(result.value, petId: pet.id, in: context) } }
                        .okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                        .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd).strokeBorder(OkcheColor.line, lineWidth: 1.5))
                        .accessibilityLabel("지금 \(result.title)")
                }
            }
        }
        .padding(16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        let today = meals.filter { $0.petId == pet.id && $0.day == OkcheDay.of(now) }
        if !today.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Text("오늘 남긴 수라").okcheText(OkcheType.label).foregroundStyle(OkcheColor.inkMuted)
                    .padding(.top, 10).padding(.bottom, 4).accessibilityAddTraits(.isHeader)
                ForEach(Array(today.enumerated()), id: \.element.id) { index, record in
                    if index > 0 { OkcheColor.line.frame(height: 1) }
                    HStack {
                        Text(QuickRecord.time(record)).okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                        Spacer(minLength: 8)
                        Text(QuickRecord.value(record)).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                    }
                    .frame(minHeight: 44)
                    .accessibilityElement(children: .combine)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 6)
            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        }
        Button("끼니 시간 정하기 ›") { editing = .direct }
            .okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
            .frame(minHeight: 44)
    }

    @ViewBuilder private func hintButtons(_ id: String) -> some View {
        Button("끼니 시간 정하기") { editing = .direct }.buttonStyle(.okchePrimary)
        Button("괜찮아요") {
            hintDismissed = (hintDismissed.split(separator: ",").map(String.init) + [id]).joined(separator: ",")
            AccessibilityNotification.Announcement("안내를 닫았어요").post()
        }
        .buttonStyle(.okchePrimary(.outlined))
    }

    private func run(_ action: () throws -> Void) {
        do {
            try action()
            failed = false
        } catch {
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

extension MealMode: Identifiable {
    var id: String { rawValue }
}

/// 급여 방식 고르기(보드 모양 — 연한 남색 판 안 두 칸). 화면 읽기는 고르기 묶음.
struct ModePicker: View {
    let mode: MealMode
    let onPick: (MealMode) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach([(MealMode.direct, "직접 급여"), (.feeder, "급식기 (수라간)")], id: \.0) { value, title in
                let on = value == mode
                Button { onPick(value) } label: {
                    Text(title).okcheText(OkcheType.body, weight: on ? .bold : .medium)
                        .foregroundStyle(on ? OkcheColor.paper : OkcheColor.namsaekDeep)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(on ? OkcheColor.namsaek : Color.clear, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusSm))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .frame(minHeight: 44)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(4)
        .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("급여 방식")
    }
}

/// '끼니 시간 08:00 · 18:00 바꾸기' / '급식기 시간 … 바꾸기' + 급식기 설명.
private struct PlanRow: View {
    let plan: MealPlan
    let onChange: () -> Void

    var body: some View {
        let feeder = plan.mode == .feeder
        let label = feeder ? "급식기 시간" : "끼니 시간"
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                (Text(label + " ").fontWeight(.bold) + Text(plan.slots.map(\.time).joined(separator: " · ")).foregroundColor(OkcheColor.inkMuted))
                    .okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Button("바꾸기", action: onChange)
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.ink).fixedSize()
                    .padding(.horizontal, 12).frame(minHeight: 40)
                    .background(OkcheColor.paper, in: Capsule())
                    .overlay(Capsule().strokeBorder(OkcheColor.lineStrong, lineWidth: 1.5))
                    .frame(minHeight: 44)
                    .accessibilityLabel("\(label) 바꾸기")
            }
            if feeder {
                Text("이 시간이 되면 끼니가 '다 먹음'으로 저절로 기록돼요. 남기거나 안 먹은 끼니만 바꿔 주세요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            }
        }
        .padding(.vertical, 10).padding(.horizontal, 16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
    }
}

/// 끼니 칸: 이름(급식기는 시각)·준 양 + 상태, 다 먹음·남김·안 먹음(기존 고르기 카드), 남김이면 '남은 양 (g, 선택)'.
private struct MealCard: View {
    static let results: [(value: String, title: String)] = [("all", "다 먹음"), ("some", "남김"), ("none", "안 먹음")]

    let slot: MealRules.Occurrence
    let now: Date
    let record: Record?
    let onPick: (String) -> Void
    let onLeft: (Record, Int?) -> Void
    @State private var left = ""
    @State private var leftError: String?

    var body: some View {
        let feeder = slot.mode == .feeder
        let waiting = feeder && slot.at > now && record == nil
        let result = record.flatMap { MealRules.json($0)["result"] as? String }
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                let detail = [feeder ? slot.slot.time : nil, slot.slot.grams.map { "\($0)g" }].compactMap { $0 }.joined(separator: " · ")
                (Text(slot.slot.name) + Text(detail.isEmpty ? "" : " \(detail)").font(.body).fontWeight(.medium).foregroundColor(OkcheColor.inkMuted))
                    .okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                Text(status(feeder: feeder, waiting: waiting)).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            }
            if !waiting {
                OkcheChoiceCards(options: Self.results, isOn: { $0 == result }, onTap: onPick)
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel("\(slot.slot.name) 식사량")
                if result == "some", let record {
                    HStack(spacing: 12) {
                        OkcheFieldLabel(title: "남은 양", note: "(g, 선택)").accessibilityHidden(true)
                        Spacer(minLength: 0)
                        HStack(spacing: 4) {
                            TextField("", text: $left)
                                .okcheText(OkcheType.body).keyboardType(.numberPad)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .accessibilityLabel("\(slot.slot.name) 남은 양 (그램, 선택)")
                                .onSubmit { saveLeft(record) }
                                .onChange(of: left) { saveLeft(record) }
                            Text("g").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted).accessibilityHidden(true)
                        }
                        .okcheFieldBox(isError: leftError != nil)
                        .frame(width: 120)
                    }
                    OkcheFieldError(message: leftError)
                }
            }
        }
        .padding(16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .onAppear { left = stored }
        .onChange(of: result) { left = stored } // 다 먹음 → 남김으로 돌아오면 지운 값이 남지 않게
    }

    private func status(feeder: Bool, waiting: Bool) -> String {
        if waiting { return "아직 시간 전" }
        guard let record else { return "아직 기록 전" }
        return record.auto ? "자동 기록됨" : QuickRecord.time(record)
    }

    private var stored: String { record.flatMap { (MealRules.json($0)["leftGrams"] as? Int).map(String.init) } ?? "" }

    /// 0~2000 정수(준 양이 있으면 그보다 많을 수 없음), 비우면 없음. 잘못된 값은 저장하지 않고 칸 아래에 알림. 기록과 같으면 저장 안 함.
    private func saveLeft(_ record: Record) {
        let text = left.trimmingCharacters(in: .whitespaces)
        guard text != stored else { return leftError = nil }
        let given = MealRules.json(record)["givenGrams"] as? Int
        if text.isEmpty {
            leftError = nil
            onLeft(record, nil)
        } else if let grams = Int(text), (0...(given ?? 2000)).contains(grams) {
            leftError = nil
            onLeft(record, grams)
        } else {
            leftError = given.map { "준 양(\($0)g)보다 많을 수 없어요" } ?? "0에서 2000 사이 숫자로 넣어 주세요"
        }
    }
}

/// 끼니 시간 시트(보드 `SuraMealTime`·`FeederTime`): 끼니 줄(자동 이름·시간·준 양 g·빼기), '+ 끼니 시간 추가', 저장.
struct MealTimeSheet: View {
    let petId: UUID
    let mode: MealMode
    let plan: MealPlan?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var rows: [Row] = []
    @State private var pickingTime: UUID?
    @State private var error: String?

    struct Row: Identifiable {
        let id = UUID()
        var time: String
        var grams: String
    }

    private var feeder: Bool { mode == .feeder }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    (Text(feeder ? "수라간 " : "수라 ") + Text(feeder ? "급식기 시간" : "끼니 시간").font(.body).fontWeight(.medium).foregroundColor(OkcheColor.inkMuted))
                        .okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                Text(feeder ? "급식기에 맞춰 둔 시간을 똑같이 넣어 주세요. 이 시간이 되면 그 끼니가 '다 먹음'으로 기록돼요. 준 양(g)은 넣고 싶을 때만 넣어 주세요."
                            : "주로 먹이는 끼니와 시간을 넣어 주세요. 시간이 조금 달라도 그 앞뒤에 남긴 수라는 그 끼니로 들어가요. 준 양(g)은 넣고 싶을 때만 넣어 주세요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                if !rows.isEmpty {
                    VStack(spacing: 0) {
                        let names = MealRules.names(rows.count)
                        ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                            if index > 0 { OkcheColor.line.frame(height: 1) }
                            rowView(index: index, name: names[index])
                        }
                    }
                    .padding(.horizontal, 16)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                }
                Button("+ 끼니 시간 추가") {
                    rows.append(Row(time: nextFreeTime(), grams: ""))
                    rows.sort { $0.time < $1.time }
                }
                .okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek).frame(minHeight: 44)
                Text(feeder ? "바꾼 시간은 아직 지나지 않은 끼니부터 바로 적용돼요. 지난 기록은 그대로예요."
                            : "바꾼 시간은 아직 지나지 않은 끼니부터 바로 적용돼요. 지난 기록은 그대로예요. 끼니를 모두 빼고 저장하면 끼니 없이 남겨요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.namsaekDeep)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 12).padding(.horizontal, 14)
                    .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                OkcheFieldError(message: error)
                Button("저장하기", action: save).buttonStyle(.okchePrimary)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.vertical, OkcheSpacing.space6)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear {
            rows = (plan?.slots ?? [MealSlot(name: "", time: "08:00"), MealSlot(name: "", time: "18:00")])
                .sorted { $0.time < $1.time }
                .map { Row(time: $0.time, grams: $0.grams.map(String.init) ?? "") }
        }
        .sheet(item: $pickingTime) { id in
            if let index = rows.firstIndex(where: { $0.id == id }) {
                TimeSheet(time: timeBinding(index))
            }
        }
    }

    private func rowView(index: Int, name: String) -> some View {
        let row = rows[index]
        let time = Button { pickingTime = row.id } label: {
            HStack {
                Text(row.time).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                Spacer(minLength: 4)
                Image(systemName: "clock").font(.body).foregroundStyle(OkcheColor.ink)
            }
            .okcheFieldBox()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(name) 시간")
        .accessibilityValue(row.time)
        let grams = HStack(spacing: 4) {
            TextField(text: $rows[index].grams, prompt: Text("양").foregroundStyle(OkcheColor.inkMuted)) { Text("\(name) 준 양") }
                .okcheText(OkcheType.body).keyboardType(.numberPad)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityLabel("\(name) 준 양 (그램)")
            Text("g").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted).accessibilityHidden(true)
        }
        .okcheFieldBox()
        let removable = !(feeder && rows.count == 1)
        let remove = Button { rows.remove(at: index) } label: {
            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.lineStrong)
                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel("\(name) 끼니 빼기")
        .disabled(!removable)
        .opacity(removable ? 1 : 0.4)
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                Text(name).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink).fixedSize()
                Spacer(minLength: 0)
                time.frame(width: 120)
                grams.frame(width: 76)
                remove
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(name).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
                    Spacer(minLength: 0)
                    remove
                }
                time
                grams
            }
        }
        .padding(.vertical, 6)
    }

    /// 새 줄 시각: 12:00부터 한 시간씩 넘겨 아직 없는 시각.
    private func nextFreeTime() -> String {
        let used = Set(rows.map(\.time))
        return (0..<24).map { String(format: "%02d:00", (12 + $0) % 24) }.first { !used.contains($0) } ?? "12:00"
    }

    private func timeBinding(_ index: Int) -> Binding<Date> {
        Binding {
            let parts = rows[index].time.split(separator: ":").compactMap { Int($0) }
            guard parts.count == 2 else { return .now } // 다른 기기에서 온 틀린 글자
            return Calendar.okcheDevice.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: .now) ?? .now
        } set: { date in
            let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: date)
            rows[index].time = String(format: "%02d:%02d", c.hour!, c.minute!)
            rows.sort { $0.time < $1.time }
        }
    }

    /// 시간 겹침 안 됨, 준 양은 0~2000 정수(비우면 없음), 급식기는 끼니 1개 이상.
    private func save() {
        if Set(rows.map(\.time)).count != rows.count {
            return fail("같은 시간의 끼니가 있어요. 시간을 바꿔 주세요.")
        }
        if feeder && rows.isEmpty {
            return fail("급식기는 끼니 시간을 하나 이상 넣어 주세요.")
        }
        var slots: [MealSlot] = []
        for row in rows {
            let text = row.grams.trimmingCharacters(in: .whitespaces)
            let grams = text.isEmpty ? nil : Int(text)
            if !text.isEmpty && !(grams.map { (0...2000).contains($0) } ?? false) {
                return fail("준 양은 0에서 2000 사이 숫자로 넣어 주세요.")
            }
            slots.append(MealSlot(name: "", time: row.time, grams: grams))
        }
        do {
            try MealRules.savePlan(petId: petId, mode: mode, slots: slots, in: context)
            try MealRules.fillFeeder(in: context)
            AccessibilityNotification.Announcement("끼니 시간을 저장했어요").post()
            dismiss()
        } catch {
            fail("저장하지 못했어요. 다시 해 주세요.")
        }
    }

    private func fail(_ message: String) {
        error = message
        AccessibilityNotification.Announcement(message).post()
    }
}
