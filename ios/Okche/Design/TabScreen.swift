import SwiftUI

/// 탭 첫 화면 틀: 제목 28 + 쉬운 말 14(디자인 '화면 짜임새' 규칙), 한지 바탕, 좌우 16.
struct TabScreen<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var content: Content

    @ViewBuilder private var header: some View {
        Text(title)
            .okcheText(OkcheType.display)
            .foregroundStyle(OkcheColor.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .accessibilityAddTraits(.isHeader)
        if let subtitle {
            Text(subtitle)
                .okcheText(OkcheType.label)
                .fontWeight(.regular)
                .foregroundStyle(OkcheColor.inkMuted)
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OkcheSpacing.space4) {
                // 큰 글자 설정에서 한 줄에 안 들어가면 쉬운 말을 아래로 내린다(제목이 글자 중간에서 끊기지 않게).
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: OkcheSpacing.space2) { header }
                    VStack(alignment: .leading, spacing: OkcheSpacing.space1) { header }
                }
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, OkcheSpacing.space6)
            .padding(.bottom, OkcheSpacing.space4)
        }
        .background(OkcheColor.hanji)
    }
}

extension TabScreen where Content == EmptyView {
    init(title: String, subtitle: String? = nil) {
        self.init(title: title, subtitle: subtitle) { EmptyView() }
    }
}
