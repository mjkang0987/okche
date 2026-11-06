package app.okche.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.okche.store.NotifySettings
import app.okche.store.OkcheDatabase
import app.okche.store.Setting
import app.okche.ui.med.clock
import app.okche.ui.pet.FormHeader
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 기별(보드 Notify): 더보기 '기별 · 알림 설정'. 돌봄·기쁜 날 스위치, 밤 알림 쉬기(시작~끝), 3일치 안내.
 * 바꾸면 바로 설정 줄에 저장(알림 다시 걸기는 저장소 바뀜 감시가 함). 체형 사진 줄은 11-3.
 */
@Composable
fun NotifyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    // 잇따라 눌러도 누른 순서대로 쓰게(뒤 값이 앞 값에 덮이지 않게).
    val lock = remember { Mutex() }
    var setting by remember { mutableStateOf<Setting?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { setting = dao.loadOrCreateSetting() }

    fun save(change: Setting.() -> Setting) {
        val before = setting ?: return
        val next = before.change().copy(updatedAt = System.currentTimeMillis())
        setting = next
        scope.launch {
            failed = runCatching { lock.withLock { dao.update(next) } }.isFailure
            if (failed) {
                setting = before // 저장 안 된 값을 보여 주지 않게 되돌림
                @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
            }
        }
    }
    fun notify(change: NotifySettings.() -> NotifySettings) = save { copy(notify = notify.change()) }

    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("기별", "알림 설정", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack)
        val s = setting ?: return@Column
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 4.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            NotifySection("돌봄") {
                OkcheSwitchRow("탕약 시간", "약마다 정한 시간에", s.notify.medTime, { v -> notify { copy(medTime = v) } }, framed = false)
                HorizontalDivider(color = OkcheColor.Line)
                OkcheSwitchRow("인슐린 시간", "당뇨 아이 · 인슐린 설정에서 정한 시간에", s.notify.insulinTime, { v -> notify { copy(insulinTime = v) } }, framed = false)
                HorizontalDivider(color = OkcheColor.Line)
                OkcheSwitchRow("약방", "${s.lowMedDays}일분 남았을 때", s.notify.lowMed, { v -> notify { copy(lowMed = v) } }, note = "남은 약", framed = false)
                HorizontalDivider(color = OkcheColor.Line)
                OkcheSwitchRow("옥체", weighInText(s.weightReminder.weekday, s.weightReminder.time), s.notify.weighIn, { v -> notify { copy(weighIn = v) } }, note = "체중 재기", framed = false)
            }
            NotifySection("기쁜 날") {
                OkcheSwitchRow("생일 · 가족 된 날 축하", "", s.notify.goodDay, { v -> notify { copy(goodDay = v) } }, framed = false)
                HorizontalDivider(color = OkcheColor.Line)
                OkcheSwitchRow("작년 오늘의 사진", "", s.notify.lastYearToday, { v -> notify { copy(lastYearToday = v) } }, framed = false)
            }
            NotifySection("조용한 시간") {
                OkcheSwitchRow(
                    "밤에는 알림 쉬기", "탕약·인슐린 알림은 이 시간에도 울려요", s.quietHours.enabled,
                    { v -> save { copy(quietHours = quietHours.copy(enabled = v)) } }, framed = false,
                )
                // 꺼져 있으면 시간 칸은 숨김(약 추가의 '알림 보낼 때'와 같은 방식).
                if (s.quietHours.enabled) {
                    Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val field = @Composable { label: String, time: String, m: Modifier, onPick: (String) -> Unit ->
                            val p = time.split(":").mapNotNull { it.toIntOrNull() }
                            OkchePickerField(
                                clock(time), label,
                                { TimePickerDialog(context, { _, h, min -> onPick("%02d:%02d".format(h, min)) }, p.getOrElse(0) { 22 }, p.getOrElse(1) { 0 }, false).show() },
                                m, showLabel = false,
                            )
                        }
                        field("시작", s.quietHours.start, Modifier.weight(1f)) { v -> save { copy(quietHours = quietHours.copy(start = v)) } }
                        Text("~", Modifier.clearAndSetSemantics {}, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
                        field("끝", s.quietHours.end, Modifier.weight(1f)) { v -> save { copy(quietHours = quietHours.copy(end = v)) } }
                    }
                }
            }
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            Text(
                "알림은 앱을 마지막으로 연 날부터 3일치만 걸어 둬요. 3일 동안 열지 않으면 멈추고, 다시 열면 이어져요.",
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
        }
    }
}

/** 기별 묶음: 흰 카드(모서리 20) + 머리말 14 흐린 글자. */
@Composable
private fun NotifySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(horizontal = 14.dp, vertical = 4.dp)
            .semantics { isTraversalGroup = true },
    ) {
        Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle())
        content()
    }
}

/** '매주 일요일 오전 10시'(분이 있으면 '오전 10시 30분'). [weekday]는 ISO(월=1 … 일=7), [time]은 `HH:mm`. */
fun weighInText(weekday: Int, time: String): String {
    val day = DayOfWeek.of(weekday.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val p = time.split(":").mapNotNull { it.toIntOrNull() }
    if (p.size != 2) return "매주 $day"
    val hour = "${if (p[0] < 12) "오전" else "오후"} ${if (p[0] % 12 == 0) 12 else p[0] % 12}시"
    return "매주 $day $hour" + if (p[1] > 0) " ${p[1]}분" else ""
}
