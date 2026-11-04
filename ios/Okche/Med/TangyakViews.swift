import SwiftData
import SwiftUI

/// 탕약(보드 `Tangyak`·`TangyakEmpty`): 더보기 '탕약 · 약 알림'. 모든 아이의 먹고 있는 약 카드(아이마다 따로), '+ 약 추가'.
/// '다음 탕약' 띠는 8-3, 카드를 눌러 고치기는 8-2b.
struct TangyakView: View {
    @Environment(\.dismiss) private var dismiss
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @Query(filter: #Predicate<MedSchedule> { $0.deletedAt == nil }) private var schedules: [MedSchedule]
    @Query private var meds: [Medication]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }) private var records: [Record]
    @Environment(\.modelContext) private var context
    @State private var failed = false

    var body: some View {
        let cards = MedSchedules.cards(schedules, pets: pets)
        VStack(spacing: 0) {
            FormHeader(title: "탕약", note: "약 · 알림", icon: "chevron.left", iconLabel: "뒤로", trailing: AnyView(PetSeal())) { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if cards.isEmpty {
                        Text("아직 먹이는 약이 없어요. 약을 추가하면 일기 탕약 줄과 알림에 나와요.")
                            .okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                    } else {
                        Text("먹고 있는 약").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                            .accessibilityAddTraits(.isHeader).padding(.top, 4)
                        ForEach(cards, id: \.schedule.id) { card in
                            if let med = meds.first(where: { $0.id == card.schedule.medicationId }) {
                                TangyakCard(schedule: card.schedule, pet: card.pet, med: med,
                                            stock: MedSchedules.stockText(med, schedules: schedules, records: records)) { on in
                                    do {
                                        try MedSchedules.setReminder(card.schedule, on, in: context)
                                        failed = false
                                    } catch {
                                        failed = true
                                        AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
                                    }
                                }
                            }
                        }
                        OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                    }
                    NavigationLink(value: MoreRoute.medAdd) {
                        Text("+ 약 추가").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.okchePrimary)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, 4).padding(.bottom, OkcheSpacing.space6)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
    }
}

/// 탕약 카드: 약 이름 + '콩이 · 이틀에 한 번 · 1알', 알림 스위치(바로 저장), 시간 알약·먹는 날 글자, 약방 줄.
struct TangyakCard: View {
    let schedule: MedSchedule
    let pet: Pet
    let med: Medication
    let stock: String
    let onReminder: (Bool) -> Void

    var body: some View {
        let detail = MedSchedules.cardDetail(schedule, petName: pet.name)
        VStack(alignment: .leading, spacing: 10) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .center) { title(detail); Spacer(minLength: 8); reminderToggle }
                VStack(alignment: .leading, spacing: 4) { title(detail); reminderToggle }
            }
            times
            (Text("약방") + Text(" · \(stock)").font(.footnote).foregroundColor(OkcheColor.inkMuted))
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 10)
                .overlay(alignment: .top) { Rectangle().fill(OkcheColor.line).frame(height: 1) }
        }
        .padding(16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .accessibilityElement(children: .contain)
    }

    private func title(_ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(med.name).okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
            Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private var reminderToggle: some View {
        if schedule.frequency != .asNeeded {
            Toggle(isOn: Binding(get: { schedule.reminder }, set: onReminder)) {
                Text("알림").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.ink)
            }
            .fixedSize()
            .tint(OkcheColor.namsaek)
            .frame(minHeight: 44)
            .accessibilityLabel("\(med.name) \(pet.name) 알림")
        }
    }

    /// 시간 알약 + 먹는 날 글자. 몇 시간마다는 다음 차례, 필요할 때만은 글자만.
    @ViewBuilder private var times: some View {
        switch schedule.frequency {
        case .asNeeded:
            Text("필요할 때만").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        case .everyNHours:
            if let next = MedSchedules.nextDue(schedule) {
                FlowLayout(spacing: 6) {
                    pill(MedSchedules.clock(Self.hm.string(from: next)))
                    Text("다음 \(Self.md.string(from: next))").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                        .padding(.vertical, 4)
                }
            }
        default:
            FlowLayout(spacing: 6) {
                ForEach(schedule.times.sorted(), id: \.self) { pill(MedSchedules.clock($0)) }
                if let text = MedSchedules.doseDayText(schedule, today: OkcheDay.of(.now)) {
                    Text(text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted).padding(.vertical, 4)
                }
            }
        }
    }

    private func pill(_ text: String) -> some View {
        Text(text).okcheText(OkcheType.label).foregroundStyle(OkcheColor.namsaekDeep)
            .padding(.vertical, 4).padding(.horizontal, 10)
            .background(OkcheColor.namsaekSoft, in: Capsule())
    }

    private static let hm: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()

    private static let md: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "M/d"
        return f
    }()
}

