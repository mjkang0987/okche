import SwiftData
import SwiftUI

/// 일기 탭 첫 화면(보드 `Record`): '누구 기록인가요?' 칩, 바로 기록 줄(구토·매화·소피는 ⋯ 자세히 — 7-2), 방금 남긴 기록. 탕약·옥체·사진은 그 단계에서.
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

    /// 칩에 나오는 아이: 추억·지운 아이를 뺀 아이들(모신 순).
    private var chipPets: [Pet] { pets.filter { $0.memorializedAt == nil } }

    /// 고른 아이: '누군지 몰라요'면 nil. 지금 보는 아이가 칩에 없으면(추억·아이 없음) '누군지 몰라요'(2026-11-03 사용자).
    private var who: UUID? {
        guard !unknownPicked, let current = PetTitle.current(pets, lastPetId: lastPetId), current.memorializedAt == nil else { return nil }
        return current.id
    }

    var body: some View {
        NavigationStack {
            // 위 20은 디자인 일기 화면 값(토큰에 20이 없음).
            TabScreen(title: "승정원일기", subtitle: "매일 기록", topPadding: 20, trailing: { PetSeal() }) {
                FieldGroup(title: "누구 기록인가요?") {
                    OkcheChoiceChips(options: chipPets.map { ($0.id.uuidString, $0.name) } + [("", "누군지 몰라요")],
                                     selection: Binding(get: { who?.uuidString ?? "" }, set: { pick($0 ?? "") }),
                                     allowsNone: false)
                }
                QuickRecordCard(onTap: { row, value in record(row, value) }, onDetail: { detail = DetailTarget(kind: $0, petId: who) })
                Text("누르면 바로 기록돼요. 빛깔·모양 같은 자세한 기록은 ⋯ 버튼으로.").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                OkcheFieldError(message: failed ? "저장하지 못했어요. 다시 해 주세요." : nil)
                RecentRecordSection(justSaved: justSaved)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(item: $detail) { target in
            RecordDetailSheet(target: target) { record, isNew in
                guard isNew else { return }
                justSaved = record.id
                AccessibilityNotification.Announcement("아뢰었사옵니다").post()
            }
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

/// 바로 기록 줄 넷(구토·매화·소피·수라): 아이콘·종류 이름 + 56 높이 버튼 셋 + ⋯ 자세히(수라는 7-3), 흰 카드 안 줄 사이 선(보드 `Record`).
/// 큰 글자·좁은 폭에선 이름 아래로 버튼.
struct QuickRecordCard: View {
    let onTap: (QuickRecord.Row, String) -> Void
    let onDetail: (RecordKind) -> Void
    @ScaledMetric(relativeTo: .body) private var buttonHeight: CGFloat = 56
    @ScaledMetric(relativeTo: .body) private var labelWidth: CGFloat = 62
    @ScaledMetric(relativeTo: .body) private var moreSize: CGFloat = 44

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(QuickRecord.rows.enumerated()), id: \.element.kind) { index, row in
                if index > 0 { OkcheColor.line.frame(height: 1) }
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 6) {
                        label(row).frame(width: labelWidth, alignment: .leading)
                        HStack(spacing: 6) { buttons(row) }
                        Spacer(minLength: 0)
                        more(row)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            label(row)
                            Spacer(minLength: 6)
                            more(row)
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
    @ViewBuilder private func more(_ row: QuickRecord.Row) -> some View {
        if !RecordDetail.fields(row.kind).isEmpty {
            Button { onDetail(row.kind) } label: {
                Image(systemName: "ellipsis").font(.body.weight(.bold)).foregroundStyle(OkcheColor.namsaek)
                    .frame(width: moreSize, height: moreSize)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
                    .overlay(RoundedRectangle(cornerRadius: OkcheRadius.radiusMd)
                        .strokeBorder(OkcheColor.lineStrong, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(QuickRecord.name(row.kind.rawValue)) 자세히 남기기")
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
                    NavigationLink { RecordLogView() } label: {
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
            .sheet(item: $editing) { RecordDetailSheet(target: $0) { _, _ in } }
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

/// 고치기(연필) — 자세히 시트가 있는 종류만(수라는 7-3).
struct EditRecordButton: View {
    let record: Record
    let pets: [Pet]
    var color = OkcheColor.ink
    let onEdit: (DetailTarget) -> Void

    var body: some View {
        if let kind = RecordKind(rawValue: record.kind), !RecordDetail.fields(kind).isEmpty {
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
    @State private var failed = false

    func body(content: Content) -> some View {
        content.fullScreenCover(item: $record) { target in
            OkcheConfirmDialog(title: "이 기록을 지울까요?", destructive: true, confirm: "지우기", confirmLook: .danger,
                               onCancel: { record = nil }, onConfirm: {
                                   do {
                                       try QuickRecord.delete(target, in: context)
                                       record = nil
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
                Text("지운 기록은 되돌릴 수 없고, 실록 통계에서도 빠져요.").okcheText(OkcheType.label, weight: .regular)
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
        .sheet(item: $editing) { RecordDetailSheet(target: $0) { _, _ in } }
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
