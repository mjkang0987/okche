import SwiftUI

// 아이 등록(6-1)과 아이 정보 고치기·인슐린 설정(6-2)이 함께 쓰는 입력 조각.

/// 인슐린 안내의 출처 줄(안내 문구 규칙 — 2026-11-01 정기 점검 반영).
let diabetesSourceNote = "출처: 미국 동물병원협회 「2026 AAHA Diabetes Management Guidelines for Cats」·「2026 AAHA Diabetes Management Guidelines for Dogs」. 위 내용은 이 단체의 권고사항이고, 아이마다 다를 수 있어요."

/// 출처 줄(12, 흐린 글자).
struct SourceNote: View {
    let text: String

    var body: some View {
        Text(text).okcheText(OkcheType.caption, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
    }
}

/// 이름표 + 내용. 이름표는 화면 읽기에서 머리말.
struct FieldGroup<Content: View>: View {
    let title: String
    var note: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            OkcheFieldLabel(title: title, note: note).accessibilityAddTraits(.isHeader)
            content
        }
    }
}

/// 종·품종·털색·성별·중성화 (보드 `Profile`·`PetEdit`).
/// 종을 바꿀 때 품종·털색을 되돌리는 건 바인딩 쪽 모델(`PetRegistration`·`PetEditing`)의 `didSet`이 한다.
struct PetIdentityFields: View {
    @Binding var species: Species
    @Binding var breed: String
    @Binding var furColor: String
    @Binding var sex: Sex?
    @Binding var neutered: Bool?

    var body: some View {
        FieldGroup(title: "종") {
            OkcheChoiceCards(options: [(.cat, "고양이"), (.dog, "강아지")], selection: $species)
        }
        breedPicker
        furPicker
        FieldGroup(title: "성별", note: "(선택)") {
            OkcheChoiceChips(options: [(Sex.male, "남아"), (.female, "여아")], selection: $sex)
        }
        FieldGroup(title: "중성화", note: "(선택)") {
            OkcheChoiceChips(options: [(true, "했어요"), (false, "안 했어요")], selection: $neutered)
        }
    }

    private var breedPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            OkcheFieldLabel(title: "품종").accessibilityHidden(true)
            Menu {
                Picker("품종", selection: $breed) {
                    // 목록에 없는 글자(목록이 바뀌기 전에 저장한 품종)도 고른 채 보이게.
                    let breeds = PetCatalog.breeds(species)
                    ForEach(breeds.contains(breed) ? breeds : [breed] + breeds, id: \.self) { Text($0).tag($0) }
                }
            } label: {
                HStack {
                    Text(breed).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).multilineTextAlignment(.leading)
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.down").font(.body.weight(.medium)).foregroundStyle(OkcheColor.inkMuted)
                }
                .okcheFieldBox()
            }
            .accessibilityLabel("품종")
            .accessibilityValue(breed)
        }
    }

    private var furPicker: some View {
        FieldGroup(title: "털색") {
            // 큰 글자에서 이름이 꺾이면 두 줄로.
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 12) { furButtons(PetCatalog.furColors(species)) }
                VStack(alignment: .leading, spacing: 12) {
                    let furs = PetCatalog.furColors(species)
                    HStack(alignment: .top, spacing: 12) { furButtons(Array(furs.prefix(2))) }
                    HStack(alignment: .top, spacing: 12) { furButtons(Array(furs.suffix(2))) }
                }
            }
        }
    }

    private func furButtons(_ furs: [PetCatalog.FurColor]) -> some View {
        ForEach(furs, id: \.self) { fur in
            let on = fur.name == furColor
            Button { furColor = fur.name } label: {
                VStack(spacing: 4) {
                    Circle().fill(fur.color)
                        .overlay(Circle().strokeBorder(fur.outlined ? OkcheColor.lineStrong : .clear, lineWidth: 1.5))
                        .frame(width: 48, height: 48)
                        .padding(5)
                        .overlay(Circle().strokeBorder(on ? OkcheColor.namsaek : .clear, lineWidth: 2))
                        .padding(-5)
                    Text(fur.name).okcheText(OkcheType.label, weight: on ? .bold : .regular).foregroundStyle(OkcheColor.ink)
                        .fixedSize()
                }
                .frame(minWidth: 48)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(fur.name)
            .accessibilityAddTraits(on ? .isSelected : [])
        }
    }
}

