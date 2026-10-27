import SwiftUI

/// 탭 첫 화면 틀: 제목 28 + 쉬운 말 14(디자인 '화면 짜임새' 규칙), 한지 바탕, 좌우 16.
struct TabScreen<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OkcheSpacing.space4) {
                HStack(alignment: .firstTextBaseline, spacing: OkcheSpacing.space2) {
                    Text(title)
                        .okcheText(OkcheType.display)
                        .foregroundStyle(OkcheColor.ink)
                        .accessibilityAddTraits(.isHeader)
                    if let subtitle {
                        Text(subtitle)
                            .okcheText(OkcheType.label)
                            .fontWeight(.regular)
                            .foregroundStyle(OkcheColor.inkMuted)
                    }
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
