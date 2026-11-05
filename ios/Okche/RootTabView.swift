import SwiftUI

/// 하단 탭 5개. 순서는 디자인 기준(일기 → 문안 → 실록 → 어진첩 → 더보기), 첫 화면은 일기.
struct RootTabView: View {
    @State private var tab = AppTab.record
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.modelContext) private var context

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
        .onChange(of: scenePhase, initial: true) { if scenePhase == .active { try? MealRules.fillFeeder(in: context) } }
    }
}