/// 생일 또는 '생일을 몰라요' + 나이 (대략).
struct BirthFields: View {
    @Binding var birthDate: Date?
    @Binding var birthUnknown: Bool
    @Binding var approxAge: String
    let birthError: String?
    let ageError: String?
    var focused: FocusState<PetRegistration.Field?>.Binding

    static let ageHelp = "오늘에서 그만큼 거슬러 간 날을 생일로 잡아요. 1살이 안 됐으면 0을 넣어 주세요."

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if birthUnknown {
                OkcheTextField(label: "나이", note: "(대략)", text: $approxAge, unit: "살쯤", keyboard: .numberPad, error: ageError,
                               hint: Self.ageHelp)
                    .focused(focused, equals: .age).id(PetRegistration.Field.age)
            } else {
                DateField(label: "생일", date: $birthDate, error: birthError).id(PetRegistration.Field.birth)
            }
            Toggle("생일을 몰라요 (나이를 어림잡아 넣을게요)", isOn: $birthUnknown)
                .toggleStyle(CheckboxStyle())
            if birthUnknown {
                Text(Self.ageHelp)
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    .accessibilityHidden(true) // 나이 칸의 힌트로 읽는다
            }
        }
    }
}

/// 날짜 칸: 누르면 달력 시트. 비울 수 있다('지우기').
struct DateField: View {
    let label: String
    @Binding var date: Date?
    let error: String?
    @State private var picking = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            OkcheFieldLabel(title: label).accessibilityHidden(true)
            Button { picking = true } label: {
                HStack {
                    Text(date.map { $0.formatted(.dateTime.year().month().day()) } ?? "날짜를 골라 주세요")
                        .okcheText(OkcheType.body)
                        .foregroundStyle(date == nil ? OkcheColor.inkMuted : OkcheColor.ink)
                    Spacer(minLength: 8)
                    Image(systemName: "calendar").font(.body).foregroundStyle(OkcheColor.ink)
                }
                .okcheFieldBox(isError: error != nil)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
            .accessibilityValue(date.map { $0.formatted(date: .long, time: .omitted) } ?? "안 고름")
            OkcheFieldError(message: error)
        }
        .sheet(isPresented: $picking) { DateSheet(title: label, date: $date) }
    }
}

/// 날짜 고르기: 달력에서 고른 뒤 '고르기'. 오늘 뒤는 고를 수 없다.
private struct DateSheet: View {
    let title: String
    @Binding var date: Date?
    @Environment(\.dismiss) private var dismiss
    @State private var picked = Date.now

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Button("취소") { dismiss() }
                Spacer()
                if date != nil { Button("지우기") { date = nil; dismiss() } }
            }
            .okcheText(OkcheType.body, weight: .medium)
            .foregroundStyle(OkcheColor.namsaek)
            .frame(minHeight: 44)
            DatePicker(title, selection: $picked, in: ...Date.now, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(OkcheColor.namsaek)
            Button("고르기") { date = picked; dismiss() }
                .buttonStyle(.okchePrimary)
        }
        .padding(OkcheSpacing.space4)
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.large])
        .onAppear { picked = date ?? .now }
    }
}