/// 약 추가(보드 `MedAdd`·`MedAddWeekday`·`MedAddHours`·`MedAddAsNeeded`): 누가 먹나요(여럿), 약방에서 고르기, 한 번에 얼마나,
/// 얼마나 자주 5가지(주기마다 칸), 알림, 먹이는 방법, '저장하기' — 고른 아이마다 한 줄.
struct MedAddView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @Query private var meds: [Medication]
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }) private var records: [Record]
    @State private var form = MedSchedules.Form()
    @State private var errors: [MedSchedules.Field: String] = [:]
    @State private var started = false
    @State private var adding = false
    @State private var failed = false
    @State private var saving = false
    @State private var editingTime: TimeIndex?
    @State private var pickingStart = false
    @State private var pickingFirst = false

    private var alive: [Pet] { pets.filter { $0.memorializedAt == nil } }
    private var listed: [Medication] { meds.filter { $0.deletedAt == nil }.sorted { $0.createdAt < $1.createdAt } }
    private var unit: DoseUnit { meds.first { $0.id == form.medicationId }?.unit ?? .pill }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(title: "탕약", note: "약 추가", icon: "chevron.left", iconLabel: "탕약으로 돌아가기") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    who
                    medPicker
                    OkcheTextField(label: "한 번에 얼마나", text: field(\.dose, .dose), unit: MedRules.unitTitle(unit), keyboard: .decimalPad,
                                   error: errors[.dose])
                        .frame(maxWidth: 160, alignment: .leading)
                    frequency
                    OkcheMenuPicker(label: "먹이는 방법", note: "(선택)", options: MedSchedules.howTos, selection: $form.howTo, inline: false)
                    OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                    Button("저장하기", action: save).buttonStyle(.okchePrimary)
                }
                .padding(.horizontal, OkcheSpacing.space4)
                .padding(.top, 8).padding(.bottom, OkcheSpacing.space6)
            }
        }
        .background(OkcheColor.hanji)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .onAppear(perform: start)
        .sheet(isPresented: $adding) {
            MedFormSheet(mode: .addAndPick) { med in pick(med) }
        }
        .sheet(item: $editingTime) { index in
            TimeSheet(time: timeBinding(index.id), onRemove: form.times.count > 1 ? { form.times.remove(at: index.id) } : nil)
        }
        .sheet(isPresented: $pickingStart) {
            TimeSheet(time: $form.startDate, components: .date)
        }
        .sheet(isPresented: $pickingFirst) {
            TimeSheet(time: $form.firstAt, components: [.date, .hourAndMinute])
        }
    }

    // MARK: 칸

    private var who: some View {
        VStack(alignment: .leading, spacing: 8) {
            OkcheFieldLabel(title: "누가 먹나요?", note: "(여럿 고를 수 있어요)").accessibilityAddTraits(.isHeader)
            OkcheChoiceChips(options: alive.map { ($0.id, $0.name) }, multiple: true, isOn: { form.petIds.contains($0) }) { id in
                if let i = form.petIds.firstIndex(of: id) { form.petIds.remove(at: i) } else { form.petIds.append(id) }
                errors[.pets] = nil
            }
            note("여럿 고르면 아이마다 따로 만들어져요. 양·시간은 나중에 아이마다 고칠 수 있어요.")
            OkcheFieldError(message: errors[.pets])
        }
    }

    private var medPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            OkcheFieldLabel(title: "약방에서 고르기").accessibilityAddTraits(.isHeader)
            ForEach(listed) { med in
                let left = MedRules.remaining(med, records: records).map { "남은 \(MedRules.number(max($0, 0)))\(MedRules.unitTitle(med.unit))" }
                SnackPickCard(name: med.name, detail: ([MedRules.formTitle(med.form)] + [left].compactMap { $0 }).joined(separator: " · "),
                              isOn: form.medicationId == med.id) { pick(med) }
            }
            Button { adding = true } label: {
                Text("+ 약방에 새 약 들이기").okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.namsaek)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            OkcheFieldError(message: errors[.med])
        }
    }

    @ViewBuilder private var frequency: some View {
        VStack(alignment: .leading, spacing: 10) {
            FieldGroup(title: "얼마나 자주") {
                OkcheChoiceChips(options: MedSchedules.frequencies, selection: Binding(get: { form.frequency }, set: { form.frequency = $0 ?? form.frequency }),
                                 allowsNone: false)
            }
            switch form.frequency {
            case .everyNDays:
                HStack(alignment: .top, spacing: 8) {
                    OkcheTextField(label: "간격", text: field(\.everyDays, .everyDays), unit: "일마다", keyboard: .numberPad, error: errors[.everyDays])
                        .frame(width: 150)
                    dateButton("시작하는 날", Self.day.string(from: form.startDate)) { pickingStart = true }
                }
                note("이틀에 한 번이면 2, 일주일에 한 번이면 7. 먹는 날에만 일기 탕약 줄과 알림에 나와요.")
            case .weekdays:
                FieldGroup(title: "먹는 요일") {
                    OkcheChoiceChips(options: (1...7).map { ($0, MedSchedules.weekdayNames[$0 - 1]) }, multiple: true,
                                     isOn: { form.weekdays.contains($0) }) { d in
                        if let i = form.weekdays.firstIndex(of: d) { form.weekdays.remove(at: i) } else { form.weekdays.append(d) }
                        errors[.weekdays] = nil
                    }
                }
                OkcheFieldError(message: errors[.weekdays])
                note(weekdayNote)
            case .everyNHours:
                HStack(alignment: .top, spacing: 8) {
                    OkcheTextField(label: "간격", text: field(\.everyHours, .everyHours), unit: "시간마다", keyboard: .numberPad, error: errors[.everyHours])
                        .frame(width: 160)
                    dateButton("처음 먹일 때", Self.dateTime.string(from: form.firstAt)) { pickingFirst = true }
                }
                if let hours = PetRegistration.wholeNumber(form.everyHours), hours >= 1 {
                    HoursPreview(lines: MedSchedules.hoursPreview(firstAt: form.firstAt, everyHours: hours))
                }
                note("시간이 날마다 바뀌어서 먹일 시간은 따로 정하지 않아요.")
                FieldGroup(title: "늦게 줬을 때 다음 차례는") {
                    OkcheChoiceCards(options: [(true, "준 시각부터 다시 세기"), (false, "처음 정한 시간대로")], selection: $form.shiftOnLate)
                }
                note(form.shiftOnLate ? "늦게 준 만큼 다음 차례도 밀려요." : "늦게 줘도 다음 차례 시간은 그대로예요.")
            case .asNeeded:
                note("필요할 때만 주는 약은 일기 탕약 줄과 알림에 나오지 않아요. 줄 때 오늘 먹일 약 시트의 '+ 예정에 없던 약 남기기'로 남겨요.")
            case .daily:
                EmptyView()
            }
        }
        if form.frequency != .asNeeded {
            VStack(alignment: .leading, spacing: 8) {
                if form.frequency != .everyNHours {
                    OkcheFieldLabel(title: form.frequency == .daily ? "먹일 시간" : "먹는 날 먹일 시간").accessibilityAddTraits(.isHeader)
                    TimeFieldsRow(times: form.times, onTap: { editingTime = TimeIndex(id: $0) }, onAdd: addTime)
                    OkcheFieldError(message: errors[.times])
                }
                OkcheSwitchRow(title: form.frequency == .everyNHours ? "차례마다 알림 보내기" : "시간마다 알림 보내기", isOn: $form.reminder)
                OkcheMenuPicker(label: "알림 보낼 때", options: MedSchedules.leadChoices.map { ($0.minutes, $0.title) }, selection: $form.leadMinutes)
            }
        }
    }

    private var weekdayNote: String {
        let picked = form.weekdays.sorted().map { MedSchedules.weekdayNames[$0 - 1] }
        let now = picked.isEmpty ? "" : " 지금은 \(picked.joined(separator: "·")), 일주일에 \(["", "한", "두", "세", "네", "다섯", "여섯", "일곱"][picked.count]) 번."
        return "고른 요일에만 일기 탕약 줄과 알림에 나와요.\(now)"
    }

    private func dateButton(_ label: String, _ value: String, action: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            OkcheFieldLabel(title: label).accessibilityHidden(true)
            Button(action: action) {
                HStack {
                    Text(value).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                    Spacer(minLength: 4)
                    Image(systemName: "calendar").font(.body).foregroundStyle(OkcheColor.ink)
                }
                .okcheFieldBox()
            }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
            .accessibilityValue(value)
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
    }

    private func field(_ key: WritableKeyPath<MedSchedules.Form, String>, _ error: MedSchedules.Field) -> Binding<String> {
        Binding(get: { form[keyPath: key] }, set: { form[keyPath: key] = $0; errors[error] = nil })
    }

    // MARK: 동작

    /// 처음 값: 지금 보는 아이(추억이면 처음 모신 살아 있는 아이), 시작하는 날 오늘(6시 기준), 처음 먹일 때 지금 분 올림.
    private func start() {
        guard !started else { return }
        started = true
        // 지금 보는 아이는 열 때 한 번만 읽는다 — `@AppStorage`를 두면 앞 화면과 서로 다시 그리며 멈춤(2026-11-04 확인).
        let current = PetTitle.current(pets, lastPetId: UserDefaults.standard.string(forKey: "lastPetId") ?? "")
        if let id = (current?.memorializedAt == nil ? current : alive.first)?.id { form.petIds = [id] }
        form.startDate = PetRegistration.date(OkcheDay.of(.now), .okcheDevice) ?? .now
        form.firstAt = MedSchedules.roundedUp(.now)
    }

    private func pick(_ med: Medication) {
        form.medicationId = med.id
        form.dose = MedRules.number(med.doseAmount) // 그 약의 '한 번에'
        errors[.med] = nil
        errors[.dose] = nil
    }

    private func addTime() {
        let used = Set(form.times)
        // 보드처럼 08:00 다음은 21:00, 그 뒤로는 비어 있는 정시.
        let candidates = ["08:00", "21:00"] + (0..<24).map { String(format: "%02d:00", (12 + $0) % 24) }
        if let next = candidates.first(where: { !used.contains($0) }) {
            form.times.append(next)
            errors[.times] = nil
        }
    }

    private func timeBinding(_ index: Int) -> Binding<Date> {
        Binding(get: {
            let p = form.times.indices.contains(index) ? form.times[index].split(separator: ":").compactMap { Int($0) } : [8, 0]
            return Calendar.okcheDevice.date(bySettingHour: p.first ?? 8, minute: p.last ?? 0, second: 0, of: .now) ?? .now
        }, set: { date in
            let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: date)
            let text = String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
            guard form.times.indices.contains(index) else { return }
            // 같은 시간이 이미 있으면 그 칸을 빼서 겹치지 않게.
            if let dup = form.times.firstIndex(of: text), dup != index { form.times.remove(at: index) } else { form.times[index] = text }
        })
    }

    private func save() {
        guard !saving else { return }
        errors = MedSchedules.errors(form)
        guard errors.isEmpty else {
            let first = [MedSchedules.Field.pets, .med, .dose, .everyDays, .weekdays, .everyHours, .times].lazy.compactMap { errors[$0] }.first
            AccessibilityNotification.Announcement(first ?? "").post()
            return
        }
        saving = true
        do {
            try MedSchedules.add(form, unit: unit, in: context)
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }

    private struct TimeIndex: Identifiable { let id: Int }

    private static let day: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일"
        return f
    }()

    private static let dateTime: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M/d a h:mm"
        return f
    }()
}

