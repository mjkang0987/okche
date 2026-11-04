import SwiftData
import SwiftUI

/// 문안 탭(보드 `Main`·`MainEmpty`·`MainNoPet`): 인사, 아이 카드, '기록하기', 오늘의 승정원일기. 지금 보는 아이가 추억이면 추억 화면(6-4).
struct MainTab: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @Environment(\.openTab) private var openTab
    @State private var registering = false
    /// 문안 안 화면 쌓기(수라 줄 → 그 아이 수라 화면). 일기 탭과 같은 까닭으로 경로로.
    @State private var path: [RecordRoute] = []
    @State private var now = Date.now
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        if let pet = PetTitle.current(pets, lastPetId: lastPetId), pet.memorializedAt != nil {
            // 추억 화면으로 바뀌면 문안 안 화면(수라)을 비운다 — 다시 돌아왔을 때 수라가 저절로 뜨지 않게.
            MemorialView(pet: pet).onAppear { path = [] }
        } else {
            NavigationStack(path: $path) {
                let day = OkcheDay.of(now)
                TabScreen(title: "문안", subtitle: MainCard.dayTitle(day), trailing: { PetSeal() }) {
                    if let pet = PetTitle.current(pets, lastPetId: lastPetId) {
                        Text("\(PetTitle.name(pet, today: now)), 오늘도 강녕하시옵소서")
                            .okcheText(greeting).foregroundStyle(OkcheColor.namsaekDeep)
                            .padding(.top, -6)
                        MainPetCard(pet: pet, today: now)
                        FoodCheckCard(pet: pet, day: day)
                        Button("기록하기") { openTab(.record) }.buttonStyle(.okchePrimary)
                        TodayRecordSection(petId: pet.id, day: day)
                    } else {
                        Text("아직 모신 아이가 없어요. 아기씨를 모시면 여기에서 날마다 안부를 여쭤요.")
                            .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                        Button("+ 새 아기씨 모시기") { registering = true }.buttonStyle(.okchePrimary(.dashed))
                    }
                }
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: RecordRoute.self) { route in
                    switch route {
                    case .sura(let petId): SuraScreen(petId: petId)
                    case .log: RecordLogView()
                    }
                }
            }
            .fullScreenCover(isPresented: $registering) { PetRegisterView() }
            // 켜 둔 채 6시를 넘기면 날짜·오늘 기록이 새 날로, 앱으로 돌아오면 바로.
            .task {
                while !Task.isCancelled {
                    do { try await Task.sleep(for: .seconds(60)) } catch { break }
                    now = .now
                }
            }
            .onChange(of: scenePhase) { if scenePhase == .active { now = .now } }
        }
    }

    /// 인사 16 고운바탕(디자인 값, 토큰에 16 고운바탕이 없음).
    private let greeting = OkcheTextStyle(family: .display, size: 16, lineHeight: 22, weight: .regular)
}

/// 아이 카드(보드 `Main`): 남색 바탕 금테 148, 프로필 사진 자리 96(사진은 11단계), 표시 알약·호칭·동행·종 줄. 큰 글자에선 사진 아래로 글자.
struct MainPetCard: View {
    let pet: Pet
    let today: Date
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        // 글자 크기로 정한다(폭으로 정하면 작은 폰 보통 글자에서도 세로가 됨) — 안드로이드 글자 1.3배와 맞춤.
        Group {
            if typeSize >= .xxxLarge {
                VStack(alignment: .leading, spacing: 12) { photo; texts }
            } else {
                HStack(spacing: 16) { photo; texts }
            }
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 21) // 금테(12 안쪽 + 매듭 9) 안에 머물면서 알약이 있어도 148
        .frame(maxWidth: .infinity, minHeight: 148, alignment: .leading)
        .background(OkcheColor.namsaekDeep)
        .overlay(GoldFrame(inset: 12).stroke(LinearGradient(colors: [OkcheColor.geumLight, OkcheColor.geum, OkcheColor.geumShade],
                                                            startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1.1)
            .accessibilityHidden(true))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(pet.name) 카드")
    }

    private var photo: some View {
        // 자리는 96 그대로라 큰 글자에선 글자를 줄여 넣는다.
        Text("프로필 사진").okcheText(OkcheType.caption).foregroundStyle(OkcheColor.geumLight)
            .lineLimit(1).minimumScaleFactor(0.4).padding(.horizontal, 4)
            .frame(width: 96, height: 96)
            .background(OkcheColor.namsaek)
            .overlay(Rectangle().strokeBorder(OkcheColor.geum, lineWidth: 1))
            .accessibilityElement()
            .accessibilityLabel("\(pet.name) 프로필 사진")
            .accessibilityAddTraits(.isImage)
    }

