import SwiftData
import SwiftUI

/// 아이 등록 (디자인 보드 `Profile`·`ProfileDog`). 프로필 사진 칸은 11단계(사진 저장) 때 넣는다.
struct PetRegisterView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var form = PetRegistration()
    @State private var errors: [PetRegistration.Field: String] = [:]
    @State private var pickingBirth = false
    @FocusState private var focused: PetRegistration.Field?

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollViewReader { scroller in
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        OkcheTextField(label: "이름", text: $form.name, error: errors[.name])
                            .focused($focused, equals: .name).id(PetRegistration.Field.name)
                        group("종") {
                            OkcheChoiceCards(options: [(.cat, "고양이"), (.dog, "강아지")], selection: $form.species)
                        }
                        breedPicker
                        furPicker
                        group("성별", note: "(선택)") {
                            OkcheChoiceChips(options: [(Sex.male, "남아"), (.female, "여아")], selection: $form.sex)
                        }
                        group("중성화", note: "(선택)") {
                            OkcheChoiceChips(options: [(true, "했어요"), (false, "안 했어요")], selection: $form.neutered)
                        }
                        birthSection
                        OkcheTextField(label: "체중", note: "(kg)", text: $form.weightKg, keyboard: .decimalPad, error: errors[.weight])
                            .focused($focused, equals: .weight).id(PetRegistration.Field.weight)
                        VStack(alignment: .leading, spacing: 6) {
                            OkcheTextField(label: "진단명", note: "(있을 때만)", text: $form.diagnosis, placeholder: "예: 염증성 장질환(IBD)")
                            Text("적어 두면 실록과 탕약 알림을 투병 관리에 맞춰 쓸 수 있어요.")
                                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                        }
                        OkcheSwitchRow(title: "당뇨가 있어요", detail: "켜면 일기와 위젯에 혈당·인슐린 칸이 생겨요.", isOn: $form.hasDiabetes)
                        if form.hasDiabetes {
                            InsulinSection(form: $form, errors: errors, focused: $focused)
                                .padding(.top, -6)
                        }
                        VStack(alignment: .leading, spacing: 8) {
                            OkcheFieldError(message: errors[.save])
                            Button("모시기") { save(scroller) }
                                .buttonStyle(.okchePrimary)
                        }
                    }
                    .padding(.horizontal, OkcheSpacing.space4)
                    .padding(.top, 8)
                    .padding(.bottom, OkcheSpacing.space6)
                }
                .scrollDismissesKeyboard(.interactively)
            }
        }
        .background(OkcheColor.hanji)
        // 숫자 자판에는 리턴 키가 없어 닫는 버튼을 붙인다.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("완료") { focused = nil }
            }
        }
        .sheet(isPresented: $pickingBirth) { BirthPicker(date: $form.birthDate) }
    }

    private var header: some View {
        HStack(spacing: 4) {
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.title2.weight(.medium)).foregroundStyle(OkcheColor.ink)
                    .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel("닫기")
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) { headerTitle; headerNote }
                headerTitle
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .frame(minHeight: 56)
    }

    private var headerTitle: some View {
        Text("새 아기씨 모시기").okcheText(OkcheType.title).foregroundStyle(OkcheColor.ink).lineLimit(1).minimumScaleFactor(0.6)
    }

    private var headerNote: some View {
        Text("아이 등록").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
    }

    private func group<Content: View>(_ title: String, note: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            OkcheFieldLabel(title: title, note: note).accessibilityAddTraits(.isHeader)
            content()
        }
    }

    private var breedPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            OkcheFieldLabel(title: "품종").accessibilityHidden(true)
            Menu {
                Picker("품종", selection: $form.breed) {
                    ForEach(PetCatalog.breeds(form.species), id: \.self) { Text($0).tag($0) }
                }
            } label: {
                HStack {
                    Text(form.breed).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).multilineTextAlignment(.leading)
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.down").font(.body.weight(.medium)).foregroundStyle(OkcheColor.inkMuted)
                }
                .okcheFieldBox()
            }
            .accessibilityLabel("품종")
            .accessibilityValue(form.breed)
        }
    }

    private var furPicker: some View {
        group("털색") {
            // 큰 글자에서 이름이 꺾이면 두 줄로.
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 12) { furButtons(PetCatalog.furColors(form.species)) }
                VStack(alignment: .leading, spacing: 12) {
                    let furs = PetCatalog.furColors(form.species)
                    HStack(alignment: .top, spacing: 12) { furButtons(Array(furs.prefix(2))) }
                    HStack(alignment: .top, spacing: 12) { furButtons(Array(furs.suffix(2))) }
                }
            }
        }
    }

    private func furButtons(_ furs: [PetCatalog.FurColor]) -> some View {
                ForEach(furs, id: \.self) { fur in
                    let on = fur.name == form.furColor
                    Button { form.furColor = fur.name } label: {
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

    private var birthSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            if form.birthUnknown {
                OkcheTextField(label: "나이", note: "(대략)", text: $form.approxAge, unit: "살쯤", keyboard: .numberPad, error: errors[.age],
                               hint: Self.ageHelp)
                    .focused($focused, equals: .age).id(PetRegistration.Field.age)
            } else {
                OkcheFieldLabel(title: "생일").accessibilityHidden(true)
                Button { pickingBirth = true } label: {
                    HStack {
                        Text(form.birthDate.map { $0.formatted(.dateTime.year().month().day()) } ?? "날짜 고르기")
                            .okcheText(OkcheType.body)
                            .foregroundStyle(form.birthDate == nil ? OkcheColor.inkMuted : OkcheColor.ink)
                        Spacer(minLength: 8)
                        Image(systemName: "calendar").font(.body).foregroundStyle(OkcheColor.ink)
                    }
                    .okcheFieldBox(isError: errors[.birth] != nil)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("생일")
                .accessibilityValue(form.birthDate.map { $0.formatted(date: .long, time: .omitted) } ?? "안 고름")
                .id(PetRegistration.Field.birth)
                OkcheFieldError(message: errors[.birth])
            }
            Toggle("생일을 몰라요 (나이를 어림잡아 넣을게요)", isOn: $form.birthUnknown)
                .toggleStyle(CheckboxStyle())
            if form.birthUnknown {
                Text(Self.ageHelp)
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    .accessibilityHidden(true) // 나이 칸의 힌트로 읽는다
            }
        }
    }

    private static let ageHelp = "오늘에서 그만큼 거슬러 간 날을 생일로 잡아요. 1살이 안 됐으면 0을 넣어 주세요."

    private func save(_ scroller: ScrollViewProxy) {
        do {
            try form.save(in: context)
            errors = [:]
            dismiss()
        } catch let invalid as PetRegistration.Invalid {
            errors = invalid.errors
            // 화면 위에서부터 첫 오류 칸으로
            let order: [PetRegistration.Field] = [.name, .birth, .age, .weight]
                + form.insulinSlots.map { .insulinUnits($0.id) } + [.insulinTimes]
            if let first = order.first(where: { errors[$0] != nil }) {
                withAnimation { scroller.scrollTo(first, anchor: .center) }
                if first != .birth, first != .insulinTimes { focused = first }
                // 화면 읽기: 오류 수와 첫 오류를 한 번에(같은 오류로 다시 눌러도 다시 알린다).
                let message = errors.count == 1 ? errors[first]! : "고칠 곳 \(errors.count)개. \(errors[first]!)"
                AccessibilityNotification.Announcement(message).post()
            }
        } catch {
            errors = [.save: "저장하지 못했어요. 다시 해 주세요."]
            AccessibilityNotification.Announcement(errors[.save]!).post()
        }
    }
}

/// 인슐린 칸 (보드 `Profile`의 인슐린 상자).
private struct InsulinSection: View {
    @Binding var form: PetRegistration
    let errors: [PetRegistration.Field: String]
    var focused: FocusState<PetRegistration.Field?>.Binding

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("인슐린").okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
            OkcheTextField(label: "인슐린 이름", note: "(몰라도 돼요)", text: $form.insulinName, placeholder: "예: 병원에서 받은 인슐린 이름")
            VStack(alignment: .leading, spacing: 0) {
                OkcheFieldLabel(title: "맞는 시간과 양").padding(.bottom, 8).accessibilityAddTraits(.isHeader)
                ForEach(Array(form.insulinSlots.enumerated()), id: \.element.id) { index, slot in
                    slotRow(index: index, id: slot.id)
                        .padding(.vertical, 10)
                        .overlay(alignment: .top) { if index > 0 { Rectangle().fill(OkcheColor.line).frame(height: 1) } }
                }
                OkcheFieldError(message: errors[.insulinTimes]).id(PetRegistration.Field.insulinTimes)
                Button("+ 시간 추가") { form.insulinSlots.append(.init(time: "12:00")) }
                    .okcheText(OkcheType.body, weight: .medium)
                    .foregroundStyle(OkcheColor.namsaek)
                    .frame(minHeight: 44)
            }
            VStack(alignment: .leading, spacing: 8) {
                OkcheFieldLabel(title: "주사기 종류").accessibilityAddTraits(.isHeader)
                OkcheChoiceChips(options: [(Syringe.u40, "U-40"), (.u100, "U-100"), (.unknown, "잘 모르겠어요")],
                                 selection: Binding { form.syringe } set: { form.syringe = $0 ?? .unknown }, allowsNone: false)
                note("인슐린과 맞는 주사기를 쓰길 권해요. 모르면 병원에 물어보길 권해요.")
            }
            note("병원에서 정해 준 양을 넣어 주세요. 나중에 아이 정보에서 바꿀 수 있어요.").padding(.bottom, 8)
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 6)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
    }

    private func slotRow(index: Int, id: UUID) -> some View {
        let order = ["첫 번째", "두 번째", "세 번째", "네 번째", "다섯 번째"]
        let nth = index < order.count ? order[index] : "\(index + 1)번째"
        let error = errors[.insulinUnits(id)]
        let time = DatePicker("\(nth) 시간", selection: timeBinding(index), displayedComponents: .hourAndMinute)
            .labelsHidden()
            .frame(maxWidth: .infinity, alignment: .leading)
            .okcheFieldBox()
        let units = HStack(spacing: 4) {
            TextField("", text: $form.insulinSlots[index].units)
                .okcheText(OkcheType.body)
                .keyboardType(.decimalPad)
                .focused(focused, equals: .insulinUnits(id))
                .accessibilityLabel("\(nth) 양 (단위)")
            Text("단위").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                .fixedSize()
                .accessibilityHidden(true)
        }
        .okcheFieldBox(isError: error != nil)
        let remove = Button { form.insulinSlots.remove(at: index) } label: {
            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.lineStrong)
                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel("\(nth) 시간 빼기")
        .disabled(form.insulinSlots.count == 1)
        .opacity(form.insulinSlots.count == 1 ? 0.4 : 1)
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

    /// `HH:mm` 글자를 시계 고르기와 잇는다(날짜는 쓰지 않음).
    private func timeBinding(_ index: Int) -> Binding<Date> {
        Binding {
            let parts = form.insulinSlots[index].time.split(separator: ":").compactMap { Int($0) }
            return Calendar.okcheDevice.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: .now) ?? .now
        } set: { date in
            let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: date)
            form.insulinSlots[index].time = String(format: "%02d:%02d", c.hour!, c.minute!)
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
    }
}

/// 생일 고르기: 달력에서 고른 뒤 '고르기'.
private struct BirthPicker: View {
    @Binding var date: Date?
    @Environment(\.dismiss) private var dismiss
    @State private var picked = Date.now

    var body: some View {
        VStack(spacing: 16) {
            DatePicker("생일", selection: $picked, in: ...Date.now, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(OkcheColor.namsaek)
            Button("고르기") { date = picked; dismiss() }
                .buttonStyle(.okchePrimary)
        }
        .padding(OkcheSpacing.space4)
        .presentationDetents([.large])
        .onAppear { picked = date ?? .now }
    }
}

/// 네모 체크 상자(디자인의 checkbox). iOS에 기본 체크 상자가 없어 Toggle 모양만 바꾼다.
private struct CheckboxStyle: ToggleStyle {
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
