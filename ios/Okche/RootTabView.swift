import SwiftData
import SwiftUI

/// 하단 탭 5개. 순서는 디자인 기준(일기 → 문안 → 실록 → 어진첩 → 더보기), 첫 화면은 일기.
struct RootTabView: View {
    @State private var tab = AppTab.record
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.modelContext) private var context
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil && $0.memorializedAt == nil }) private var alive: [Pet]
    @State private var router = NotifyRouter.shared
    @State private var rescheduling: Task<Void, Never>?

    var body: some View {
        TabView(selection: $tab) {
            // 디자인 아이콘은 선 모양이라, 시스템이 탭 아이콘을 채운 모양으로 바꾸지 않게 탭마다 symbolVariants를 끈다.
            RecordTab()
                .undoToastHost()
                .tabItem { Label("일기", systemImage: "doc.text").environment(\.symbolVariants, .none) }
                .tag(AppTab.record)
            MainTab()
                .undoToastHost()
                .tabItem { Label("문안", systemImage: "heart").environment(\.symbolVariants, .none) }
                .tag(AppTab.main)
            SillokTab()
                .undoToastHost()
                .tabItem { Label("실록", systemImage: "chart.bar").environment(\.symbolVariants, .none) }
                .tag(AppTab.sillok)
            AlbumTab()
                .undoToastHost()
                .tabItem { Label("어진첩", systemImage: "photo").environment(\.symbolVariants, .none) }
                .tag(AppTab.album)
            MoreTab()
                .undoToastHost()
                .tabItem { Label("더보기", systemImage: "ellipsis").environment(\.symbolVariants, .none) }
                .tag(AppTab.more)
        }
        .environment(\.openTab) { tab = $0 }
        // 앱을 열 때·다시 앞으로 올 때 급식기 지난 끼니를 채운다(7-3).
        .onChange(of: scenePhase, initial: true) {
            guard scenePhase == .active else { return }
            try? MealRules.fillFeeder(in: context)
            reschedule()
        }
        // 기록·약·설정·휴양이 저장될 때마다 탕약 알림을 다시 건다(8-4b). 잇따른 저장은 한 번으로.
        .onReceive(NotificationCenter.default.publisher(for: ModelContext.didSave)) { _ in reschedule() }
        // 알림을 누르면 그 아이의 오늘 먹일 약 시트(정한 것 ①). 추억·지운 아이면 앱만 열림.
        .sheet(item: Binding(get: { alive.first { $0.id == router.openPetId } },
                             set: { if $0 == nil { router.openPetId = nil } })) { pet in
            MedTodaySheet(pet: pet)
        }
        // 남은 약 알림을 누르면 더보기 탭(약방·그 약 고치기는 더보기가 연다, 8-4c).
        .onChange(of: router.openMedId, initial: true) { if router.openMedId != nil { tab = .more } }
        // 추억·지운 아이 알림이면 비워 둔다(나중에 되돌려도 시트가 갑자기 뜨지 않게).
        .onChange(of: router.openPetId) { if let id = router.openPetId, !alive.contains(where: { $0.id == id }) { router.openPetId = nil } }
    }

    private func reschedule() {
        let previous = rescheduling
        previous?.cancel()
        rescheduling = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            // 걸던 중인 앞 차례가 끝난 뒤에(옛 목록을 늦게 더해 지운 알림이 되살아나지 않게).
            await previous?.value
            await MedReminders.reschedule(in: context)
        }
    }
}
