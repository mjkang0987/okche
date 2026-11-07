import SwiftData
import SwiftUI

/// 실록 탭(보드 `Sillok`·`SillokLocked`·`SillokEmpty`, plan.md 10-1). 계산은 `SillokRules`. 안드로이드 `SillokScreen`과 같은 화면.
/// 보기 설정(10-2)·바꾼 뒤 어때요(10-3)·당뇨 칸(10-4)·PDF(10-5)는 그 단계에서. 지금 보는 아이가 추억이어도 그대로(실록 탭은 기록 보기).
struct SillokView: View {
    let pet: Pet
    // shortcut: 기록 전체를 읽어 화면에서 거름(일기 탭과 같음), 기록이 수천 건이 되어 느려지면 `@Query`에 아이·날 조건을 넣는다.
    @Query(filter: #Predicate<Record> { $0.deletedAt == nil }) private var allRecords: [Record]
    @Query private var plans: [MealPlan]
    @Query private var rests: [RestPeriod]
    @Query private var foodChanges: [FoodChange]
    @Query private var foods: [Food] // 지운 사료도(이름은 남음 — 1-4)
    @Query(filter: #Predicate<MedSchedule> { $0.deletedAt == nil }) private var schedules: [MedSchedule]
    @Query private var meds: [Medication]
    @State private var range = SillokRules.Range.month
    @State private var back = 0
    @State private var locked = false
    @State private var now = Date.now
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        let period = SillokRules.period(today: OkcheDay.of(now), days: range.days ?? 30, back: back)
        let before = period.previous
        let current = SillokRules.records(allRecords, petId: pet.id, in: period)
        let previous = SillokRules.records(allRecords, petId: pet.id, in: before)
        ScrollView {
            VStack(alignment: .leading, spacing: OkcheSpacing.space4) {
                HStack { Spacer(minLength: 0); PetSeal() }.frame(minHeight: 44)
                SillokHeader(name: pet.name, headline: SillokRules.headline(period, range), period: SillokRules.text(period))
                SillokRangeChips(range: range, onPick: { range = $0; back = 0 }, onLocked: { locked = true })
                SillokPeriodNav(period: period, range: range, onPrev: { back += 1 }, onNext: { if back > 0 { back -= 1 } })
                if current.isEmpty {
                    Text("이 기간에 남긴 기록이 없어요. 일기에서 기록을 남기면 여기에 모여요.")
                        .okcheText(OkcheType.body).foregroundStyle(OkcheColor.inkMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(OkcheSpacing.space4)
                        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                } else {
                    let changes = SillokRules.changes(pet: pet, foodChanges: foodChanges, foods: foods, schedules: schedules, meds: meds, in: period)
                    summary(current, previous, period)
                    SillokVomitChart(records: current, period: period, changes: changes)
                    if let shares = SillokRules.stoolShares(current) { SillokStoolCard(shares: shares) }
                    if let urine = SillokRules.urine(current, days: period.days) {
                        SillokUrineCard(urine: urine, before: previous.isEmpty ? nil : SillokRules.urine(previous, days: before.days)?.perDay, range: range)
                    }
                    let weights = SillokRules.weightPoints(current)
                    if !weights.isEmpty { SillokWeightCard(points: weights, period: period) }
                    if !changes.isEmpty { SillokChangesCard(changes: changes) }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, OkcheSpacing.space6)
            .padding(.bottom, OkcheSpacing.space4)
        }
        .background(OkcheColor.hanji)
        // 켜 둔 채 6시를 넘기면 '오늘'이 새 날로, 앱으로 돌아오면 바로(문안과 같음).
        .task {
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(60)) } catch { break }
                now = .now
            }
        }
        .onChange(of: scenePhase) { if scenePhase == .active { now = .now } }
        // 자물쇠 안내 창(보드 `SillokLocked`) — '알아보기'는 14단계(결제) 전까지 숨김.
        .fullScreenCover(isPresented: $locked) {
            OkcheConfirmDialog(title: "여러 달은 전체 기간 열기로 볼 수 있어요", confirm: "그냥 둘게요", confirmLook: .outlined, showsCancel: false,
                               onCancel: { locked = false }, onConfirm: { locked = false }) {
                Text("무료로는 한 번에 30일씩 봐요. 지난 기록도 화살표로 30일씩 넘기면 다 볼 수 있어요.")
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                Text("3달·1년·전체를 한눈에 보거나 바꾼 뒤 4주를 비교하려면 전체 기간 열기(한 번 결제)가 필요해요.")
                    .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            }
            .presentationBackground(.clear)
        }
        .transaction { $0.disablesAnimations = locked }
    }