/// 인슐린 맞는 시간과 양 줄들 + '+ 시간 추가'. 바탕 상자는 쓰는 쪽이 둔다.
struct InsulinSlotsEditor: View {
    @Binding var slots: [PetRegistration.InsulinSlotInput]
    let errors: [PetRegistration.Field: String]
    var focused: FocusState<PetRegistration.Field?>.Binding
    /// 시각을 고르는 줄(시트).
    @State private var pickingTime: UUID? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(slots.enumerated()), id: \.element.id) { index, slot in
                slotRow(index: index, id: slot.id)
                    .padding(.vertical, 10)
                    .overlay(alignment: .top) { if index > 0 { Rectangle().fill(OkcheColor.line).frame(height: 1) } }
            }
            OkcheFieldError(message: errors[.insulinTimes]).id(PetRegistration.Field.insulinTimes)
            Button { slots.append(.init(time: nextFreeTime())) } label: {
                Text("+ 시간 추가")
                    .okcheText(OkcheType.body, weight: .medium)
                    .foregroundStyle(OkcheColor.namsaek)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
        }
        .sheet(item: $pickingTime) { id in
            if let index = slots.firstIndex(where: { $0.id == id }) {
                TimeSheet(time: timeBinding(index))
            }
        }
    }

    private func slotRow(index: Int, id: UUID) -> some View {
        let nth = Self.ordinal(index + 1)
        let error = errors[.insulinUnits(id)]
        // 기본 compact 시간 고르기는 안쪽 버튼 이름이 '시간 선택기'로 고정돼 몇 번째 시간인지 못 읽는다 — 칸 + 시트로.
        // 다른 기기에서 온 틀린 글자는 그대로 보여 준다(안드로이드와 같음 — 지금 시각을 정해진 값처럼 보이지 않게).
        let valid = slots[index].time.split(separator: ":").compactMap { Int($0) }.count == 2
        let shown = valid ? timeBinding(index).wrappedValue.formatted(date: .omitted, time: .shortened) : slots[index].time
        let time = Button { pickingTime = id } label: {
            HStack {
                Text(shown).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                Spacer(minLength: 8)
                Image(systemName: "clock").font(.body).foregroundStyle(OkcheColor.ink)
            }
            .okcheFieldBox()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(nth) 시간")
        .accessibilityValue(shown)
        let units = HStack(spacing: 4) {
            TextField("", text: $slots[index].units)
                .okcheText(OkcheType.body)
                .frame(maxWidth: .infinity, maxHeight: .infinity) // 테두리 안 어디를 눌러도 칸이 잡히게
                .keyboardType(.decimalPad)
                .focused(focused, equals: .insulinUnits(id))
                .accessibilityLabel("\(nth) 양 (단위)")
            Text("단위").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                .fixedSize()
                .accessibilityHidden(true)
        }
        .okcheFieldBox(isError: error != nil)
        let remove = Button { slots.remove(at: index) } label: {
            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.lineStrong)
                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel("\(nth) 시간 빼기")
        .disabled(slots.count == 1)
        .opacity(slots.count == 1 ? 0.4 : 1)
        return VStack(alignment: .leading, spacing: 6) {
            // 큰 글자에서 한 줄에 안 들어가면 시간 / 양·빼기로 나눈다.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) { time; units.frame(width: 104); remove }
                VStack(alignment: .leading, spacing: 8) {
                    time
                    HStack(spacing: 8) { units; remove }
                }
            }
            OkcheFieldError(message: error)
        }
        .id(PetRegistration.Field.insulinUnits(id))
    }

    /// '첫 번째'…'열 번째', 그 뒤는 '11 번째'(안드로이드 `ordinal`과 같음).
    static func ordinal(_ n: Int) -> String {
        let words = ["첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열"]
        return (n >= 1 && n <= words.count ? words[n - 1] : String(n)) + " 번째"
    }

    /// 새 줄 시각: 12:00부터 한 시간씩 넘겨 아직 없는 시각(두 번 눌러도 같은 시간이 생기지 않게).
    private func nextFreeTime() -> String {
        let used = Set(slots.map(\.time))
        return (0..<24).map { String(format: "%02d:00", (12 + $0) % 24) }.first { !used.contains($0) } ?? "12:00"
    }

    /// `HH:mm` 글자를 시계 고르기와 잇는다(날짜는 쓰지 않음).
    private func timeBinding(_ index: Int) -> Binding<Date> {
        Binding {
            let parts = slots[index].time.split(separator: ":").compactMap { Int($0) }
            guard parts.count == 2 else { // 다른 기기에서 온 틀린 글자 — 고르기는 12:00에서(안드로이드와 같음)
                return Calendar.okcheDevice.date(bySettingHour: 12, minute: 0, second: 0, of: .now) ?? .now
            }
            return Calendar.okcheDevice.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: .now) ?? .now
        } set: { date in
            let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: date)
            slots[index].time = String(format: "%02d:%02d", c.hour!, c.minute!)
        }
    }
}

