import SwiftData
import SwiftUI

/// 자세히 시트를 여는 값: 새 기록(종류·아이)이거나 고칠 기록.
struct DetailTarget: Identifiable {
    let id = UUID()
    let kind: RecordKind
    var editing: Record?
    var petId: UUID?
}

/// 구토·매화·소피 자세히 시트(보드 `Vomit`·`Stool`·`Urine`): 시간, 칸들, 의서 안내, '기록하기'. 고치기도 같은 시트(값이 채워진 채).
struct RecordDetailSheet: View {
    let target: DetailTarget
    /// 저장한 기록과 새 기록인지.
    let onSaved: (Record, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    @State private var values: RecordDetail.Values = [:]
    @State private var occurredAt = Date.now
    @State private var timeChanged = false
    @State private var pickingTime = false
    @State private var alerting = false
    /// 이 시트에서 레이어가 저절로 떴는지(시트 한 번에 한 번 — 2026-11-03 사용자).
    @State private var alerted = false
    @State private var failed = false
    @State private var saving = false

    init(target: DetailTarget, onSaved: @escaping (Record, Bool) -> Void) {
        self.target = target
        self.onSaved = onSaved
        // 고칠 값은 처음 한 번만 채운다(레이어가 닫히며 onAppear가 다시 불려도 고르던 값을 덮지 않게).
        _values = State(initialValue: target.editing.map(RecordDetail.values) ?? [:])
        _occurredAt = State(initialValue: target.editing?.occurredAt ?? .now)
    }

    private var kind: RecordKind { target.kind }
    private var petId: UUID? { target.editing?.petId ?? target.petId }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    let name = petId.flatMap { id in pets.first { $0.id == id }?.name } ?? "누군지 모름"
                    Text("\(name) · \(QuickRecord.name(kind.rawValue))").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                timeRow
                ForEach(RecordDetail.fields(kind), id: \.key) { field in fieldView(field) }
                warning
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                Button("기록하기", action: save).buttonStyle(.okchePrimary)
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.vertical, OkcheSpacing.space6)
        }
        .background(OkcheColor.hanji)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .sheet(isPresented: $pickingTime) {
            TimeSheet(time: Binding(get: { occurredAt }, set: { occurredAt = $0; timeChanged = true }),
                      components: [.date, .hourAndMinute], notAfterNow: true)
        }
        .fullScreenCover(isPresented: $alerting) {
            GuideLayer(kind: kind) { alerting = false }.presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = alerting }
    }

    /// '지금 · 오후 2:14' + '시간 바꾸기'.
    private var timeRow: some View {
        // 큰 글자에서 한 줄에 안 들어가면 버튼을 아래로.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                Text(timeText).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink).fixedSize()
                Spacer(minLength: 0)
                timeButton
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(timeText).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                timeButton
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 14)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
    }

    private var timeButton: some View {
        Button("시간 바꾸기") { pickingTime = true }
            .okcheText(OkcheType.label, weight: .regular)
            .fixedSize()
            .foregroundStyle(OkcheColor.ink)
            .padding(.horizontal, 12)
            .frame(minHeight: 40)
            .background(OkcheColor.paper, in: Capsule())
            .overlay(Capsule().strokeBorder(OkcheColor.lineStrong, lineWidth: 1.5))
            .frame(minHeight: 44)
    }

    private var timeText: String {
        let clock = Self.clock.string(from: occurredAt)
        if target.editing == nil && !timeChanged { return "지금 · \(clock)" }
        let day = OkcheDay.of(occurredAt)
        let today = OkcheDay.of(.now)
        return "\(day == today ? "오늘" : QuickRecord.dayTitle(day, today: today)) · \(clock)"
    }

    private static let clock: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "a h:mm"
        return f
    }()

    @ViewBuilder private func fieldView(_ field: RecordDetail.Field) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 2) {
                OkcheFieldLabel(title: field.title, note: field.hint).accessibilityAddTraits(.isHeader)
                if field.key == "color" {
                    Button { alerting = true } label: {
                        Image(systemName: "questionmark.circle").font(.body).foregroundStyle(OkcheColor.namsaek)
                            .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(kind == .urine ? "어떨 때 병원에 가야 하나요" : "어떤 빛깔이면 병원에 가야 하나요")
                }
            }
            let options = field.options.map { (value: $0.value, title: $0.title) }
            let isOn: (String) -> Bool = { values[field.key]?.contains($0) == true }
            if kind == .stool && field.key == "shape" {
                OkcheChoiceCards(options: options, icons: Self.shapeIcons, isOn: isOn) { pick(field, $0) }
            } else {
                OkcheChoiceChips(options: options, multiple: field.multi, isOn: isOn) { pick(field, $0) }
            }
        }
    }

    /// 디자인 아이콘(알갱이·알약·무른 덩어리·웅덩이·물방울)에 가까운 SF Symbols.
    private static let shapeIcons = ["hard": "ellipsis", "normal": "capsule", "soft": "mountain.2", "loose": "humidity", "watery": "drop"]

    private func pick(_ field: RecordDetail.Field, _ value: String) {
        let before = values
        values = RecordDetail.toggle(values, field: field, value: value)
        if !alerted && RecordDetail.picksAlert(kind, before: before, after: values) {
            alerted = true
            alerting = true
        }
    }

    /// 구토·소피 시트 아래 연지색 안내('의서'·'의서 보기'를 누르면 레이어).
    @ViewBuilder private var warning: some View {
        let parts: (String, String)? = switch kind {
        case .vomit: ("피·커피색이 보이거나 이물질을 삼킨 것 같으면 ", "의서")
        case .urine: ("힘만 주고 안 나오면 변비처럼 보여도 응급일 수 있어요. ", "의서 보기")
        default: nil
        }
        if let (text, link) = parts {
            Button { alerting = true } label: {
                (Text(text) + Text(link).fontWeight(.bold) + Text(kind == .vomit ? "를 먼저 보길 권해요." : ""))
                    .okcheText(OkcheType.label, weight: .regular)
                    .foregroundStyle(OkcheColor.yeonji)
                    .multilineTextAlignment(.leading)
            }
            .buttonStyle(.plain)
            .accessibilityHint("의서 안내 열기")
        }
    }

    private func save() {
        guard !saving else { return } // 두 번 눌러 기록이 둘 생기지 않게(닫히는 동안에도 — 실패할 때만 다시 풂)
        saving = true
        // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
        let at = target.editing == nil && !timeChanged ? Date.now : occurredAt
        do {
            let record = try RecordDetail.save(kind, values: values, occurredAt: at, editing: target.editing, petId: petId, in: context)
            onSaved(record, target.editing == nil)
            dismiss()
        } catch {
            saving = false
            failed = true
            AccessibilityNotification.Announcement("저장하지 못했어요. 다시 해 주세요.").post()
        }
    }
}