    /// 요약 세 칸: 구토·묽은 변·다 먹은 끼니(끼니 차례가 없던 기간이면 두 칸).
    private func summary(_ current: [Record], _ previous: [Record], _ period: SillokRules.Period) -> some View {
        let hasPrev = !previous.isEmpty
        let vomit = SillokRules.vomitCount(current)
        let loose = SillokRules.looseStoolCount(current)
        let meal = SillokRules.mealFraction(current, plans: plans, rests: rests, petId: pet.id, period, now: now)
        return HStack(alignment: .top, spacing: OkcheSpacing.space2) {
            SillokSummaryCell(label: "구토", value: "\(vomit)", unit: "회",
                              note: SillokRules.compare(vomit, SillokRules.vomitCount(previous), beforeHasRecords: hasPrev, range))
            SillokSummaryCell(label: "묽은 변", value: "\(loose)", unit: "회",
                              note: SillokRules.compare(loose, SillokRules.looseStoolCount(previous), beforeHasRecords: hasPrev, range))
            if let meal { SillokSummaryCell(label: "다 먹은 끼니", value: "\(meal.eaten)", unit: "/\(meal.total)", note: nil, easy: true) }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// '매화 · 변 상태'처럼 쉬운 말을 0.85배 회색으로.
private func sillokEasy(_ word: String, _ easy: String, size: CGFloat = 16) -> Text {
    Text(word) + Text(" · \(easy)").font(.system(size: size * 0.85, weight: .regular)).foregroundColor(OkcheColor.inkMuted)
}

/// 남색 금테 머리: '콩이실록 최근 30일' + '9/26 – 10/25'. VoiceOver는 한 머리말.
private struct SillokHeader: View {
    let name: String
    let headline: String
    let period: String

    var body: some View {
        VStack(spacing: 2) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) { title; sub }
                VStack(spacing: 2) { title; sub }
            }
            Text(period).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.onDeepMuted)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 28)
        .padding(.vertical, 21)
        .frame(maxWidth: .infinity, minHeight: 104)
        .background(OkcheColor.namsaekDeep)
        .overlay(GoldFrame(inset: 12).stroke(LinearGradient(colors: [OkcheColor.geumLight, OkcheColor.geum, OkcheColor.geumShade],
                                                            startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1.1)
            .accessibilityHidden(true))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var title: some View { Text("\(name)실록").okcheText(OkcheType.display).foregroundStyle(OkcheColor.geumLight) }
    private var sub: some View {
        Text(headline).okcheText(OkcheTextStyle(family: .display, size: 18, lineHeight: 26, weight: .bold)).foregroundStyle(OkcheColor.hanji)
    }
}

/// 빠른 선택: 1주·30일은 고르기, 3달·1년·전체는 자물쇠(누르면 안내 창).
private struct SillokRangeChips: View {
    let range: SillokRules.Range
    let onPick: (SillokRules.Range) -> Void
    let onLocked: () -> Void

    var body: some View {
        FlowLayout(spacing: 6) {
            ForEach(SillokRules.Range.allCases, id: \.self) { r in
                let isLocked = r.days == nil
                let selected = r == range
                Button { isLocked ? onLocked() : onPick(r) } label: {
                    HStack(spacing: 4) {
                        if isLocked { Image(systemName: "lock").font(.system(size: 12, weight: .semibold)) }
                        Text(r.title).okcheText(OkcheType.body, weight: selected ? .bold : .regular)
                    }
                    .foregroundStyle(selected ? OkcheColor.paper : isLocked ? OkcheColor.inkMuted : OkcheColor.ink)
                    .padding(.horizontal, isLocked ? 12 : 14)
                    .frame(minHeight: 40)
                    .background(selected ? OkcheColor.namsaek : OkcheColor.paper, in: Capsule())
                    .overlay(Capsule().strokeBorder(selected ? .clear : isLocked ? OkcheColor.line : OkcheColor.lineStrong, lineWidth: 1.5))
                    .padding(.vertical, 2) // 누르는 자리 44
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isLocked ? "\(r.title) · 잠김" : r.title)
                .accessibilityAddTraits(selected ? .isSelected : [])
                .accessibilityHint(isLocked ? "안내 보기" : "")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("기간")
    }
}

/// ‹ 이전 · '9/26 – 10/25' · 다음 › (오늘이 든 기간이면 다음 안 눌림). '보기 설정'은 10-2.
private struct SillokPeriodNav: View {
    let period: SillokRules.Period
    let range: SillokRules.Range
    let onPrev: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: OkcheSpacing.space2) {
            arrow("chevron.left", "이전 \(range.title) 보기", enabled: true, action: onPrev)
            Text(SillokRules.text(period)).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                .frame(maxWidth: .infinity)
            arrow("chevron.right", "다음 \(range.title) 보기", enabled: period.back > 0, action: onNext)
        }
    }

