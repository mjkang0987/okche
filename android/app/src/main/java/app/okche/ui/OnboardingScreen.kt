package app.okche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType

/**
 * 첫 실행 '시작하기'(디자인 보드 Onboarding). 큰 글자로 넘치면 스크롤하고, 남으면 버튼을 아래에 붙인다(margin-top: auto).
 * 낮은 화면(360×640 등)에선 위 여백 72를 24로 줄여 주 버튼이 첫 화면에 보이게 한다.
 */
@Composable
fun OnboardingScreen(onRegister: () -> Unit, onBrowse: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                // 위 72·묶음 사이 28·아이콘 줄 14는 보드 값(토큰에 없음).
                .padding(
                    start = OkcheSpacing.Space4, end = OkcheSpacing.Space4,
                    top = if (maxHeight < 720.dp) OkcheSpacing.Space6 else 72.dp, bottom = OkcheSpacing.Space6,
                ),
        ) {
            BrandPlaque()
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.semantics {
                    collectionInfo = CollectionInfo(3, 1)
                    contentDescription = "옥체로 할 수 있는 일"
                },
                verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
            ) {
                Feature(Icons.Outlined.Description, "승정원일기", "구토·변·약을 한 번 눌러 기록해요")
                Feature(Icons.Outlined.BarChart, "실록", "병원에 그대로 보여 줄 기록이 쌓여요")
                Feature(Icons.Outlined.Image, "어진첩", "사진과 나이가 함께 남아요")
            }
            // 카드와 버튼 사이 최소 28, 남는 높이는 그 아래 빈칸이 가져가 버튼이 바닥에 붙는다(margin-top: auto).
            // 버튼 묶음에 weight를 주면 스크롤 안에서 내용이 화면보다 길 때 묶음 높이가 0이 돼 버튼이 잘린다.
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OkchePrimaryButton("아이 등록하고 시작하기", onRegister)
                TextButton(onBrowse, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("먼저 둘러볼게요", color = OkcheColor.NamsaekDeep, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
                }
                Text(
                    "기록은 이 기기에 저장돼요. 백업은 더보기에서 켤 수 있어요.",
                    Modifier.fillMaxWidth().padding(top = OkcheSpacing.Space1),
                    color = OkcheColor.InkMuted,
                    style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// 금테 모서리 꽃살(보드 SVG 그대로, 12·12 기준 dp 좌표).
private val cornerPath = PathParser().parsePathString("M-3 -9H3V-3H9V3H3V9H-3V3H-9V-3H-3Z M-3 -3H3V3H-3Z").toPath()

/** 금테 남색 현판: '옥체'(고운바탕 56 — 이 화면만의 예외) + 한 줄 소개. */
@Composable
private fun BrandPlaque() {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .background(OkcheColor.NamsaekDeep)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    0f to OkcheColor.GeumLight, 0.5f to OkcheColor.Geum, 1f to OkcheColor.GeumShade,
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val w = size.width / density
                val h = size.height / density
                val frame = Path().apply {
                    moveTo(21f, 12f); lineTo(w - 21, 12f)
                    moveTo(21f, h - 12); lineTo(w - 21, h - 12)
                    moveTo(12f, 21f); lineTo(12f, h - 21)
                    moveTo(w - 12, 21f); lineTo(w - 12, h - 21)
                    for ((x, y) in listOf(12f to 12f, w - 12 to 12f, 12f to h - 12, w - 12 to h - 12)) {
                        addPath(cornerPath, Offset(x, y))
                    }
                }
                onDrawBehind {
                    // dp 좌표로 그리려고 density만큼 키운다. 선 굵기도 같이 커지므로 1.1dp.
                    scale(density, pivot = Offset.Zero) { drawPath(frame, brush, style = Stroke(1.1f)) }
                }
            },
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val brand = OkcheType.Display.toTextStyle().copy(fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = 6.sp)
        Text(
            "옥체",
            Modifier.singleLineHeight(brand.lineHeight).semantics { heading() },
            color = OkcheColor.GeumLight,
            style = brand,
            maxLines = 1,
        )
        Text(
            "우리 아이의 하루를, 왕의 기록처럼",
            Modifier.padding(horizontal = OkcheSpacing.Space6),
            color = OkcheColor.OnDeepMuted,
            style = OkcheType.Body.toTextStyle(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, description: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
            .padding(OkcheSpacing.Space4)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusSm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = OkcheColor.NamsaekDeep)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
            Text(description, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
    }
}
