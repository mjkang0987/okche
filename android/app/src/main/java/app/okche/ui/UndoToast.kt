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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheType
import kotlinx.coroutines.delay

/** 알림 하나: 같은 글자가 다시 와도 새 알림으로(시간 다시). */
data class UndoToastItem(val text: String, val id: String = app.okche.store.newId())

/**
 * 탭 바 위 되돌리기 알림(보드 `RecordMedGiven`, 2026-11-04 승인): 남색 띠에 글자 + 금색 '되돌리기'. 9단계 인슐린 건너뜀 알림도 이걸 쓴다. [item]이 null이면 숨김.
 * 화면 읽기 알림은 부르는 쪽이 만들 때 한 번(탭을 다녀와도 다시 읽지 않게). 시스템 권장 시간(기본 5초) 뒤 [onDismiss], 터치 탐색 중이면 닫지 않는다(WCAG 2.2.1).
 * 닫는 건 부르는 쪽 — 되돌리기는 성공했을 때 닫는다. 알림 아래 화면은 눌리지 않는다.
 */
@Composable
fun UndoToast(item: UndoToastItem?, onDismiss: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    // 사라지는 동안에도 글자가 남게 마지막 글자를 쥔다.
    var shown by remember { mutableStateOf(item?.text) }
    if (item != null) shown = item.text
    LaunchedEffect(item?.id) {
        if (item == null) return@LaunchedEffect
        val am = view.context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        if (am?.isTouchExplorationEnabled == true) return@LaunchedEffect
        val flags = android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT or android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS
        // 권장 시간은 API 29부터(minSdk 26).
        delay(if (android.os.Build.VERSION.SDK_INT >= 29) (am?.getRecommendedTimeoutMillis(5_000, flags) ?: 5_000).toLong() else 5_000L)
        onDismiss()
    }
    AnimatedVisibility(item != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .shadow(8.dp, shape, ambientColor = OkcheColor.NamsaekDeep, spotColor = OkcheColor.NamsaekDeep)
                .background(OkcheColor.NamsaekDeep, shape)
                .pointerInput(Unit) { detectTapGestures { } } // 글자·여백을 눌러도 아래 화면이 눌리지 않게
                // 터치 탐색 중엔 저절로 안 닫히니 화면 읽기 동작으로 닫기.
                .semantics { customActions = listOf(CustomAccessibilityAction("알림 닫기") { onDismiss(); true }) }
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown.orEmpty(), Modifier.weight(1f), color = OkcheColor.Hanji, style = OkcheType.Body.toTextStyle(FontWeight.Normal))
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(OkcheRadius.RadiusMd))
                    .clickable(role = Role.Button) { if (item != null) onUndo() } // 사라지는 동안은 눌리지 않게
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("되돌리기", color = OkcheColor.GeumLight, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
            }
        }
    }
}