    private func arrow(_ icon: String, _ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(OkcheColor.ink)
                .frame(width: 40, height: 40)
                .background(OkcheColor.paper, in: Circle())
                .overlay(Circle().strokeBorder(OkcheColor.lineStrong, lineWidth: 1.5))
                .opacity(enabled ? 1 : 0.35)
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct SillokSummaryCell: View {
    let label: String
    let value: String
    let unit: String
    let note: String?
    var easy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            (Text(value).font(.system(size: 28, weight: .bold)) + Text(unit).font(.system(size: 14, weight: .medium)))
                .foregroundStyle(OkcheColor.ink)
            if easy {
                sillokEasy("수라", "식사", size: 12).okcheText(OkcheType.caption).foregroundStyle(OkcheColor.inkMuted)
            } else if let note {
                // 좋다·나쁘다 색 없이 남색 한 가지(늘었·줄었), 같으면 회색(보드).
                let same = note.hasSuffix("같아요")
                Text(note).okcheText(OkcheType.caption, weight: same ? .regular : .bold)
                    .foregroundStyle(same ? OkcheColor.inkMuted : OkcheColor.namsaekDeep)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(OkcheSpacing.space3)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusMd))
        .accessibilityElement(children: .combine)
    }
}

/// 흰 카드(모서리 20, 안 16).
private struct SillokCard<Content: View>: View {
    var spacing: CGFloat = OkcheSpacing.space3
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(OkcheSpacing.space4)
            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
    }
}

private func sillokCardTitle(_ text: Text) -> some View {
    text.okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink).accessibilityAddTraits(.isHeader)
}

/// 구토 횟수 막대: 하루 단위, 최근 날짜부터 보이고 옆으로 넘기면 지난 날. 바꾼 날 앞에 금색 선과 이름표(선 왼쪽 — 첫 이틀은 오른쪽, 12장).
private struct SillokVomitChart: View {
    let records: [Record]
    let period: SillokRules.Period
    let changes: [SillokRules.Change]
    private let area: CGFloat = 84
    private let labelTop: CGFloat = 18

    var body: some View {
        let counts = SillokRules.dailyVomits(records, period)
        let top = SillokRules.barTop(counts)
        let marks = SillokRules.marks(changes)
        let days = period.dayList
        SillokCard {
            sillokCardTitle(Text("구토 횟수 ") + Text("하루 단위").font(.system(size: 14, weight: .regular)).foregroundColor(OkcheColor.inkMuted))
            HStack(alignment: .top, spacing: 6) {
                VStack(alignment: .trailing) {
                    Text("\(top)"); Spacer(minLength: 0); Text("\(top / 2)"); Spacer(minLength: 0); Text("0")
                }
                .font(.system(size: 12)).foregroundStyle(OkcheColor.inkMuted)
                .frame(height: area).padding(.top, labelTop)
                .accessibilityHidden(true)
                ScrollView(.horizontal, showsIndicators: true) {
                    HStack(alignment: .top, spacing: 4) {
                        ForEach(Array(days.enumerated()), id: \.element) { i, day in
                            if let mark = marks[day] { changeLine(mark, labelRight: i < 2) }
                            bar(counts[i], top: top, label: dayLabel(day, i))
                        }
                    }
                    .background(alignment: .topLeading) {
                        // 가운데·0 눈금 선.
                        VStack(spacing: 0) {
                            Color.clear.frame(height: labelTop + area / 2)
                            OkcheColor.line.frame(height: 1)
                            Color.clear.frame(height: area / 2 - 1)
                            OkcheColor.line.frame(height: 1)
                        }
                    }
                    .padding(.bottom, 6)
                }
                .defaultScrollAnchor(.trailing) // 처음엔 최근 날짜(오른쪽 끝)부터
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(SillokRules.vomitChartLabel(period, total: counts.reduce(0, +), changes: changes))
            }
            Text("최근 날짜부터 보여요. 옆으로 넘기면 지난 날짜가 나와요. 금색 선은 사료·탕약을 바꾼 날이에요.")
                .okcheText(OkcheType.caption).foregroundStyle(OkcheColor.inkMuted)
        }
    }

