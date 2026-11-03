import SwiftData
import SwiftUI

/// 호칭·나이 표기·지금 보는 아이(plan.md 6-3). 안드로이드 `PetSeal.kt`와 같은 규칙.
enum PetTitle {
    /// 생일부터 오늘까지 꽉 찬 해. 2/29생은 다음 해 2/28에 1살(기기 달력).
    private static func fullYears(from birth: Date, to today: Date, _ calendar: Calendar) -> Int {
        var years = calendar.dateComponents([.year], from: birth, to: today).year ?? 0
        if let next = calendar.date(byAdding: .year, value: years + 1, to: birth), next <= today { years += 1 }
        return years
    }

    private static func birth(_ birthDate: String?, _ calendar: Calendar) -> Date? {
        birthDate.flatMap { PetRegistration.date($0, calendar) }
    }

    /// 생일(어림 포함)부터 1살 생일 전날까지 '아기씨', 1살 생일부터 '마마'. 생일이 없으면 '아기씨'.
    static func honorific(_ birthDate: String?, today: Date = .now, calendar: Calendar = .okcheDevice) -> String {
        guard let birth = birth(birthDate, calendar) else { return "아기씨" }
        return fullYears(from: birth, to: calendar.startOfDay(for: today), calendar) >= 1 ? "마마" : "아기씨"
    }

    /// '콩이 마마'. 추억으로 간직한 아이는 호칭 없이 이름만(6-4).
    static func name(_ pet: Pet, today: Date = .now, calendar: Calendar = .okcheDevice) -> String {
        pet.memorializedAt != nil ? pet.name : "\(pet.name) \(honorific(pet.birthDate, today: today, calendar: calendar))"
    }

    /// 이름 + 을/를(받침이 있으면 '을'). 한글로 끝나지 않으면 '을(를)'.
    static func withObjectParticle(_ name: String) -> String {
        guard let last = name.unicodeScalars.last, (0xAC00...0xD7A3).contains(last.value) else { return name + "을(를)" }
        return name + ((last.value - 0xAC00) % 28 == 0 ? "를" : "을")
    }

    /// 목록 순서: 모신 순, 추억으로 간직한 아이는 맨 뒤(추억끼리도 모신 순, 6-4).
    static func ordered(_ pets: [Pet]) -> [Pet] {
        pets.filter { $0.memorializedAt == nil } + pets.filter { $0.memorializedAt != nil }
    }

    /// 만 나이 '7살', 1살 미만은 만 개월 '3개월'(1개월 미만 '0개월'). 생일이 없으면 nil.
    static func age(_ birthDate: String?, today: Date = .now, calendar: Calendar = .okcheDevice) -> String? {
        guard let birth = birth(birthDate, calendar) else { return nil }
        let day = calendar.startOfDay(for: today)
        let years = fullYears(from: birth, to: day, calendar)
        if years >= 1 { return "\(years)살" }
        var months = calendar.dateComponents([.month], from: birth, to: day).month ?? 0
        if let next = calendar.date(byAdding: .month, value: months + 1, to: birth), next <= day { months += 1 }
        return "\(max(months, 0))개월"
    }

    /// 지금 보는 아이: 이 기기만의 설정 `lastPetId`, 없거나 지운 아이면 가장 먼저 모신 아이. `pets`는 지운 줄 뺀 `createdAt` 순.
    static func current(_ pets: [Pet], lastPetId: String) -> Pet? {
        pets.first { $0.id.uuidString == lastPetId } ?? pets.first
    }
}

/// 금테 네모 44: 남색 바탕에 작은 금테(디자인 낙관·아이 바꾸기 시트). 안은 사진(11단계)·캐릭터 자리라 지금은 비움.
struct SealSquare: View {
    var body: some View {
        OkcheColor.namsaekDeep
            .frame(width: 44, height: 44)
            .overlay(GoldFrame(inset: 4, knot: 0.45).stroke(OkcheColor.geum, lineWidth: 1))
            .accessibilityHidden(true)
    }
}

