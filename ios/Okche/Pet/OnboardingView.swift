import SwiftUI

/// 첫 실행 · 시작하기 (디자인 보드 `Onboarding`). 아이가 없고 '먼저 둘러볼게요'를 누른 적 없을 때.
struct OnboardingView: View {
    @AppStorage("onboardingDone") private var onboardingDone = false
    @State private var registering = false

    var body: some View {
        GeometryReader { screen in
        ScrollView {
            VStack(spacing: 28) {
                brand
                features
                Spacer(minLength: 0) // 디자인: 버튼 묶음은 화면 아래에 붙는다
                VStack(spacing: 10) {
                    Button("아이 등록하고 시작하기") { registering = true }
                        .buttonStyle(.okchePrimary)
                    Button { onboardingDone = true } label: {
                        Text("먼저 둘러볼게요")
                            .okcheText(OkcheType.body, weight: .medium)
                            .foregroundStyle(OkcheColor.namsaekDeep)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Text("기록은 이 기기에 저장돼요. 백업은 더보기에서 켤 수 있어요.")
                        .okcheText(OkcheType.label, weight: .regular)
                        .foregroundStyle(OkcheColor.inkMuted)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, 72 - 44) // 보드는 상태 막대 높이(44)를 포함한 72
            .padding(.bottom, OkcheSpacing.space6)
            .frame(minHeight: screen.size.height)
        }
        }
        .background(OkcheColor.hanji)
        .fullScreenCover(isPresented: $registering) { PetRegisterView() }
    }

    private var brand: some View {
        VStack(spacing: 8) {
            Text("옥체")
                .font(.custom(OkcheFontFamily.display.fontName ?? "", size: 56, relativeTo: .largeTitle))
                .tracking(6)
                .foregroundStyle(OkcheColor.geumLight)
                .accessibilityAddTraits(.isHeader)
            Text("우리 아이의 하루를, 왕의 기록처럼")
                .okcheText(OkcheType.body)
                .foregroundStyle(OkcheColor.onDeepMuted)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, minHeight: 180)
        .background(OkcheColor.namsaekDeep)
        .overlay(GoldFrame(inset: 12).stroke(goldGradient, lineWidth: 1.1).accessibilityHidden(true))
        .accessibilityElement(children: .combine)
    }

    private var features: some View {
        VStack(spacing: 12) {
            feature("doc.text", "승정원일기", "구토·변·약을 한 번 눌러 기록해요")
            feature("chart.bar", "실록", "병원에 그대로 보여 줄 기록이 쌓여요")
            feature("photo", "어진첩", "사진과 나이가 함께 남아요")
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("옥체로 할 수 있는 일")
    }

    private func feature(_ icon: String, _ title: String, _ detail: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .environment(\.symbolVariants, .none)
                .foregroundStyle(OkcheColor.namsaekDeep)
                .frame(width: 44, height: 44)
                .background(OkcheColor.namsaekSoft, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusSm))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).okcheText(OkcheType.body, weight: .bold).foregroundStyle(OkcheColor.ink)
                Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .accessibilityElement(children: .combine)
    }

    private var goldGradient: LinearGradient {
        LinearGradient(colors: [OkcheColor.geumLight, OkcheColor.geum, OkcheColor.geumShade],
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

/// 금테: 안쪽으로 `inset`만큼 들어간 네 변과 모서리 亞자 매듭(디자인 보드의 SVG와 같은 모양).
private struct GoldFrame: Shape {
    let inset: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let l = inset, t = inset, r = rect.width - inset, b = rect.height - inset
        path.move(to: CGPoint(x: l + 9, y: t)); path.addLine(to: CGPoint(x: r - 9, y: t))
        path.move(to: CGPoint(x: l + 9, y: b)); path.addLine(to: CGPoint(x: r - 9, y: b))
        path.move(to: CGPoint(x: l, y: t + 9)); path.addLine(to: CGPoint(x: l, y: b - 9))
        path.move(to: CGPoint(x: r, y: t + 9)); path.addLine(to: CGPoint(x: r, y: b - 9))
        for corner in [CGPoint(x: l, y: t), CGPoint(x: r, y: t), CGPoint(x: l, y: b), CGPoint(x: r, y: b)] {
            // 亞자: 십자 바깥선 + 가운데 네모
            let cross: [(CGFloat, CGFloat)] = [(-3, -9), (3, -9), (3, -3), (9, -3), (9, 3), (3, 3), (3, 9), (-3, 9),
                                               (-3, 3), (-9, 3), (-9, -3), (-3, -3)]
            path.addLines(cross.map { CGPoint(x: corner.x + $0.0, y: corner.y + $0.1) })
            path.closeSubpath()
            path.addRect(CGRect(x: corner.x - 3, y: corner.y - 3, width: 6, height: 6))
        }
        return path
    }
}