    /// 막대 아래 날짜: 첫날과 매달 1일은 '10/1', 나머지는 날만.
    private func dayLabel(_ day: String, _ index: Int) -> String {
        let parts = day.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return day }
        return index == 0 || parts[2] == 1 ? "\(parts[1])/\(parts[2])" : "\(parts[2])"
    }

    private func bar(_ count: Int, top: Int, label: String) -> some View {
        VStack(spacing: 4) {
            ZStack(alignment: .bottom) {
                Color.clear.frame(width: 14, height: area)
                if count == 0 {
                    OkcheColor.line.frame(width: 14, height: 2)
                } else {
                    UnevenRoundedRectangle(topLeadingRadius: 3, topTrailingRadius: 3).fill(OkcheColor.namsaek)
                        .frame(width: 14, height: area * CGFloat(count) / CGFloat(top))
                }
            }
            Text(label).font(.system(size: 12)).foregroundStyle(OkcheColor.inkMuted).lineLimit(1).fixedSize()
        }
        .frame(width: 22)
        .padding(.top, labelTop)
    }

    /// 금색 세로선(2 × 84)과 이름표 — 이름표는 자리를 차지하지 않고 선 옆에 그린다.
    private func changeLine(_ label: String, labelRight: Bool) -> some View {
        OkcheColor.geum.frame(width: 2, height: area)
            .overlay(alignment: labelRight ? .topLeading : .topTrailing) {
                Text(label).font(.system(size: 12, weight: .bold)).foregroundStyle(OkcheColor.geumDeep)
                    .fixedSize()
                    .offset(x: labelRight ? 6 : -6, y: -labelTop)
            }
            .padding(.top, labelTop)
    }
}

/// 매화 변 상태 비율 막대 + 범례.
private struct SillokStoolCard: View {
    let shares: [(band: SillokRules.StoolBand, percent: Int)]

    private func color(_ band: SillokRules.StoolBand) -> Color {
        switch band {
        case .hard: OkcheColor.lineStrong
        case .normal: OkcheColor.namsaekDeep
        case .soft: OkcheColor.namsaek
        case .watery: OkcheColor.namsaekSoft
        }
    }

    var body: some View {
        SillokCard {
            sillokCardTitle(sillokEasy("매화", "변 상태"))
            GeometryReader { geo in
                HStack(spacing: 0) {
                    ForEach(shares.filter { $0.percent > 0 }, id: \.band) { share in
                        color(share.band).frame(width: geo.size.width * CGFloat(share.percent) / 100)
                    }
                }
            }
            .frame(height: 20)
            .clipShape(Capsule())
            .overlay(Capsule().strokeBorder(OkcheColor.lineStrong, lineWidth: 1))
            .accessibilityHidden(true)
            FlowLayout(spacing: 12) {
                ForEach(shares, id: \.band) { share in
                    HStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 3).fill(color(share.band)).frame(width: 12, height: 12)
                            .overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(share.band == .watery ? OkcheColor.lineStrong : .clear, lineWidth: 1))
                        Text("\(share.band.title) \(share.percent)%").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.ink)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}

/// 소피: 하루 평균 n번 + 직전 기간 평균 + 찔끔·분홍·붉은색·힘만 줌 횟수(안내 문구 없음 — 정한 것 ②).
private struct SillokUrineCard: View {
    let urine: SillokRules.Urine
    let before: Double?
    let range: SillokRules.Range

    var body: some View {
        SillokCard(spacing: 6) {
            sillokCardTitle(sillokEasy("소피", "소변"))
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) { average; Spacer(minLength: 0); beforeText }
                VStack(alignment: .leading, spacing: 4) { average; beforeText }
            }
            Text("찔끔 \(urine.little)번 · 분홍·붉은색 \(urine.pinkRed)번 · 힘만 줌 \(urine.straining)번")
                .okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
        .accessibilityElement(children: .combine)
    }