/// 의서 레이어(보드 `GuideAlert`·`GuideAlertStool`·`GuideAlertUrine`): 고른 것을 집어 말하지 않고 의서의 '지금 바로 병원에' 공통 목록만.
struct GuideLayer: View {
    let kind: RecordKind
    let onClose: () -> Void
    @Environment(\.openURL) private var openURL
    @AccessibilityFocusState private var titleFocused: Bool

    var body: some View {
        ZStack {
            OkcheColor.ink.opacity(0.45).ignoresSafeArea()
                .onTapGesture(perform: onClose)
                .accessibilityHidden(true)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("의서").okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                            Text("병원에 바로 연락하길 권해요").okcheText(OkcheType.heading).foregroundStyle(OkcheColor.yeonji)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($titleFocused)
                        Spacer(minLength: 0)
                        Button(action: onClose) {
                            Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(OkcheColor.ink)
                                .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                        }
                        .accessibilityLabel("닫기")
                    }
                    Text("의서의 '지금 바로 병원에' 목록이에요.").okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(GuideText.lines(kind), id: \.self) { line in
                            HStack(alignment: .firstTextBaseline, spacing: 10) {
                                Circle().fill(OkcheColor.yeonji).frame(width: 6, height: 6).accessibilityHidden(true)
                                Text(line).okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                            }
                        }
                    }
                    Text(GuideText.help(kind)).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    Button("가까운 24시 동물병원 찾기") { GuideText.openHospitalSearch(openURL) }.buttonStyle(.okchePrimary)
                    Button("기록 이어 하기", action: onClose).buttonStyle(.okchePrimary(.outlined))
                    SourceNote(text: GuideText.source)
                }
                .padding(.horizontal, 18)
                .padding(.top, 20)
                .padding(.bottom, 18)
                .background(OkcheColor.hanji, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                .padding(.horizontal, 16)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)
        }
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onClose) // 두 손가락 Z로 닫기
        .onAppear { titleFocused = true }
    }
}

/// 의서 레이어 글자·지도 열기.
enum GuideText {
    static func lines(_ kind: RecordKind) -> [String] {
        let urinary = "화장실에서 힘만 주고 소변이 안 나와요 (특히 수컷\u{00A0}고양이 · 변비처럼 보여도 응급일 수 있어요)"
        let limp = "축 늘어져 불러도 반응이 약해요"
        return switch kind {
        case .vomit: [
            "피를 토하거나, 커피 찌꺼기 같은 걸 토해요",
            "연달아 여러 번 토하거나, 물만 마셔도 토해요",
            "토하려는데 아무것도 안 나와요 (강아지)",
            "끈·실·약·사람 음식 같은 걸 삼킨 것 같아요 — 실이 보여도 잡아당기거나 억지로 토하게 하지 않길 권해요",
            limp,
        ]
        case .stool: ["변이 짜장처럼 새까매요", "변에 피가 많이 섞였고, 처지거나 토해요", urinary, limp]
        default: [urinary, limp]
        }
    }

    static func help(_ kind: RecordKind) -> String {
        switch kind {
        case .vomit: "토한 것 사진과 실록을 보여 주면 진료에 도움이 돼요."
        case .stool: "변 사진과 실록을 보여 주면 진료에 도움이 돼요."
        default: "언제부터 못 봤는지와 실록을 보여 주면 진료에 도움이 돼요."
        }
    }

    static let source = "출처: 의서와 같음(코넬대 수의대 고양이·개 건강센터 외). 위 내용은 이 단체들의 권고사항이고, 아이마다 다를 수 있어요. 진단이 아니니 수의사와 상의하길 권해요."

    /// '24시 동물병원'으로 네이버 지도 앱 → 없으면 카카오맵 앱 → 둘 다 없으면 네이버 지도 웹(2026-11-01 사용자). 위치 권한은 지도 앱이.
    static func openHospitalSearch(_ openURL: OpenURLAction) {
        let query = "24시 동물병원"
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let path = query.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? ""
        guard let naver = URL(string: "nmap://search?query=\(q)&appname=app.okche"),
              let kakao = URL(string: "kakaomap://search?q=\(q)"),
              let web = URL(string: "https://map.naver.com/p/search/\(path)") else { return }
        openURL(naver) { opened in
            if !opened { openURL(kakao) { opened in if !opened { openURL(web) } } }
        }
    }
}
