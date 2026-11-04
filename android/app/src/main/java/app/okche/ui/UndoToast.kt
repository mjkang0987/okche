package app.okche.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheType
import kotlinx.coroutines.delay

/**
 * 탭 바 위 되돌리기 알림(보드 `RecordMedGiven`, 2026-11-04 승인): 남색 띠에 글자 + 금색 '되돌리기'.
 * 5초 뒤 사라지고(화면 읽기가 켜져 있으면 10초) 화면 읽기로도 알린다. 9단계 인슐린 건너뜀 알림도 이걸 쓴다. [message]가 null이면 숨김.
 */
@Composable
fun UndoToast(message: String?, onDismiss: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    // 사라지는 동안에도 글자가 남게 마지막 글자를 쥔다.
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        @Suppress("DEPRECATION") view.announceForAccessibility("$message. 되돌리려면 되돌리기")
        val am = view.context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        delay(if (am?.isTouchExplorationEnabled == true) 10_000 else 5_000)
        onDismiss()
    }
    AnimatedVisibility(message != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .shadow(8.dp, shape, ambientColor = OkcheColor.NamsaekDeep, spotColor = OkcheColor.NamsaekDeep)
                .background(OkcheColor.NamsaekDeep, shape)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown.orEmpty(), Modifier.weight(1f), color = OkcheColor.Hanji, style = OkcheType.Body.toTextStyle(FontWeight.Normal))
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(OkcheRadius.RadiusMd))
                    .clickable(role = Role.Button, onClick = onUndo)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("되돌리기", color = OkcheColor.GeumLight, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
            }
        }
    }
}