/// 낙관형 표시(탭 머리 오른쪽, 디자인 `Record` 등): 지금 보는 아이 호칭 + '바꾸기' + 금테 네모. 누르면 아이 바꾸기 시트.
/// 아이가 없으면 같은 자리에 '+ 새 아기씨 모시기'(보드 `TabHeaderNoPet`).
struct PetSeal: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @State private var switching = false
    @State private var afterSheet: PetSwitchSheet.Next?
    @State private var editing: Pet?
    @State private var registering = false
    /// 호칭 16 고운바탕 굵게(디자인 값, 토큰에 16 고운바탕이 없음).
    private let sealName = OkcheTextStyle(family: .display, size: 16, lineHeight: 18, weight: .bold)

    var body: some View {
        Group {
            if let pet = PetTitle.current(pets, lastPetId: lastPetId) {
                Button { switching = true } label: {
                    HStack(spacing: 10) {
                        VStack(alignment: .trailing, spacing: 0) {
                            Text(PetTitle.name(pet)).okcheText(sealName).foregroundStyle(OkcheColor.ink)
                            Text("바꾸기").okcheText(OkcheType.caption).foregroundStyle(OkcheColor.inkMuted)
                        }
                        SealSquare()
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .accessibilityLabel("지금 보는 아이 \(PetTitle.name(pet)) · 아이 바꾸기")
            } else {
                Button("+ 새 아기씨 모시기") { registering = true }
                    .buttonStyle(.okchePrimary(.pill))
            }
        }
        // 시트를 닫은 뒤에 다음 화면을 연다(닫히는 중에 띄우면 안 뜸).
        .sheet(isPresented: $switching, onDismiss: {
            switch afterSheet {
            case .edit: editing = PetTitle.current(pets, lastPetId: lastPetId)
            case .register: registering = true
            case nil: break
            }
            afterSheet = nil
        }) {
            PetSwitchSheet(pets: pets, lastPetId: $lastPetId) { afterSheet = $0 }
        }
        .fullScreenCover(item: $editing) { pet in NavigationStack { PetEditScreen(pet: pet) } }
        .fullScreenCover(isPresented: $registering) { PetRegisterView() }
    }
}

/// 아이 바꾸기 시트(보드 `PetSwitch`). 고르면 바로 바뀌고 닫힌다.
struct PetSwitchSheet: View {
    enum Next { case edit, register }
    let pets: [Pet]
    @Binding var lastPetId: String
    let then: (Next) -> Void
    @Environment(\.dismiss) private var dismiss
    @Query(filter: #Predicate<RestPeriod> { $0.endedAt == nil && $0.deletedAt == nil }) private var rests: [RestPeriod]
    /// 시트 높이를 내용에 맞춘다(아이가 많거나 큰 글자면 끝까지 올려 스크롤).
    @State private var contentHeight: CGFloat = 360

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("어느 아이를 볼까요?").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                VStack(spacing: 8) {
                    ForEach(PetTitle.ordered(pets)) { pet in row(pet) }
                }
                Text("고른 아이가 모든 화면 오른쪽 위에 계속 보여요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { buttons }
                    VStack(spacing: 8) { buttons }
                }
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, OkcheSpacing.space6)
            .padding(.bottom, OkcheSpacing.space6)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { contentHeight = $0 }
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.height(contentHeight), .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder private var buttons: some View {
        Button("아이 정보 고치기") { then(.edit); dismiss() }.buttonStyle(.okchePrimary(.outlined))
        Button("+ 새 아기씨 모시기") { then(.register); dismiss() }.buttonStyle(.okchePrimary(.dashed))
    }

    private func row(_ pet: Pet) -> some View {
        let selected = pet.id == PetTitle.current(pets, lastPetId: lastPetId)?.id
        let detail = pet.memorializedAt != nil
            ? [pet.species == .cat ? "고양이" : "강아지", "추억으로 간직 중"].joined(separator: " · ")
            : [pet.species == .cat ? "고양이" : "강아지", PetTitle.age(pet.birthDate),
               rests.contains { $0.petId == pet.id } ? "휴양 중" : nil].compactMap { $0 }.joined(separator: " · ")
        return RadioRow(title: PetTitle.name(pet), detail: detail, selected: selected, showsSeal: true) {
            lastPetId = pet.id.uuidString
            dismiss()
        }
    }
}

/// 라디오 줄(아이 바꾸기 시트·누구였는지 정하기 시트): 동그라미 + (금테 네모) + 이름·설명. 고른 줄은 남색 연한 바탕·테두리.
struct RadioRow: View {
    let title: String
    var detail: String?
    let selected: Bool
    var showsSeal = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.title3).foregroundStyle(selected ? OkcheColor.namsaek : OkcheColor.lineStrong)
                    .accessibilityHidden(true)
                if showsSeal { SealSquare() }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).okcheText(OkcheType.body, weight: selected ? .bold : nil)
                        .foregroundStyle(selected ? OkcheColor.namsaekDeep : OkcheColor.ink)
                    if let detail {
                        Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(selected ? OkcheColor.namsaekSoft : OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
            .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                .strokeBorder(selected ? OkcheColor.namsaek : OkcheColor.line, lineWidth: 1.5))
            .contentShape(Rectangle())
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 더보기 '아이 관리'(보드 `More`·`MoreNoPet`): 아이마다 호칭(→ 아이 정보) + '휴양 중 · 관리 쉬기' 스위치(바로 저장).
struct PetManageSection: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @Query(filter: #Predicate<RestPeriod> { $0.endedAt == nil && $0.deletedAt == nil }) private var rests: [RestPeriod]
    @Environment(\.modelContext) private var context
    @State private var registering = false
    @State private var failed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("아이 관리").okcheText(OkcheType.label).foregroundStyle(OkcheColor.inkMuted)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 10).padding(.bottom, pets.isEmpty ? 12 : 4)
            if pets.isEmpty {
                Text("아직 모신 아이가 없어요.").okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                Button("+ 새 아기씨 모시기") { registering = true }
                    .buttonStyle(.okchePrimary(.dashed))
                    .padding(.top, 12).padding(.bottom, 10)
            } else {
                ForEach(Array(PetTitle.ordered(pets).enumerated()), id: \.element.id) { index, pet in
                    row(pet)
                        .overlay(alignment: .top) { if index > 0 { Rectangle().fill(OkcheColor.line).frame(height: 1) } }
                }
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil).padding(.bottom, failed ? 10 : 0)
            }
        }
        .padding(.horizontal, OkcheSpacing.space4)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .fullScreenCover(isPresented: $registering) { PetRegisterView() }
    }

    private func row(_ pet: Pet) -> some View {
        let resting = rests.contains { $0.petId == pet.id }
        let note = pet.memorializedAt != nil ? "· 추억으로 간직 중" : resting ? "· 휴양 중" : nil
        let link = NavigationLink { PetEditScreen(pet: pet) } label: {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(PetTitle.name(pet)).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                if let note { Text(note).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted) }
            }
            .multilineTextAlignment(.leading)
            .frame(minHeight: 44)
        }
        .accessibilityLabel(PetTitle.name(pet) + (note.map { " \($0)" } ?? ""))
        // 추억으로 간직한 아이는 휴양 스위치 없음(6-4).
        if pet.memorializedAt != nil {
            return AnyView(HStack { link; Spacer(minLength: 0) }.frame(minHeight: 56))
        }
        let toggle = Toggle(isOn: Binding(get: { resting }, set: { on in
            do {
                try RestPeriod.setResting(on, petId: pet.id, in: context)
                failed = false
            } catch {
                failed = true
                AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
            }
        })) {
            Text("휴양 중 · 관리 쉬기").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .tint(OkcheColor.namsaek)
        .accessibilityLabel("\(PetTitle.name(pet)) 휴양 중 · 관리 쉬기")
        // 한 줄에 안 들어가면 스위치를 아래 줄로(큰 글자에서 스위치 이름도 줄바꿈).
        return AnyView(ViewThatFits(in: .horizontal) {
            HStack { link; Spacer(minLength: 8); toggle.fixedSize() }
            VStack(alignment: .leading, spacing: 4) { link; toggle }
        }
        .frame(minHeight: 56))
    }
}
