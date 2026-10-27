import SwiftUI

@main
struct OkcheApp: App {
    /// 디자인의 탭 막대: 흰 바탕, 위 가는 선(line), 안 고른 탭은 inkMuted. 앱 전체 설정이라 앱 시작 때 한 번.
    init() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(OkcheColor.paper)
        appearance.shadowColor = UIColor(OkcheColor.line)
        let item = appearance.stackedLayoutAppearance
        item.normal.iconColor = UIColor(OkcheColor.inkMuted)
        // 탭 글자 12(caption), 고른 탭만 굵게 — 디자인과 같게.
        item.normal.titleTextAttributes = [
            .foregroundColor: UIColor(OkcheColor.inkMuted),
            .font: UIFont.systemFont(ofSize: OkcheType.caption.size),
        ]
        item.selected.iconColor = UIColor(OkcheColor.namsaek)
        item.selected.titleTextAttributes = [
            .foregroundColor: UIColor(OkcheColor.namsaek),
            .font: UIFont.systemFont(ofSize: OkcheType.caption.size, weight: .bold),
        ]
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
        }
    }
}