    private var texts: some View {
        VStack(alignment: .leading, spacing: 3) {
            if let pill = MainCard.pill(birthDate: pet.birthDate, birthEstimated: pet.birthEstimated, familyDate: pet.familyDate,
                                     day: PetRegistration.day(today, .okcheDevice)) {
                Text(pill).okcheText(OkcheType.caption, weight: .bold).foregroundStyle(OkcheColor.ink)
                    .padding(.vertical, 2).padding(.horizontal, 10)
                    .background(OkcheColor.geum, in: Capsule())
            }
            Text(PetTitle.name(pet, today: today)).okcheText(OkcheType.title).foregroundStyle(OkcheColor.geumLight)
                .accessibilityAddTraits(.isHeader)
            if let days = PetEditing.companionDays(pet.familyDate, today: today) {
                Text("동행 \(days.formatted())일").okcheText(OkcheType.body).foregroundStyle(OkcheColor.hanji)
            }
            Text(MainCard.detail(pet, today: today)).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.onDeepMuted)
        }
    }
}

/// 오늘의 승정원일기(보드 `Main`·`MainEmpty`): 그 아이의 오늘 기록, 줄을 누르면 고치기 시트(수라는 수라 화면).
struct TodayRecordSection: View {
    let petId: UUID
    let day: String
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    // shortcut: 지우지 않은 기록 전체를 읽어 화면에서 거름, 기록이 수천 건이 되어 느려지면 `@Query`에 날 조건을 넣는다.
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }, sort: \Record.occurredAt) private var records: [Record]
    @State private var editing: DetailTarget?
    @State private var snackEditing: Record?
    @ScaledMetric(relativeTo: .body) private var plainSize: CGFloat = 13.6
    @ScaledMetric(relativeTo: .body) private var rowHeight: CGFloat = 52
    private let titleStyle = OkcheTextStyle(family: .display, size: 18, lineHeight: 26, weight: .bold)

    var body: some View {
        let rows = MainCard.todayRecords(records, petId: petId, day: day)
        VStack(alignment: .leading, spacing: 4) {
            // 큰 글자에서 한 줄에 안 들어가면 '오늘 기록'을 아래로(제목이 낱말 가운데서 끊기지 않게).
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) { title; caption }
                VStack(alignment: .leading, spacing: 2) { title; caption }
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            if rows.isEmpty {
                Text("아직 오늘 남긴 기록이 없어요.").okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                    .padding(.top, 8).padding(.bottom, 4)
            } else {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, record in
                    row(record)
                        .overlay(alignment: .bottom) {
                            if index < rows.count - 1 { Rectangle().fill(OkcheColor.line).frame(height: 1) }
                        }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .sheet(item: $editing) { RecordDetailSheet(target: $0) { _, _ in } }
        .sheet(item: $snackEditing) { record in
            if let pet = pets.first(where: { $0.id == petId }) { SnackRecordSheet(pet: pet, editing: record) }
        }
    }

    private var title: some View {
        Text("오늘의 승정원일기").okcheText(titleStyle).foregroundStyle(OkcheColor.ink).lineLimit(1).minimumScaleFactor(0.5)
    }

    private var caption: some View {
        Text("오늘 기록").okcheText(OkcheType.caption).foregroundStyle(OkcheColor.inkMuted)
    }

    @ViewBuilder private func row(_ record: Record) -> some View {
        let value = QuickRecord.value(record)
        let label = "\(QuickRecord.time(record)) \(QuickRecord.name(record.kind))\(value.isEmpty ? "" : " \(value)") · 고치기"
        if record.kind == RecordKind.snack.rawValue, pets.contains(where: { $0.id == petId }) {
            Button { snackEditing = record } label: { rowContent(record, value) }
                .buttonStyle(.plain)
                .accessibilityLabel(label)
        } else if record.kind == RecordKind.meal.rawValue {
            NavigationLink(value: RecordRoute.sura(petId)) { rowContent(record, value) }
                .buttonStyle(.plain)
                .accessibilityLabel(label)
        } else if let kind = RecordKind(rawValue: record.kind) {
            Button { editing = DetailTarget(kind: kind, editing: record) } label: { rowContent(record, value) }
                .buttonStyle(.plain)
                .accessibilityLabel(label)
        }
    }

    private func rowContent(_ record: Record, _ value: String) -> some View {
        HStack(spacing: 8) {
            (Text(QuickRecord.time(record)).foregroundColor(OkcheColor.inkMuted) + Text("  \(QuickRecord.name(record.kind))")
                + Text(MainCard.plainWord(record.kind).map { " · \($0)" } ?? "").font(.system(size: plainSize)).foregroundColor(OkcheColor.inkMuted))
                .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(value).okcheText(OkcheType.body, weight: .medium).foregroundStyle(OkcheColor.ink)
            Image(systemName: "pencil").font(.body).foregroundStyle(OkcheColor.namsaek).accessibilityHidden(true)
        }
        .multilineTextAlignment(.leading)
        .frame(minHeight: rowHeight)
        .contentShape(Rectangle())
    }
}