    private var average: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(SillokRules.perDayText(urine.perDay)).okcheText(OkcheType.number)
            Text("번 / 하루 평균").okcheText(OkcheType.label, weight: .regular)
        }
        .foregroundStyle(OkcheColor.ink)
    }

    @ViewBuilder private var beforeText: some View {
        if let before {
            Text("직전 \(range.title) \(SillokRules.perDayText(before))번").okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
        }
    }
}

/// 옥체 체중선: 기간 안 체중 기록, 마지막 값 크게. 기록 하나면 점 하나.
private struct SillokWeightCard: View {
    let points: [(at: Date, grams: Int)]
    let period: SillokRules.Period

    var body: some View {
        let last = points.last?.grams ?? 0
        let label = points.count == 1 ? "체중 \(SillokRules.kgNumber(last))kg"
            : "체중 변화: \(SillokRules.kgNumber(points.first?.grams ?? 0))kg에서 \(SillokRules.kgNumber(last))kg"
        SillokCard(spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                sillokCardTitle(sillokEasy("옥체", "체중"))
                Spacer(minLength: 0)
                (Text(SillokRules.kgNumber(last)).font(.system(size: 32, weight: .bold)) + Text(" kg").font(.system(size: 14)))
                    .foregroundStyle(OkcheColor.ink)
            }
            line.frame(height: 90).accessibilityElement().accessibilityLabel(label).accessibilityAddTraits(.isImage)
            HStack {
                ForEach(Array(SillokRules.weightAxis(period).enumerated()), id: \.offset) { i, text in
                    if i > 0 { Spacer(minLength: 0) }
                    Text(text)
                }
            }
            .font(.system(size: 12)).foregroundStyle(OkcheColor.inkMuted)
            .accessibilityHidden(true)
        }
    }

    /// 가로 = 기간 안 시각 비율(기간 첫날 6시 ~ 끝 다음 날 6시), 세로 = 기간 안 최소·최대.
    private var line: some View {
        let calendar = Calendar.okcheDevice
        let from = PetRegistration.date(period.start, calendar).flatMap { calendar.date(bySettingHour: OkcheDay.startHour, minute: 0, second: 0, of: $0) } ?? .distantPast
        let to = PetRegistration.date(QuickRecord.shift(period.end, by: 1), calendar)
            .flatMap { calendar.date(bySettingHour: OkcheDay.startHour, minute: 0, second: 0, of: $0) } ?? .distantFuture
        let lo = points.map(\.grams).min() ?? 0
        let hi = points.map(\.grams).max() ?? 0
        return Canvas { context, size in
            var base = Path()
            base.move(to: CGPoint(x: 0, y: 80))
            base.addLine(to: CGPoint(x: size.width, y: 80))
            context.stroke(base, with: .color(OkcheColor.line), lineWidth: 1)
            let span = max(to.timeIntervalSince(from), 1)
            func x(_ at: Date) -> CGFloat { 10 + (size.width - 20) * CGFloat(min(max(at.timeIntervalSince(from) / span, 0), 1)) }
            func y(_ g: Int) -> CGFloat { hi == lo ? 40 : 60 - 40 * CGFloat(g - lo) / CGFloat(hi - lo) }
            if points.count > 1 {
                var path = Path()
                for (i, p) in points.enumerated() {
                    if i == 0 { path.move(to: CGPoint(x: x(p.at), y: y(p.grams))) } else { path.addLine(to: CGPoint(x: x(p.at), y: y(p.grams))) }
                }
                context.stroke(path, with: .color(OkcheColor.namsaek), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
            }
            if let last = points.last {
                context.fill(Path(ellipseIn: CGRect(x: x(last.at) - 5, y: y(last.grams) - 5, width: 10, height: 10)), with: .color(OkcheColor.namsaek))
            }
        }
    }
}

/// 이 기간에 바뀐 것(6-4) — '바꾼 뒤 어때요 ›'는 10-3에서.
private struct SillokChangesCard: View {
    let changes: [SillokRules.Change]

    var body: some View {
        SillokCard(spacing: 4) {
            sillokCardTitle(Text("이 기간에 바뀐 것"))
            ForEach(Array(changes.enumerated()), id: \.offset) { i, change in
                if i > 0 { OkcheColor.line.frame(height: 1) }
                HStack(spacing: 8) {
                    Circle().fill(OkcheColor.geum).frame(width: 10, height: 10)
                    Text(change.text).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.ink)
                }
                .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                .accessibilityElement(children: .combine)
            }
        }
    }
}
