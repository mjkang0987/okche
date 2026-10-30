import SwiftData
import SwiftUI

/// 첫 화면: 아이가 없고 '먼저 둘러볼게요'를 누른 적 없으면 시작하기, 아니면 탭.
struct RootView: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }) private var pets: [Pet]
    @AppStorage("onboardingDone") private var onboardingDone = false

    var body: some View {
        if pets.isEmpty && !onboardingDone {
            OnboardingView()
        } else {
            RootTabView()
        }
    }
}