/// 먹일 시간 칸 줄: 시간 칸 여러 개 옆으로(넘치면 다음 줄) + 점선 '+'. 칸을 누르면 시간 고르기(빼기 포함).
struct TimeFieldsRow: View {
    let times: [String]
    let onTap: (Int) -> Void
    let onAdd: () -> Void

    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(Array(times.enumerated()), id: \.offset) { index, time in
                Button { onTap(index) } label: {
                    Text(MedSchedules.clock(time)).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                        .frame(minWidth: 112, alignment: .leading)
                        .okcheFieldBox()
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(InsulinSlotsEditor.ordinal(index + 1)) 시간")
                .accessibilityValue(MedSchedules.clock(time))
            }
            Button(action: onAdd) {
                Text("+").okcheText(OkcheType.heading, weight: .regular).foregroundStyle(OkcheColor.namsaek)
                    .frame(width: 48, height: 52)
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                        .strokeBorder(OkcheColor.namsaek, style: StrokeStyle(lineWidth: 1.5, dash: [6, 4])))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("시간 하나 더")
        }
    }
}

/// '이렇게 돌아가요': 몇 시간마다의 다음 몇 번.
struct HoursPreview: View {
    let lines: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("이렇게 돌아가요").okcheText(OkcheType.label).foregroundStyle(OkcheColor.inkMuted).accessibilityAddTraits(.isHeader)
            ForEach(lines, id: \.self) { Text($0).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        .accessibilityElement(children: .combine)
    }
}
