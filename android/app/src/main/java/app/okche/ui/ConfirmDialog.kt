package app.okche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheType

/**
 * 확인 창(빼기·지우기·간직 등, 디자인 MemoryKeep·PetDelete·MemoryRestore): 어두운 바탕 위 가운데 카드.
 * 제목(되돌릴 수 없는 일은 [destructive] — 연지색), 본문([text] — 검은 글씨), 안내 글·점 목록, 덧붙일 칸([extra]), [cancel]과 확인 버튼.
 * 허락 안내 창(NotifyAsk, 8-4b)도 이 창 — 본문 + 회색 안내 한 줄, '나중에'·'계속하기'.
 */
@Composable
fun OkcheConfirmDialog(
    title: String,
    confirm: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
    message: String? = null,
    text: String? = null,
    cancel: String = "그냥 둘게요",
    bullets: List<String> = emptyList(),
    confirmLook: ButtonLook = ButtonLook.Filled,
    /** 거짓이면 '그냥 둘게요' 없이 버튼 하나(안내 창 — '지금은 뺄 수 없어요', 8-1). */
    showsCancel: Boolean = true,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(OkcheColor.Hanji, RoundedCornerShape(OkcheRadius.RadiusLg))
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title, Modifier.semantics { heading() },
                color = if (destructive) OkcheColor.Yeonji else OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
            )
            text?.let { Text(it, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle()) }
            message?.let { Text(it, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal)) }
            if (bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (line in bullets) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 9.dp).size(6.dp).background(OkcheColor.LineStrong, CircleShape))
                            Text(line, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
                        }
                    }
                }
            }
            extra()
            // 큰 글자에서는 두 버튼을 세로로 쌓는다(아이 바꾸기 시트와 같은 기준).
            if (LocalDensity.current.fontScale >= 1.15f) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showsCancel) OkchePrimaryButton(cancel, onCancel, look = ButtonLook.Outlined)
                    OkchePrimaryButton(confirm, onConfirm, look = confirmLook)
                }
            } else {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showsCancel) OkchePrimaryButton(cancel, onCancel, Modifier.weight(1f), look = ButtonLook.Outlined)
                    OkchePrimaryButton(confirm, onConfirm, Modifier.weight(1f), look = confirmLook)
                }
            }
        }
    }
}