/// 시각 고르기: 돌림판에서 고른 뒤 '고르기'. 인슐린 시각(시·분), 기록 시간 바꾸기(날짜·시각, 지금보다 뒤 안 됨 — 7-2).
struct TimeSheet: View {
    @Binding var time: Date
    var components: DatePickerComponents = .hourAndMinute
    var notAfterNow = false
    @Environment(\.dismiss) private var dismiss
    @State private var picked = Date.now

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Button("취소") { dismiss() }
                Spacer()
            }
            .okcheText(OkcheType.body, weight: .medium)
            .foregroundStyle(OkcheColor.namsaek)
            .frame(minHeight: 44)
            Group {
                if notAfterNow {
                    DatePicker("시간", selection: $picked, in: ...Date.now, displayedComponents: components)
                } else {
                    DatePicker("시간", selection: $picked, displayedComponents: components)
                }
            }
            .datePickerStyle(.wheel)
            .labelsHidden()
            Button("고르기") { time = picked; dismiss() }
                .buttonStyle(.okchePrimary)
        }
        .padding(OkcheSpacing.space4)
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.medium, .large])
        .onAppear { picked = time }
    }
}

/// 화면 머리: 왼쪽 버튼(닫기·뒤로) + 제목 22 + 쉬운 말 14.
struct FormHeader: View {
    let title: String
    let note: String
    /// `xmark`(닫기) 또는 `chevron.left`(뒤로)
    let icon: String
    let iconLabel: String
    /// 머리 오른쪽(수라 화면의 낙관 — 7-3).
    var trailing: AnyView? = nil
    let action: () -> Void

    var body: some View {
        // 오른쪽 낙관과 한 줄에 안 들어가면(큰 글자) 낙관을 아래 줄로.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 4) {
                leading
                Spacer(minLength: 0)
                trailing
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    leading
                    Spacer(minLength: 0)
                }
                if let trailing { trailing.padding(.leading, 4) } // 안드로이드 FlowRow 다음 줄과 같은 자리
            }
        }
        .padding(.leading, 8)
        .padding(.trailing, trailing == nil ? 8 : 16) // 오른쪽 낙관은 보드처럼 16
        .frame(minHeight: 56)
    }

    @ViewBuilder private var leading: some View {
        Button(action: action) {
            Image(systemName: icon).font(.title2.weight(.medium)).foregroundStyle(OkcheColor.ink)
                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel(iconLabel)
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 8) { titleText; noteText }
            titleText
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var titleText: some View {
        Text(title).okcheText(OkcheType.title).foregroundStyle(OkcheColor.ink).lineLimit(1).minimumScaleFactor(0.6)
    }

    private var noteText: some View {
        Text(note).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
    }
}

/// 네모 체크 상자(디자인의 checkbox). iOS에 기본 체크 상자가 없어 Toggle 모양만 바꾼다.
struct CheckboxStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 8) {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(configuration.isOn ? OkcheColor.namsaek : OkcheColor.lineStrong)
                configuration.label.okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).multilineTextAlignment(.leading)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityRepresentation { Toggle(isOn: configuration.$isOn) { configuration.label } }
    }
}

// `.sheet(item:)`에 줄 ID를 바로 쓰려고.
extension UUID: @retroactive Identifiable {
    public var id: UUID { self }
}
